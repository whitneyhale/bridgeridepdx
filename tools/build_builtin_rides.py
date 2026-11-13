"""Build the rides that ship inside the app (release 0.3: follow a line before on-phone routing exists).

Asks the public BRouter service (brouter.de, OpenStreetMap data, "trekking" bike profile) for a route
through the points listed below, and writes android/app/src/main/assets/rides/ID.csv:
"# key: value" header lines, then one "lat,lon" per line.

The points are what force the right bridge in the right direction: a router given only a start and
an end picks the shortest crossing (docs/spike-results.md, test rides 6 and 13). A point in the
middle of the river can only be reached on that bridge.

Both rides start on the Eastbank Esplanade at the Steel Bridge, a public spot. The rider's home
stays out of the repo (docs/test-rides.md).

Needs the network. Run by hand; the app's unit tests then check each ride crosses the bridges
listed in its header, in order (android/core BuiltInRidesTest).
"""
import json
import pathlib
import urllib.parse
import urllib.request

ROOT = pathlib.Path(__file__).resolve().parent.parent
OUT = ROOT / "android" / "app" / "src" / "main" / "assets" / "rides"
BRIDGES = {b["id"]: b for b in json.loads((ROOT / "data" / "bridges.json").read_text(encoding="utf-8"))["bridges"]}

# On the Eastbank Esplanade's main path, about 100 m south of the Steel Bridge and clear of its ramps.
# (A start on a ramp makes the router begin with a U-turn.)
START = (45.526841, -122.665618)


def on_bridge(bridge_id: str, from_end: str, fraction: float) -> tuple[float, float]:
    """A point on a bridge's straight line, a fraction of the way from one end to the other."""
    ends = BRIDGES[bridge_id]["ends"]
    a = ends[from_end]
    b = next(v for k, v in ends.items() if k != from_end)
    return (a[0] + (b[0] - a[0]) * fraction, a[1] + (b[1] - a[1]) * fraction)


RIDES = [
    {
        "id": "hawthorne-steel",
        "name": "Hawthorne out, Steel back",
        "start": "Eastbank Esplanade at the Steel Bridge",
        "note": "All on paths. Esplanade south, Hawthorne westbound, Waterfront Park north, Steel Bridge lower deck eastbound.",
        "crosses": "hawthorne westbound, steel eastbound",
        "via": [
            START,
            on_bridge("hawthorne", "east", 0.72),  # mid-river
            (45.52687, -122.67043),  # west landing of the Steel Bridge lower deck walkway
            (45.52764, -122.66875),  # on the lower deck walkway (the upper deck sidewalk is directly above)
            START,
        ],
    },
    {
        "id": "sellwood-loop",
        "name": "Sellwood loop",
        "start": "Eastbank Esplanade at the Steel Bridge",
        "note": "Esplanade south, Hawthorne westbound, Willamette Greenway Trail to the Sellwood Bridge, eastbound, Springwater north, Blumenauer northbound.",
        "crosses": "hawthorne westbound, sellwood eastbound, blumenauer northbound",
        "via": [
            START,
            on_bridge("hawthorne", "east", 0.72),
            tuple(BRIDGES["sellwood"]["ends"]["west"]),
            on_bridge("sellwood", "east", 0.57),  # mid-river
            (45.47170, -122.66108),  # on the Springwater on the Willamette trail
            tuple(BRIDGES["blumenauer"]["ends"]["south"]),
            tuple(BRIDGES["blumenauer"]["ends"]["north"]),
            START,
        ],
    },
]


def route(via: list[tuple[float, float]]) -> tuple[list[tuple[float, float]], int]:
    query = urllib.parse.urlencode({
        "lonlats": "|".join(f"{lon:.6f},{lat:.6f}" for lat, lon in via),
        "profile": "trekking",
        "alternativeidx": 0,
        "format": "geojson",
    })
    req = urllib.request.Request("https://brouter.de/brouter?" + query, headers={"User-Agent": "bridgeridepdx-tools (github.com/whitneyhale/bridgeridepdx)"})
    with urllib.request.urlopen(req, timeout=120) as r:
        feature = json.loads(r.read())["features"][0]
    points = [(lat, lon) for lon, lat, *_ in feature["geometry"]["coordinates"]]
    return points, int(feature["properties"]["track-length"])


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    for ride in RIDES:
        points, meters = route(ride["via"])
        lines = [
            f"# name: {ride['name']}",
            f"# start: {ride['start']}",
            f"# note: {ride['note']}",
            f"# crosses: {ride['crosses']}",
            f"# length_m: {meters}",
            "# source: BRouter (brouter.de), trekking profile, on OpenStreetMap data (c) OpenStreetMap contributors, ODbL. Built by tools/build_builtin_rides.py.",
        ]
        last = None
        for lat, lon in points:
            row = f"{lat:.6f},{lon:.6f}"
            if row != last:
                lines.append(row)
            last = row
        path = OUT / f"{ride['id']}.csv"
        path.write_text("\n".join(lines) + "\n", encoding="utf-8")
        print(f"wrote {path.relative_to(ROOT)}: {meters} m, {len(lines) - 6} points")


if __name__ == "__main__":
    main()
