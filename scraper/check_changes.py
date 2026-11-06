#!/usr/bin/env python3
"""Rychly detektor zmen webu mesta Lostice.

Pouziva pouze standardni knihovnu Pythonu (zadne pip zavislosti).
Zkontroluje prvni stranky vsech kategorii proti existujicim data/*.json.
Umoznuje spoustet GitHub Actions kazdou hodinu a ukoncit beh za 2-5 sekund,
pokud na webu zadny novy prispevek nepribyl.
"""
import json
import re
import sys
import urllib.request
from pathlib import Path

DATA_DIR = Path(__file__).resolve().parent.parent / "data"

URLS = {
    "aktuality": "https://www.mu-lostice.cz/obcan/aktualne/aktuality/",
    "hlaseni": "https://www.mu-lostice.cz/obcan/aktualne/aktualni-hlaseni-mestskeho-rozhlasu/",
    "rm": "https://www.mu-lostice.cz/mesto/samosprava/usneseni-rm/",
    "zm": "https://www.mu-lostice.cz/mesto/samosprava/usneseni-zm/",
    "hlas": "https://www.mu-lostice.cz/obcan/hlas-lostic/"
}

HEADERS = {"User-Agent": "Mozilla/5.0 (compatible; LosticeChangeCheck/1.0)"}


def check_for_changes():
    for kind, url in URLS.items():
        json_path = DATA_DIR / f"{kind}.json"
        if not json_path.exists():
            print(f"[{kind}] Soubor {json_path.name} neexistuje -> nutno spustit scraper.")
            return True

        try:
            stored_data = json.loads(json_path.read_text(encoding="utf-8"))
            stored_items = stored_data.get("items", [])
            stored_ids = {str(it.get("id")) for it in stored_items}
        except Exception as e:
            print(f"[{kind}] Chyba pri cteni {json_path.name}: {e} -> nutno spustit scraper.")
            return True

        try:
            req = urllib.request.Request(url, headers=HEADERS)
            html = urllib.request.urlopen(req, timeout=20).read().decode("utf-8", errors="ignore")
        except Exception as e:
            print(f"[{kind}] Nelze nacist {url}: {e}")
            continue

        if kind in ["aktuality", "hlaseni"]:
            pattern = rf'/obcan/aktualne/{kind}/[^"\s]+\.html'
            if kind == "hlaseni":
                pattern = r'/obcan/aktualne/aktualni-hlaseni-mestskeho-rozhlasu/[^"\s]+\.html'
            matches = re.findall(pattern, html)
            for m in matches[:5]:
                id_match = re.search(r'-([0-9]+)cs\.html$', m)
                id_ = id_match.group(1) if id_match else m.split("/")[-1].replace(".html", "")
                if id_ not in stored_ids:
                    print(f"[{kind}] Nalezen novy zaznam: {id_} ({m})")
                    return True
        else:
            files = re.findall(r'download\.php\?file=([^"&]+)', html)
            for f in files[:5]:
                id_ = f.replace("%7C", "-").replace("|", "-")
                if id_ not in stored_ids:
                    print(f"[{kind}] Nalezen novy soubor: {id_}")
                    return True

    return False


def main():
    has_changes = check_for_changes()
    output_val = "true" if has_changes else "false"
    print(f"has_changes={output_val}")

    # Podpora pro GitHub Actions $GITHUB_OUTPUT
    if len(sys.argv) > 1 and sys.argv[1]:
        output_file = Path(sys.argv[1])
        try:
            with open(output_file, "a", encoding="utf-8") as f:
                f.write(f"has_changes={output_val}\n")
        except Exception as e:
            print(f"Nelze zapsat do GITHUB_OUTPUT: {e}")


if __name__ == "__main__":
    main()
