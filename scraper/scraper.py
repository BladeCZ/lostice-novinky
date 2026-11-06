#!/usr/bin/env python3
"""Scraper webu mesta Lostice: aktuality, hlaseni rozhlasu, usneseni RM a ZM, Hlas Lostic.

Stahne PDF dokumenty, vytahne z nich strukturovany text a rozparsuje usneseni
na jednotliva cisla se shrnutim. Hlas Lostic navic rozdeli na jednotlive stranky A5 na vysku.
Aktuality a hlaseni navic obohacuje o datum a cas vlozeni a posledni aktualizace.
"""
import copy
import io
import json
import re
import sys
import time
from datetime import datetime, timezone
from pathlib import Path
from urllib.parse import urljoin, urlparse, parse_qs

import pdfplumber
import pypdf
import requests
from bs4 import BeautifulSoup

BASE = "https://www.mu-lostice.cz"
HEADERS = {"User-Agent": "Mozilla/5.0 (compatible; LosticeNovinky/1.0; +https://github.com/BladeCZ/lostice-novinky)"}
DATA_DIR = Path(__file__).resolve().parent.parent / "data"
WEB_DIR = Path(__file__).resolve().parent.parent / "web"
PDF_A5_DIR = WEB_DIR / "pdf_a5"

SOURCES = {
    "aktuality": {"url": BASE + "/obcan/aktualne/aktuality/", "limit": 40},
    "hlaseni": {
        "url": BASE + "/obcan/aktualne/aktualni-hlaseni-mestskeho-rozhlasu/",
        "limit": 150,
    },
    "rm": {"url": BASE + "/mesto/samosprava/usneseni-rm/", "limit": 100},
    "zm": {"url": BASE + "/mesto/samosprava/usneseni-zm/", "limit": 60},
    "hlas": {"url": BASE + "/obcan/hlas-lostic/", "limit": 20},
}

session = requests.Session()
session.headers.update(HEADERS)


def get(url, **kw):
    for attempt in range(3):
        try:
            r = session.get(url, timeout=60, **kw)
            r.raise_for_status()
            return r
        except requests.RequestException as e:
            print(f"  chyba {url}: {e} (pokus {attempt + 1})", file=sys.stderr)
            time.sleep(2)
    return None


def soup_of(url):
    r = get(url)
    if r is None:
        return None
    r.encoding = "utf-8"
    return BeautifulSoup(r.text, "html.parser")


def clean(s):
    return re.sub(r"\s+", " ", s or "").strip()


def format_clean_text(lines_with_pos):
    """Sestavi z radku s vertikalni polohou citelny text s odstavci a odrazkami."""
    out_paragraphs = []
    curr_lines = []

    def flush():
        nonlocal curr_lines
        if not curr_lines:
            return
        p_text = "\n".join(curr_lines).strip()
        if p_text:
            out_paragraphs.append(p_text)
        curr_lines = []

    prev = None
    for item in lines_with_pos:
        txt = item["text"]
        gap = item["gap"]
        h = item["h"]

        is_big_gap = gap >= 6.5 or (h > 0 and gap >= h * 0.55)

        is_heading_or_item = bool(
            re.match(
                r"^(Usnesení\s+\d+|[0-9]{1,2}\.\s+|"
                r"Vážení\s+občané|ČEZ\s+|Klub\s+|Místní\s+organizace|Upozornění|"
                r"Zápis\s+a\s+usnesení|Přítomni:|Program:|Starostka\s+předložila|"
                r"Ověřovatelé\s+zápisu)",
                txt,
                re.IGNORECASE,
            )
        )

        is_vote_or_decision = bool(
            re.match(
                r"^(PRO[-:\s]|Poměr hlasování|RM tento program|Po projednání)",
                txt,
                re.IGNORECASE,
            )
        )

        is_bullet = bool(
            re.match(r"^(?:[-–•●▪*]|\bod\s+\d{1,2}[\.:]\d{2})", txt, re.IGNORECASE)
        )

        if prev is not None and (is_big_gap or (is_heading_or_item and gap >= 2.5)):
            flush()

        if prev is not None and not (is_big_gap or (is_heading_or_item and gap >= 2.5)):
            prev_txt = curr_lines[-1] if curr_lines else ""
            if prev_txt.endswith(":") or is_bullet or is_vote_or_decision or txt.startswith("Usnesení "):
                curr_lines.append(txt)
            else:
                if prev_txt.endswith("-"):
                    curr_lines[-1] = prev_txt[:-1] + txt
                else:
                    curr_lines[-1] = prev_txt + " " + txt
        else:
            curr_lines.append(txt)

        prev = item

    flush()
    return "\n\n".join(out_paragraphs)


def pdf_to_text(content):
    """Vytahne text z PDF (bez OCR) a zachova mezery i odstavce podle vertikalni pozice radku."""
    all_lines = []
    with pdfplumber.open(io.BytesIO(content)) as pdf:
        for pno, pg in enumerate(pdf.pages):
            lines = pg.extract_text_lines(x_tolerance=1.5, y_tolerance=3)
            if not lines:
                continue
            prev_l = None
            for l in lines:
                txt = re.sub(r"[ \t]+", " ", l["text"]).strip()
                if not txt:
                    continue
                if re.match(r"^\d{1,3}$", txt) and (l["top"] > pg.height - 60 or l["top"] < 60):
                    continue
                gap = (l["top"] - prev_l["bottom"]) if prev_l else (15.0 if pno > 0 else 0.0)
                h = l["bottom"] - l["top"]
                all_lines.append(
                    {
                        "text": txt,
                        "top": l["top"],
                        "bottom": l["bottom"],
                        "gap": gap,
                        "h": h,
                        "page": pno,
                    }
                )
                prev_l = l

    return format_clean_text(all_lines)


def split_pdf_to_a5(content):
    """Rozdeli PDF kde jsou dvojstranky A4 na sirku na jednotlive stranky A5 na vysku."""
    src = pypdf.PdfReader(io.BytesIO(content))
    writer = pypdf.PdfWriter()

    for idx, page in enumerate(src.pages):
        box = page.mediabox
        w = float(box.width)
        h = float(box.height)

        if w > h * 1.15:
            mid = w / 2.0
            p_left = copy.copy(page)
            p_left.mediabox = copy.copy(box)
            p_left.mediabox.lower_left = (box.left, box.bottom)
            p_left.mediabox.upper_right = (mid, box.top)

            p_right = copy.copy(page)
            p_right.mediabox = copy.copy(box)
            p_right.mediabox.lower_left = (mid, box.bottom)
            p_right.mediabox.upper_right = (box.right, box.top)

            writer.add_page(p_left)
            writer.add_page(p_right)
        else:
            writer.add_page(page)

    buf = io.BytesIO()
    writer.write(buf)
    return buf.getvalue(), len(writer.pages)


def download_text(pdf_url):
    r = get(pdf_url)
    if r is None:
        return ""
    try:
        t = pdf_to_text(r.content)
        if t and len(t.strip()) > 30:
            return t
    except Exception as e:
        print(f"  chyba cteni PDF {pdf_url}: {e}", file=sys.stderr)
    return ""


def parse_date(txt):
    m = re.search(r"(\d{1,2})\.\s*(\d{1,2})\.\s*(\d{4})", txt or "")
    if m:
        d, mth, y = m.groups()
        return f"{y}-{int(mth):02d}-{int(d):02d}"
    return None


def parse_resolutions(text):
    """Rozparsuje text usneseni na cisla, rozhodnuti a shrnuti."""
    m_first = re.search(r"(?:^|\n)(?:Usnesení\s+č\.\s*|Usnesení\s+)(\d+/\d+)", text)
    if not m_first:
        return "", []

    header = text[: m_first.start()].strip()
    body = text[m_first.start() :]

    pattern = r"(?:^|\n)(?:Usnesení\s+č\.\s*|Usnesení\s+)(\d+/\d+)(.*?)(?=(?:\n(?:Usnesení\s+č\.\s*|Usnesení\s+)\d+/\d+)|\Z)"
    matches = re.findall(pattern, body, re.DOTALL)

    resolutions = []
    for num, block in matches:
        b = block.strip()

        m_dec = re.search(r"\b(SCHVALUJE|NESCHVALUJE|BERE NA VĚDOMÍ|SOUHLASÍ|VOLÍ|JMENUJE|UKLÁDÁ|POVĚŘUJE)\b", b)
        decision = m_dec.group(1) if m_dec else "OSTATNÍ"

        is_proc = bool(
            re.search(
                r"(ověřovatel|program jednání|zapisovatel|návrhová komise|volební komise|zasedání.*řídí|schválení programu)",
                b,
                re.IGNORECASE,
            )
        )

        clean_block = b
        if m_dec:
            clean_block = b[m_dec.end() :].strip()
            clean_block = re.sub(r"^[:\-\s]+", "", clean_block).strip()

        first_sentence = clean_block
        dot_idx = -1
        m_dot = re.search(r"(?<!\bp)(?<!\bč)(?<!\br)(?<!\bul)(?<!\bč\.j)(?<!\bč\.p)(?<!\s\d)\.\s+[A-ZÁČĎÉĚÍŇÓŘŠŤÚŮÝŽ]", clean_block)
        if m_dot:
            dot_idx = m_dot.start() + 1
        elif "." in clean_block:
            dot_idx = clean_block.find(".") + 1

        if dot_idx > 0:
            first_sentence = clean_block[:dot_idx].strip()

        first_sentence = re.sub(r"\s+", " ", first_sentence)
        if len(first_sentence) > 260:
            first_sentence = first_sentence[:257].rsplit(" ", 1)[0] + "…"

        summary = f"{decision.capitalize()} {first_sentence}" if decision != "OSTATNÍ" else first_sentence
        summary = re.sub(r"\s+", " ", summary).strip()

        resolutions.append({
            "num": num,
            "decision": decision,
            "is_procedural": is_proc,
            "summary": summary,
            "text": b
        })

    return header, resolutions


def load_existing(kind):
    p = DATA_DIR / f"{kind}.json"
    if p.exists():
        try:
            return {i["id"]: i for i in json.loads(p.read_text("utf-8")).get("items", [])}
        except Exception:
            pass
    return {}


def pages(base_url, max_pages=8):
    for p in range(1, max_pages + 1):
        url = base_url if p == 1 else f"{base_url.rstrip('/')}/?page={p}"
        s = soup_of(url)
        if s is None:
            break
        yield s
        if not s.find("a", href=re.compile(rf"page={p + 1}")):
            break


def scrape_aktuality(cfg, existing):
    """Scrapuje aktuality vcetne textu, obrazku a priloh."""
    items = []
    seen = set()
    for s in pages(cfg["url"]):
        links = []
        for a in s.find_all("a", href=True):
            h = a["href"].replace("&amp;", "&")
            if "/aktuality/" in h and h.endswith(".html") and h not in seen:
                seen.add(h)
                links.append(h)

        for h in links:
            if len(items) >= cfg["limit"]:
                break
            art_url = urljoin(BASE, h)
            m = re.search(r"-([0-9]+)cs\.html$", h)
            id_ = m.group(1) if m else h.split("/")[-1].replace(".html", "")

            old = existing.get(id_)
            if old and old.get("text") and old.get("date_insert"):
                items.append(old)
                continue

            print("  aktualita", id_, h)
            d = soup_of(art_url)
            if d is None:
                if old:
                    items.append(old)
                continue

            h1 = d.find("h1")
            title = clean(h1.get_text()) if h1 else clean(h.split("/")[-1])

            ins = d.find(class_="date-insert-value")
            upd = d.find(class_="date-update-value")
            d_ins = clean(ins.get_text()) if ins else ""
            d_upd = clean(upd.get_text()) if upd else ""

            date_iso = ""
            dm = re.search(r"(\d{1,2})\.\s*(\d{1,2})\.\s*(\d{4})", d_ins or d_upd)
            if dm:
                date_iso = f"{dm.group(3)}-{int(dm.group(2)):02d}-{int(dm.group(1)):02d}"

            c = d.find("div", class_="event-detail-content") or d.find("div", class_="event-detail")
            lead = ""
            body_text = ""
            if c:
                lead_tag = c.find("div", class_="lead")
                if lead_tag:
                    lead = clean(lead_tag.get_text())
                txt_tag = c.find("div", class_="event-text")
                if txt_tag:
                    paragraphs = []
                    for p_el in txt_tag.find_all(["p", "div", "li"]):
                        ptxt = clean(p_el.get_text())
                        if ptxt and ptxt not in paragraphs and len(ptxt) > 1:
                            paragraphs.append(ptxt)
                    body_text = "\n\n".join(paragraphs) if paragraphs else clean(txt_tag.get_text())
            else:
                body_tag = d.select_one("#content, .content, article, main")
                body_text = clean(body_tag.get_text())[:3000] if body_tag else ""

            full_text_parts = []
            if lead:
                full_text_parts.append(lead)
            if body_text:
                full_text_parts.append(body_text)

            footer = []
            if d_ins:
                footer.append(f"Datum vložení: {d_ins}")
            if d_upd:
                footer.append(f"Datum poslední aktualizace: {d_upd}")
            if footer:
                full_text_parts.append("\n".join(footer))

            full_text = "\n\n".join(full_text_parts)

            # Obrázky (galerie a inline)
            images = []
            seen_imgs = set()
            if c:
                for tag in c.find_all(["img", "a"]):
                    src = tag.get("href") or tag.get("src")
                    if not src:
                        continue
                    if "evt_image.php" in src or src.lower().endswith((".jpg", ".jpeg", ".png", ".webp")):
                        full_img = urljoin(BASE, src)
                        base_img = re.sub(r"&(width|height|box)=\d+", "", full_img)
                        if base_img not in seen_imgs and "logo" not in base_img.lower() and "crest" not in base_img.lower():
                            seen_imgs.add(base_img)
                            images.append(base_img)

            # Přílohy a soubory
            files = []
            if c:
                files_div = c.find("div", class_="event-files")
                if files_div:
                    for fa in files_div.find_all("a", href=True):
                        fname = clean(fa.get_text()) or "Příloha"
                        furl = urljoin(BASE, fa["href"].replace("&amp;", "&"))
                        files.append({"name": fname, "url": furl})

            items.append({
                "id": id_,
                "title": title,
                "date": date_iso,
                "date_insert": d_ins,
                "date_update": d_upd,
                "url": art_url,
                "page": art_url,
                "text": full_text,
                "images": images,
                "files": files,
            })

        if len(items) >= cfg["limit"]:
            break

    return items


def scrape_hlaseni(cfg, existing):
    entries = []
    seen = set()
    for s in pages(cfg["url"]):
        n_before = len(seen)
        entries += _hlaseni_page(s, seen)
        if len(seen) == n_before or len(entries) >= cfg["limit"]:
            break
    return _hlaseni_items(cfg, existing, entries)


def _hlaseni_page(s, seen):
    entries = []
    for a in s.find_all("a", href=True):
        href = a["href"].replace("&amp;", "&")
        m = re.search(r"-([0-9]+)cs\.html$", href)
        if not m or m.group(1) in seen or "/aktualni-hlaseni-mestskeho-rozhlasu/" not in href:
            continue
        seen.add(m.group(1))
        txt = a.get_text(" ")
        title = clean(txt.split("Datum:")[0])
        entries.append((m.group(1), urljoin(BASE, href), title, parse_date(txt.split("Datum:")[-1])))
    return entries


def _hlaseni_items(cfg, existing, entries):
    items = []
    for id_, page, title, date in entries[: cfg["limit"]]:
        old = existing.get(id_)
        # Pokud starý záznam existuje a již obsahuje datum vložení
        if old and old.get("text") and "Datum vložení:" in old.get("text", "") and old.get("date_insert"):
            items.append(old)
            continue

        print("  hlaseni", id_, title)
        d = soup_of(page)
        if d is None:
            if old:
                items.append(old)
            continue

        ins = d.find(class_="date-insert-value")
        upd = d.find(class_="date-update-value")
        d_ins = clean(ins.get_text()) if ins else ""
        d_upd = clean(upd.get_text()) if upd else ""

        footer = []
        if d_ins:
            footer.append(f"Datum vložení: {d_ins}")
        if d_upd:
            footer.append(f"Datum poslední aktualizace: {d_upd}")
        footer_str = "\n".join(footer)

        pdf = None
        for a in d.find_all("a", href=True):
            if "evt_file.php" in a["href"] or a["href"].lower().endswith(".pdf"):
                pdf = urljoin(BASE, a["href"].replace("&amp;", "&"))
                break

        text = download_text(pdf) if pdf else None
        if not text:
            if old and old.get("text"):
                text = old["text"].split("\n\nDatum vložení:")[0]
            else:
                body = d.select_one("#content, .content, article, main") or d
                text = clean(body.get_text(" "))[:3000] if not pdf else ""

        if not date:
            date = parse_date(title) or parse_date(d.get_text(" "))

        if footer_str and "Datum vložení:" not in (text or ""):
            text = ((text or "").rstrip() + "\n\n" + footer_str).strip()

        items.append({
            "id": id_,
            "title": title,
            "date": date,
            "date_insert": d_ins,
            "date_update": d_upd,
            "url": pdf or page,
            "page": page,
            "text": text or "",
        })
    return items


def scrape_usneseni(cfg, existing):
    items, seen = [], set()
    for s in pages(cfg["url"]):
        n_before = len(seen)
        _usneseni_page(s, cfg, existing, items, seen)
        if len(seen) == n_before or len(items) >= cfg["limit"]:
            break
    return items


def _usneseni_page(s, cfg, existing, items, seen):
    for a in s.find_all("a", href=True):
        if "download.php" not in a["href"]:
            continue
        href = a["href"].replace("&amp;", "&")
        q = parse_qs(urlparse(href).query)
        id_ = q.get("file", [href])[0].replace("|", "-")
        if id_ in seen:
            continue
        seen.add(id_)
        title = clean(a.get_text(" "))
        url = urljoin(BASE, href)
        old = existing.get(id_)
        if old and old.get("text") and old.get("resolutions"):
            items.append(old)
            continue
        print("  usneseni", id_, title)
        text = download_text(url) or ""
        header, resolutions = parse_resolutions(text)
        items.append({
            "id": id_,
            "title": title,
            "date": parse_date(title),
            "url": url,
            "page": cfg["url"],
            "text": text,
            "header": header,
            "resolutions": resolutions,
        })
        if len(items) >= cfg["limit"]:
            break


def extract_toc_from_a5(a5_path):
    """Extrahuje nadpisy a cisla stran z vygenerovaneho A5 PDF."""
    toc = []
    try:
        with pdfplumber.open(a5_path) as pdf:
            for idx, page in enumerate(pdf.pages):
                words = page.extract_words(extra_attrs=['size', 'fontname'])
                cw = [w for w in words if w['top'] > 35 and w['bottom'] < page.height - 30]
                if not cw:
                    continue
                max_s = max(w['size'] for w in cw)
                if max_s >= 10.5:
                    h_words = [w['text'] for w in cw if w['size'] >= max_s * 0.9]
                    title = re.sub(r'\s+', ' ', " ".join(h_words)).strip()
                    if len(title) >= 5 and len(title) <= 120 and not re.match(
                        r'^(hlas|loštic|podzim|jaro|léto|leto|zima|\d+[\s\.\,\-]*)$', title, re.I
                    ):
                        if not toc or toc[-1]['title'] != title:
                            toc.append({'page': idx + 1, 'title': title})
    except Exception as e:
        print("  chyba pri extrakci TOC:", e, file=sys.stderr)
    return toc


def scrape_hlas_lostic(cfg, existing):
    PDF_A5_DIR.mkdir(parents=True, exist_ok=True)
    s = soup_of(cfg["url"])
    if s is None:
        return None
    items = []
    links = [a for a in s.find_all("a", href=True) if "download.php" in a["href"]]
    for a in links[: cfg["limit"]]:
        title = clean(a.get_text(" "))
        href = a["href"].replace("&amp;", "&")
        q = parse_qs(urlparse(href).query)
        id_ = q.get("file", [href])[0].replace("|", "-")
        orig_url = urljoin(BASE, href)

        m = re.search(r"(jaro|léto|leto|podzim|zima)\s+(\d{4})", title, re.IGNORECASE)
        date_str = ""
        if m:
            season = m.group(1).lower()
            year = m.group(2)
            month_map = {"jaro": "03", "léto": "06", "leto": "06", "podzim": "09", "zima": "12"}
            date_str = f"{year}-{month_map.get(season, '01')}-01"

        old = existing.get(id_)
        a5_filename = f"hlas-{id_}.pdf"
        a5_path = PDF_A5_DIR / a5_filename

        pages_count = 0
        toc = old.get("toc", []) if old else []
        if a5_path.exists():
            pages_count = old.get("pages_count", 32) if old else 32
            if not toc:
                toc = extract_toc_from_a5(a5_path)
        else:
            print("  hlas lostic (split A5)", id_, title)
            r = get(orig_url)
            if r is not None:
                a5_bytes, pages_count = split_pdf_to_a5(r.content)
                a5_path.write_bytes(a5_bytes)
                toc = extract_toc_from_a5(a5_path)

        items.append({
            "id": id_,
            "title": title,
            "date": date_str,
            "url": orig_url,
            "a5_url": f"pdf_a5/{a5_filename}",
            "pages_count": pages_count,
            "toc": toc,
            "text": f"{title}\n\nPřehled článků a témat naleznete v obsahu níže. Časopis je převeden z původních dvojstran do formátu A5 na výšku pro pohodlné čtení v telefonu (celkem {pages_count} stránek na výšku).",
        })
    return items


def main():
    DATA_DIR.mkdir(exist_ok=True)
    WEB_DIR.mkdir(exist_ok=True)
    now = datetime.now(timezone.utc).isoformat(timespec="seconds")
    for kind, cfg in SOURCES.items():
        print("==", kind)
        existing = load_existing(kind)
        if kind == "aktuality":
            items = scrape_aktuality(cfg, existing)
        elif kind == "hlaseni":
            items = scrape_hlaseni(cfg, existing)
        elif kind == "hlas":
            items = scrape_hlas_lostic(cfg, existing)
        else:
            items = scrape_usneseni(cfg, existing)

        if not items:
            print("  zadne polozky - ponechavam stavajici data", file=sys.stderr)
            continue
        ids = {i["id"] for i in items}
        items += [o for o in existing.values() if o["id"] not in ids]
        items.sort(key=lambda i: (i.get("date") or "", i["id"]), reverse=True)
        items = items[: cfg["limit"]]
        out = {"updated": now, "source": cfg["url"], "items": items}
        (DATA_DIR / f"{kind}.json").write_text(json.dumps(out, ensure_ascii=False, indent=1), "utf-8")
        (WEB_DIR / "data" / f"{kind}.json").write_text(json.dumps(out, ensure_ascii=False, indent=1), "utf-8")
        print(f"  ulozeno {len(items)} polozek")


if __name__ == "__main__":
    main()
