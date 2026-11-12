"""Export what the app's bridge sheet shows, one line per bridge.

Reads data/bridges.json and data/descriptions.json.
Writes android/app/src/main/assets/bridge_info.tsv, tab separated:
id, label, name, year, type, category, access, facility, summary,
then direction, offered (yes/no), side for each of the two crossing directions.
CI runs this and fails if the committed file is out of date.
"""
import json
import pathlib
import re

ROOT = pathlib.Path(__file__).resolve().parent.parent
BRIDGES = ROOT / "data" / "bridges.json"
DESCRIPTIONS = ROOT / "data" / "descriptions.json"
OUT = ROOT / "android" / "app" / "src" / "main" / "assets" / "bridge_info.tsv"

# Short names for small spaces (passport stamps, stop rows). Others drop " Bridge" from the name.
LABELS = {
    "tilikum": "Tilikum",
    "ross-island": "Ross Island",
    "interstate-5": "Interstate",
    "interstate-205": "Glenn Jackson",
    "blumenauer": "Blumenauer",
}


def clean(text) -> str:
    """One line, no tabs, and without the trailing source note in parentheses (sources stay in the data files)."""
    text = re.sub(r"\s+", " ", str(text or "")).strip()
    return re.sub(r"\s*\((?:Wikipedia|OpenStreetMap|KPFF|OAR|Multnomah|BikePortland|TriMet)[^)]*\)", "", text).strip()


def main() -> None:
    bridges = json.loads(BRIDGES.read_text(encoding="utf-8"))["bridges"]
    descriptions = json.loads(DESCRIPTIONS.read_text(encoding="utf-8"))["descriptions"]
    rows = ["\t".join(["id", "label", "name", "year", "type", "category", "access", "facility", "summary",
                       "direction_1", "offered_1", "side_1", "direction_2", "offered_2", "side_2"])]
    for b in bridges:
        label = LABELS.get(b["id"], b["name"].replace(" Bridge", ""))
        summary = descriptions.get(b["id"], {}).get("summary") or b.get("bike_note") or ""
        row = [b["id"], label, b["name"], str(b.get("year") or ""), clean(b.get("type")), clean(b["category_label"]).rstrip("."),
               b["bike_access"], clean(b["bike_facility"]), clean(summary)]
        crossings = sorted((b.get("crossings") or {}).items())
        for direction, c in (crossings + [("", {}), ("", {})])[:2]:
            row += [direction, "yes" if c.get("offered") else ("no" if direction else ""), (c.get("lane") or {}).get("side") or ""]
        rows.append("\t".join(row))
    OUT.write_text("\n".join(rows) + "\n", encoding="utf-8")
    print(f"wrote {OUT.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
