#!/usr/bin/env python3
"""Scraper webu mesta Lostice: hlaseni mestskeho rozhlasu, usneseni RM a ZM.

Stahne PDF dokumenty, vytahne z nich text a ulozi do data/*.json.
Jiz zpracovane dokumenty se znovu nestahuji (cache podle id).
"""
import io
import json
import re
import sys
import time
from datetime import datetime, timezone
from pathlib import Path
from urllib.parse import urljoin, urlparse, parse_qs

import pdfplumber
import requests
from bs4 import BeautifulSoup

BASE = "https://www.mu-lostice.cz"
HEADERS = {"User-Agent": "Mozilla/5.0 (compatible; LosticeNovinky/1.0; +https://github.com/BladeCZ/lostice-novinky)"}
DATA_DIR = Path(__file__).resolve().parent.parent / "data"

SOURCES = {
    "hlaseni": {
        "url": BASE + "/obcan/aktualne/aktualni-hlaseni-mestskeho-rozhlasu/",
        "limit": 150,
    },
    "rm": {"url": BASE + "/mesto/samosprava/usneseni-rm/", "limit": 100},
    "zm": {"url": BASE + "/mesto/samosprava/usneseni-zm/", "limit": 60},
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


def pdf_to_text(content):
    """Vytahne text z PDF (bez OCR) a zpracuje ho do odstavcu."""
    pages = []
    with pdfplumber.open(io.BytesIO(content)) as pdf:
        for page in pdf.pages:
            t = page.extract_text(x_tolerance=1.5, y_tolerance=3) or ""
            pages.append(t)
    text = "\n".join(pages)
    text = text.replace("\u00ad", "").replace("\xa0", " ")
    lines = [re.sub(r"[ \t]+", " ", l).strip() for l in text.splitlines()]
    # Spojeni radku, ktere jsou jen zalomenim vety uvnitr odstavce.
    out = []
    for l in lines:
        if not l:
            if out and out[-1] != "":
                out.append("")
            continue
        if out and out[-1] != "":
            prev = out[-1]
            # Spoj jen zjevne zalomeni vety: dalsi radek zacina malym pismenem
            # a predchozi nekonci koncem vety.
            if l[0].islower() and not re.search(r"[\.\!\?:;]$", prev):
                out[-1] = prev + " " + l
                continue
        out.append(l)
    return "\n".join(out).strip()


def parse_date(s):
    """Vrati ISO datum z textu typu '3. 11. 2026' nebo '19.10.2026'."""
    m = re.search(r"(\d{1,2})\s*\.\s*(\d{1,2})\s*\.\s*(\d{4})", s or "")
    if not m:
        return None
    d, mo, y = map(int, m.groups())
    try:
        return datetime(y, mo, d).strftime("%Y-%m-%d")
    except ValueError:
        return None


def download_text(url):
    r = get(url)
    if r is None:
        return None
    try:
        return pdf_to_text(r.content)
    except Exception as e:  # poskozene/necitelne PDF
        print(f"  PDF {url} nelze precist: {e}", file=sys.stderr)
        return None


def load_existing(kind):
    p = DATA_DIR / f"{kind}.json"
    if p.exists():
        try:
            return {i["id"]: i for i in json.loads(p.read_text("utf-8")).get("items", [])}
        except Exception:
            pass
    return {}


MAX_PAGES = 15


def pages(url):
    """Generator stranek seznamu (?page=N). Konci, kdyz stranka selze."""
    for n in range(1, MAX_PAGES + 1):
        s = soup_of(url if n == 1 else f"{url}?page={n}")
        if s is None:
            return
        yield s


def scrape_hlaseni(cfg, existing):
    seen, entries = set(), []
    for s in pages(cfg["url"]):
        before = len(entries)
        entries += _hlaseni_entries(s, seen)
        if len(entries) == before or len(entries) >= cfg["limit"]:
            break
    if not entries:
        return None
    return _hlaseni_items(cfg, existing, entries)


def _hlaseni_entries(s, seen):
    entries = []
    for a in s.find_all("a", href=True):
        href = a["href"]
        m = re.search(r"/aktualni-hlaseni-mestskeho-rozhlasu/.+?-(\d+)cs\.html$", href)
        if not m or m.group(1) in seen:
            continue
        seen.add(m.group(1))
        txt = clean(a.get_text(" "))
        title = clean(txt.split("Datum:")[0])
        entries.append((m.group(1), urljoin(BASE, href), title, parse_date(txt.split("Datum:")[-1])))
    return entries


def _hlaseni_items(cfg, existing, entries):
    items = []
    for id_, page, title, date in entries[: cfg["limit"]]:
        old = existing.get(id_)
        if old and old.get("text"):
            items.append(old)
            continue
        print("  hlaseni", id_, title)
        d = soup_of(page)
        if d is None:
            if old:
                items.append(old)
            continue
        pdf = None
        for a in d.find_all("a", href=True):
            if "evt_file.php" in a["href"] or a["href"].lower().endswith(".pdf"):
                pdf = urljoin(BASE, a["href"].replace("&amp;", "&"))
                break
        text = download_text(pdf) if pdf else None
        if not text:  # zaloha: text primo ze stranky
            body = d.select_one("#content, .content, article, main") or d
            text = clean(body.get_text(" "))[:3000] if not pdf else ""
        if not date:
            date = parse_date(title) or parse_date(d.get_text(" "))
        items.append({"id": id_, "title": title, "date": date, "url": pdf or page, "page": page, "text": text or ""})
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
        if old and old.get("text"):
            items.append(old)
            continue
        print("  usneseni", id_, title)
        text = download_text(url) or ""
        items.append({"id": id_, "title": title, "date": parse_date(title), "url": url, "page": cfg["url"], "text": text})
        if len(items) >= cfg["limit"]:
            break


def main():
    DATA_DIR.mkdir(exist_ok=True)
    now = datetime.now(timezone.utc).isoformat(timespec="seconds")
    for kind, cfg in SOURCES.items():
        print("==", kind)
        existing = load_existing(kind)
        items = scrape_hlaseni(cfg, existing) if kind == "hlaseni" else scrape_usneseni(cfg, existing)
        if not items:
            print("  zadne polozky - ponechavam stavajici data", file=sys.stderr)
            continue
        # Zachovat starsi polozky, ktere uz z webu zmizely
        ids = {i["id"] for i in items}
        items += [o for o in existing.values() if o["id"] not in ids]
        items.sort(key=lambda i: (i.get("date") or "", i["id"]), reverse=True)
        items = items[: cfg["limit"]]
        out = {"updated": now, "source": cfg["url"], "items": items}
        (DATA_DIR / f"{kind}.json").write_text(json.dumps(out, ensure_ascii=False, indent=1), "utf-8")
        print(f"  ulozeno {len(items)} polozek")


if __name__ == "__main__":
    main()

