#!/usr/bin/env python3
"""Prüft die Längen der Store-Texte in docs/play-store/store-listing.md.

Jeder Text steht dort in einem ```text-Block direkt nach einem Marker wie <!-- de:title -->. Gezählt wird wie bei
Google Play in Zeichen (Unicode-Codepoints): Umlaute, „ß" und Gedankenstriche zählen je eins, ein Zeilenumbruch
ebenfalls. Gibt eine Tabelle aus und endet mit Status 1, wenn ein Text zu lang ist oder fehlt.

Aufruf: python3 docs/play-store/tools/count_listing.py [Pfad/zu/store-listing.md]
"""

import re
import sys
from pathlib import Path

LIMITS = {"title": 30, "short": 80, "full": 4000, "notes": 500}
NAMES = {"title": "App-Name", "short": "Kurzbeschreibung", "full": "Vollständige Beschreibung", "notes": "Versionshinweise"}
LANGUAGES = ("de", "en")

BLOCK = re.compile(r"<!--\s*(\w+):(\w+)\s*-->\s*```text\n(.*?)\n```", re.DOTALL)


def main() -> int:
    default = Path(__file__).resolve().parent.parent / "store-listing.md"
    path = Path(sys.argv[1]) if len(sys.argv) > 1 else default
    texts = {(lang, field): body for lang, field, body in BLOCK.findall(path.read_text(encoding="utf-8"))}

    ok = True
    print(f"{'Feld':<28}{'Grenze':>8}" + "".join(f"{lang:>8}" for lang in LANGUAGES))
    for field, limit in LIMITS.items():
        cells = []
        for lang in LANGUAGES:
            text = texts.get((lang, field))
            if text is None:
                cells.append("fehlt")
                ok = False
                continue
            count = len(text)
            if count > limit:
                ok = False
                cells.append(f"{count}!")
            else:
                cells.append(str(count))
            if text != text.strip():
                print(f"Warnung: {lang}:{field} beginnt oder endet mit Leerraum", file=sys.stderr)
        print(f"{NAMES[field]:<28}{limit:>8}" + "".join(f"{cell:>8}" for cell in cells))
    if not ok:
        print("\nFEHLER: Text fehlt oder ist zu lang (mit ! markiert).", file=sys.stderr)
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
