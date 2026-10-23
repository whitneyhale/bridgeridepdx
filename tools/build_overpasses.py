"""Builds data/overpasses.json and routing/overpass_model.json from the extracted overpasses.

Run: python tools/extract_overpasses.py ; python tools/build_overpasses.py
Weights below are proposals (rule O-04, O-05): low = 0.5, preferred = 1.5.
"""
import json, math, os, re

HERE = os.path.dirname(os.path.abspath(__file__))
DATA = os.path.join(HERE, "..", "data")
CACHE = os.path.join(HERE, ".cache")
ROUTING = os.path.join(HERE, "..", "routing")
LOW_WEIGHT, PREFERRED_WEIGHT = 0.5, 1.5
BUFFER_M = 14

raw = json.load(open(os.path.join(CACHE, "overpasses_raw.json")))
geom = json.load(open(os.path.join(CACHE, "bridge_geometry.json")))
ov = json.load(open(os.path.join(DATA, "overpass_overrides.json")))
defaults, overrides = ov["defaults"], ov["overrides"]

river_bridge_ways = {w for bid, g in geom.items() if bid != "blumenauer" for w in g["osm_ways"]}
BIG_BRIDGE_NAMES = re.compile(r"^(broadway|morrison|hawthorne|saint johns|st\.? johns|sellwood|interstate|tilikum|steel|burnside|ross island|fremont|marquam|glenn l\. jackson)\b.*(bridge|crossing)", re.I)
TRAIL_NAMES = re.compile(r"multi-use path|springwater|fanno|willamette greenway|esplanade|burnt bridge|peninsula crossing|i-84 (bike|corridor)|i 84 corridor|i-205", re.I)
KY = 110574.0
KX = 111320.0 * math.cos(math.radians(45.52))


def hull(points):
    pts = sorted(set(points))
    if len(pts) <= 2:
        return pts
    def cross(o, a, b):
        return (a[0]-o[0])*(b[1]-o[1]) - (a[1]-o[1])*(b[0]-o[0])
    lo = []
    for p in pts:
        while len(lo) >= 2 and cross(lo[-2], lo[-1], p) <= 0:
            lo.pop()
        lo.append(p)
    up = []
    for p in reversed(pts):
        while len(up) >= 2 and cross(up[-2], up[-1], p) <= 0:
            up.pop()
        up.append(p)
    return lo[:-1] + up[:-1]


def buffered_hull(pts):
    ring = []
    for lat, lon in pts:
        for k in range(8):
            a = math.radians(k * 45)
            ring.append((round(lat + math.cos(a) * BUFFER_M / KY, 7), round(lon + math.sin(a) * BUFFER_M / KX, 7)))
    h = hull(ring)
    return [[lon, lat] for lat, lon in h] + [[h[0][1], h[0][0]]]


def effort(r):
    if r["steps_attached"] or r["bicycle"] == "dismount" or r["length_m"] >= 150:
        return "high"
    return "medium" if r["length_m"] >= 80 else "low"


def over_text(r):
    parts = []
    for c in r["crosses"]:
        parts.append(c["kind"] + (" " + c["name"] if c["name"] else ""))
    return ", ".join(parts)


records, skipped = [], 0
for r in raw:
    if r["osm_way"] in river_bridge_ways or (r["name"] and BIG_BRIDGE_NAMES.match(r["name"])):
        skipped += 1
        continue
    o = overrides.get(str(r["osm_way"]), {})
    kinds = sorted({c["kind"] for c in r["crosses"]})
    rec = {
        "id": o.get("id") or "op-%d" % r["osm_way"],
        "label": "overpass",
        "name": o.get("name") or r["name"] or ("Overpass over " + over_text(r)),
        "named_in_osm": bool(r["name"]),
        "aliases": o.get("aliases", []),
        "misspellings": o.get("misspellings", []),
        "osm_way_ids": [r["osm_way"]] + o.get("also_ways", []),
        "start": r["start"], "end": r["end"],
        "length_m": r["length_m"],
        "crosses": r["crosses"],
        "crosses_kinds": kinds,
        "near_rail": any(k in ("railroad", "MAX light rail", "streetcar or tram") for k in kinds),
        "category": "mixed_use_overpass" if (r["highway"] == "cycleway" and re.search(r"(avenue|street|boulevard|road|drive)$", r["name"] or "", re.I)) else "pedestrian_overpass",
        "structure_type": "bike lane on a street overpass" if (r["highway"] == "cycleway" and re.search(r"(avenue|street|boulevard|road|drive)$", r["name"] or "", re.I)) else "bike and pedestrian bridge",
        "bike_access": {"dismount": "walk your bike", "designated": "bikes allowed", "yes": "bikes allowed", "permissive": "bikes allowed"}.get(r["bicycle"], "bikes allowed"),
        "steps_attached": r["steps_attached"],
        "effort": effort(r),
        "part_of_trail": bool(r["name"] and TRAIL_NAMES.match(r["name"])) or bool(TRAIL_NAMES.search(r["name"] or "")),
        "preference": o.get("preference", defaults["preference"]),
        "preference_reason": o.get("preference_reason", defaults["preference_reason"]),
        "weight": PREFERRED_WEIGHT if o.get("preference") == "preferred" else LOW_WEIGHT,
        "polygon": buffered_hull([tuple(p) for p in r["pts"]]),
    }
    for k in ("crosses_text", "street", "south_end", "north_end", "length_ft", "width_ft", "structure", "opened", "part_of", "sources"):
        if k in o:
            rec[k] = o[k]
    records.append(rec)

records.sort(key=lambda r: (-r["start"][0], r["id"]))
doc = {
    "generated": "2026-10-23",
    "label": "overpass",
    "rules": "docs/routing-rules.md, O-01 to O-08",
    "attribution": "Positions derived from OpenStreetMap (c) OpenStreetMap contributors, ODbL.",
    "coverage": "Portland area of the BBBike extract, lat 45.43 to 45.66 (includes Vancouver, WA). Foot, path, and cycle bridges over railways, MAX, streetcar, freeways, and major roads. River bridges are in bridges.json.",
    "counts": {"overpasses": len(records), "preferred": sum(1 for r in records if r["preference"] == "preferred"),
               "near_rail": sum(1 for r in records if r["near_rail"]), "skipped_river_bridge_parts": skipped},
    "overpasses": [{k: v for k, v in r.items() if k != "polygon"} for r in records],
}
json.dump(doc, open(os.path.join(DATA, "overpasses.json"), "w"), indent=1)

features = [{"type": "Feature", "id": "op_%d" % r["osm_way_ids"][0], "properties": {},
             "geometry": {"type": "Polygon", "coordinates": [r["polygon"]]}} for r in records]
priority = [{"if": "in_op_%d" % r["osm_way_ids"][0], "multiply_by": str(r["weight"])} for r in records]
model = {"_comment": "Generated by tools/build_overpasses.py. Merge into a profile's custom model (or send as custom_model on a request).",
         "priority": priority, "areas": {"type": "FeatureCollection", "features": features}}
json.dump(model, open(os.path.join(ROUTING, "overpass_model.json"), "w"))
print("wrote data/overpasses.json:", doc["counts"])
print("wrote routing/overpass_model.json with", len(features), "areas")
