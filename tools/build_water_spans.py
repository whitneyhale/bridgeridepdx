"""Find where each bridge is over water, for bridge-crossing detection in the app.

For every bridge in data/bridges.json, walks the straight line between its two ends and checks
each step against the water polygons in OpenFreeMap's vector tiles (OpenStreetMap data, the same
tiles the app draws). Writes data/water_spans.json: for each end, "bank" is how far along the line,
from that end toward the other, the water starts (0 to 1).

Riverbanks are not square to the bridge, and riders reach a bridge by paths beside it (the Steel
Bridge lower deck lands 70 m south of the bridge's line, where the bank is 40 m farther out). So
the bank is measured on five parallel lines: the bridge's own line and lines half a corridor and a
full corridor to each side. "bank" lists the five values from left to right, as seen facing from
the first end ("from") to the second ("to"). The corridor is the same one the app uses:
60 m plus a tenth of the length, between 100 and 400 m.

The app counts a crossing when a ride goes from the land at one end to the land at the other
without leaving the bridge (android/core Crossings.kt). The ends in bridges.json are where the
bridge meets the street grid, often far from the river, and riders join by ramps near the bank,
so the ends alone cannot tell a crossing from a ride along the approach.

Needs the network and two packages: pip install mapbox-vector-tile shapely
Run by hand when bridges.json changes; CI only checks that the app's CSV matches the two data files.
A bridge that is not over water (Blumenauer, over I-84) is set in NOT_WATER below.
"""
import datetime
import json
import math
import pathlib
import urllib.request

import mapbox_vector_tile
from shapely.geometry import Point, shape
from shapely.ops import unary_union

ROOT = pathlib.Path(__file__).resolve().parent.parent
SRC = ROOT / "data" / "bridges.json"
OUT = ROOT / "data" / "water_spans.json"
TILEJSON = "https://tiles.openfreemap.org/planet"
ZOOM = 14
STEP_M = 2.0
MIN_WATER_M = 40.0  # shorter stretches are ponds and fountains, not the river
MERGE_GAP_M = 25.0  # piers and mapping slivers inside the river

# Bridges over something other than water: bank fractions set by hand, with the reason.
NOT_WATER = {
    "blumenauer": {"bank": 0.2, "why": "Crosses I-84 and the Union Pacific tracks, not water. The whole 132 m deck is the crossing."},
}


def fetch(url: str) -> bytes:
    req = urllib.request.Request(url, headers={"User-Agent": "bridgeridepdx-tools (github.com/whitneyhale/bridgeridepdx)"})
    with urllib.request.urlopen(req, timeout=60) as r:
        return r.read()


def tile_of(lat: float, lon: float) -> tuple[int, int]:
    n = 2**ZOOM
    x = int((lon + 180.0) / 360.0 * n)
    y = int((1.0 - math.asinh(math.tan(math.radians(lat))) / math.pi) / 2.0 * n)
    return x, y


def tile_water(template: str, x: int, y: int, cache: dict):
    """Water polygons of one tile, in lon/lat."""
    if (x, y) in cache:
        return cache[(x, y)]
    data = mapbox_vector_tile.decode(fetch(template.format(z=ZOOM, x=x, y=y)), default_options={"y_coord_down": True})
    layer = data.get("water", {"features": [], "extent": 4096})
    extent = layer.get("extent", 4096)
    n = 2**ZOOM

    def to_lonlat(px: float, py: float) -> tuple[float, float]:
        lon = (x + px / extent) / n * 360.0 - 180.0
        lat = math.degrees(math.atan(math.sinh(math.pi * (1 - 2 * (y + py / extent) / n))))
        return lon, lat

    def convert(coords):
        if isinstance(coords[0], (int, float)):
            return to_lonlat(coords[0], coords[1])
        return [convert(c) for c in coords]

    polys = []
    for f in layer["features"]:
        g = f["geometry"]
        if g["type"] not in ("Polygon", "MultiPolygon"):
            continue
        geom = shape({"type": g["type"], "coordinates": convert(g["coordinates"])})
        polys.append(geom if geom.is_valid else geom.buffer(0))
    cache[(x, y)] = unary_union(polys) if polys else None
    return cache[(x, y)]


def haversine(a, b) -> float:
    r = 6371008.8
    d_lat = math.radians(b[0] - a[0])
    d_lon = math.radians(b[1] - a[1])
    h = math.sin(d_lat / 2) ** 2 + math.cos(math.radians(a[0])) * math.cos(math.radians(b[0])) * math.sin(d_lon / 2) ** 2
    return 2 * r * math.asin(math.sqrt(h))


LATERALS = [-1.0, -0.5, 0.0, 0.5, 1.0]  # in corridors, left to right facing from the first end to the second


def corridor_m(length_m: float) -> float:
    return max(100.0, min(400.0, 60.0 + 0.1 * length_m))


def bearing(a, b) -> float:
    la, lb = math.radians(a[0]), math.radians(b[0])
    dl = math.radians(b[1] - a[1])
    return (math.degrees(math.atan2(math.sin(dl) * math.cos(lb), math.cos(la) * math.sin(lb) - math.sin(la) * math.cos(lb) * math.cos(dl))) + 360) % 360


def shifted(p, bearing_deg: float, meters: float):
    r = 6371008.8
    b = math.radians(bearing_deg)
    return [p[0] + math.degrees(meters * math.cos(b) / r), p[1] + math.degrees(meters * math.sin(b) / (r * math.cos(math.radians(p[0]))))]


def water_runs(a, b, template: str, cache: dict) -> tuple[float, list[list[float]]]:
    """Length of the line a-b and the stretches of it over water, as fractions from a."""
    length = haversine(a, b)
    steps = max(2, int(length / STEP_M))
    wet = []
    for i in range(steps + 1):
        t = i / steps
        lat = a[0] + (b[0] - a[0]) * t
        lon = a[1] + (b[1] - a[1]) * t
        water = tile_water(template, *tile_of(lat, lon), cache)
        wet.append(water is not None and water.contains(Point(lon, lat)))
    runs = []
    start = None
    for i, w in enumerate(wet + [False]):
        if w and start is None:
            start = i
        if not w and start is not None:
            runs.append([start / steps, (i - 1) / steps])
            start = None
    merged = []
    for r in runs:
        if merged and (r[0] - merged[-1][1]) * length <= MERGE_GAP_M:
            merged[-1][1] = r[1]
        else:
            merged.append(r)
    return length, [r for r in merged if (r[1] - r[0]) * length >= MIN_WATER_M]


def main() -> None:
    tilejson = json.loads(fetch(TILEJSON))
    template = tilejson["tiles"][0]
    bridges = json.loads(SRC.read_text(encoding="utf-8"))["bridges"]
    cache: dict = {}
    out = []
    for b in bridges:
        names = sorted(b["ends"])
        a, c = b["ends"][names[0]], b["ends"][names[1]]
        if b["id"] in NOT_WATER:
            fixed = NOT_WATER[b["id"]]
            length = haversine(a, c)
            out.append({"id": b["id"], "from": names[0], "to": names[1], "length_m": round(length), "corridor_m": round(corridor_m(length)),
                        "water": [], "bank": {names[0]: [fixed["bank"]] * 5, names[1]: [fixed["bank"]] * 5}, "note": fixed["why"]})
            continue
        length, runs = water_runs(a, c, template, cache)
        if not runs:
            raise SystemExit(f"{b['id']}: no water found between its ends; add it to NOT_WATER or check the ends")
        right = bearing(a, c) + 90.0
        corridor = corridor_m(length)
        banks = {names[0]: [], names[1]: []}
        for k in LATERALS:
            _, side_runs = water_runs(shifted(a, right, k * corridor), shifted(c, right, k * corridor), template, cache)
            banks[names[0]].append(round(side_runs[0][0], 3) if side_runs else None)
            banks[names[1]].append(round(1 - side_runs[-1][1], 3) if side_runs else None)
        for end in names:  # a side line that misses the water takes the value of the bridge's own line
            banks[end] = [v if v is not None else banks[end][2] for v in banks[end]]
        out.append({
            "id": b["id"], "from": names[0], "to": names[1], "length_m": round(length), "corridor_m": round(corridor),
            "water": [[round(r[0], 3), round(r[1], 3)] for r in runs],
            "bank": banks,
        })
        print(f"{b['id']:15s} {round(length):5d} m  {names[0]} {banks[names[0]]}  {names[1]} {banks[names[1]]}")
    doc = {
        "generated": datetime.date.today().isoformat(),
        "source": f"OpenFreeMap vector tiles, water layer, zoom {ZOOM} ({template.split('/')[4]})",
        "attribution": "OpenFreeMap, (c) OpenMapTiles, data from OpenStreetMap contributors (ODbL)",
        "convention": "bank[end] is the fraction of the way from that end to the other end at which the water starts, on five parallel lines: 1 and 0.5 corridors to the left, the bridge's own line, 0.5 and 1 corridors to the right, facing from 'from' to 'to'.",
        "bridges": out,
    }
    OUT.write_text(json.dumps(doc, indent=1) + "\n", encoding="utf-8")
    print(f"wrote {OUT.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
