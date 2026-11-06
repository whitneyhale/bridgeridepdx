"""Export the 28 bridge end points from data/bridges.json for the Android app.

Writes android/app/src/main/assets/bridge_ends.csv: id,name,end,lat,lon,bike_access
CI runs this and fails if the committed CSV is out of date.
"""
import csv
import json
import pathlib

ROOT = pathlib.Path(__file__).resolve().parent.parent
SRC = ROOT / "data" / "bridges.json"
OUT = ROOT / "android" / "app" / "src" / "main" / "assets" / "bridge_ends.csv"


def main() -> None:
    bridges = json.loads(SRC.read_text(encoding="utf-8"))["bridges"]
    OUT.parent.mkdir(parents=True, exist_ok=True)
    with OUT.open("w", newline="", encoding="utf-8") as f:
        w = csv.writer(f, lineterminator="\n")
        w.writerow(["id", "name", "end", "lat", "lon", "bike_access"])
        for b in bridges:
            for end in sorted(b["ends"]):
                lat, lon = b["ends"][end]
                w.writerow([b["id"], b["name"], end, f"{lat:.6f}", f"{lon:.6f}", b["bike_access"]])
    print(f"wrote {OUT.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
