# Loštice novinky

Neoficiální aplikace (Android + web na GitHub Pages) pro občany města Loštice:

- **Hlášení městského rozhlasu**
- **Usnesení rady města**
- **Usnesení zastupitelstva**

Zdrojem je [mu-lostice.cz](https://www.mu-lostice.cz). PDF dokumenty se automaticky stahují a převádí na text (bez OCR).

## Funkce
- Seznam dokumentů, po kliknutí celý text.
- Zvětšení/zmenšení textu (+/−) a předčítání nahlas s nastavením rychlosti (+/−); obojí se ukládá.
- Odkaz na původní PDF.
- Nastavení: upozornění na nové hlášení rozhlasu / usnesení RM / usnesení ZM (každé zvlášť). Aplikace kontroluje novinky cca 1× za hodinu.
- Offline: poslední stažená data jsou uložena v telefonu.

## Struktura
| Složka | Obsah |
|---|---|
| `scraper/` | Python skript: stáhne PDF, vytáhne text → `data/*.json` |
| `data/` | `hlaseni.json`, `rm.json`, `zm.json` (z nich čerpá aplikace i web) |
| `web/` | PWA publikovaná na GitHub Pages |
| `android/` | Android aplikace (Kotlin, Jetpack Compose) |
| `.github/workflows/` | `scraper.yml` (každé 2 h aktualizace + deploy webu), `build_apk.yml` (sestavení APK) |

## Instalace
- **Web:** https://bladecz.github.io/lostice-novinky/ (v nastavení repozitáře: *Settings → Pages → Source: GitHub Actions*).
- **Android:** APK ke stažení v [Releases → apk-latest](https://github.com/BladeCZ/lostice-novinky/releases/tag/apk-latest).

## Lokální spuštění scraperu
```
pip install -r scraper/requirements.txt
python scraper/scraper.py
```
