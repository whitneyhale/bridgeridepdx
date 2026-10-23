"""Builds data/bridges.json from the hand-edited names file, the extracted geometry, and the validation results.

Run order:
  1. python tools/extract_bridges.py     (needs routing/Portland.osm.pbf)
  2. start the routing engine, then: python tools/validate_bridges.py
  3. python tools/build_bridges.py
"""
import json, os, datetime

HERE = os.path.dirname(os.path.abspath(__file__))
DATA = os.path.join(HERE, "..", "data")
CACHE = os.path.join(HERE, ".cache")

names = json.load(open(os.path.join(DATA, "bridge_names.json")))["bridges"]
geom = json.load(open(os.path.join(CACHE, "bridge_geometry.json")))
valid_path = os.path.join(CACHE, "bridge_validation.json")
valid = json.load(open(valid_path)) if os.path.exists(valid_path) else {}

ORDER = ["st-johns", "fremont", "broadway", "steel", "burnside", "morrison", "hawthorne",
         "marquam", "tilikum", "ross-island", "sellwood"]

entries = []
for bid in ORDER:
    n, g = names[bid], geom[bid]
    offered = n["bike_access"] in ("yes", "limited")
    crossings = {}
    for direction, frm, to in (("eastbound", "west_approach", "east_approach"),
                               ("westbound", "east_approach", "west_approach")):
        c = {"offered": offered, "from": g[frm], "to": g[to]}
        v = valid.get(bid, {}).get(direction)
        if v:
            c["validated"] = bool(v["ok"])
            c["validation"] = (f"route {v['route_m']} m vs {v['straight_m']} m straight "
                               f"(x{v['detour_ratio']}), {v['on_bridge_m']} m on the bridge")
        elif offered:
            c["validated"] = None
        crossings[direction] = c
    entries.append({
        "id": bid, "name": n["name"], "aliases": n["aliases"], "misspellings": n["misspellings"],
        "colors": n["colors"], "year": n["year"], "type": n["type"], "carries": n["carries"],
        "county_bridge_code": n["county_code"], "bike_access": n["bike_access"],
        "bike_note": n.get("bike_note"),
        "west_end": g["west_end"], "east_end": g["east_end"],
        # Marquam's extent includes the freeway, so its length is not meaningful.
        "span_m": None if bid == "marquam" else g["span_m"],
        "crossings": crossings,
        "osm_way_ids": g["osm_ways"],
    })

doc = {
    "generated": datetime.date.today().isoformat(),
    "convention": ("eastbound = from the west approach to the east approach. Points are [lat, lon], "
                   "placed about 60 m outside each bridge end so the router can snap to the street or path."),
    "attribution": "Positions derived from OpenStreetMap (c) OpenStreetMap contributors, ODbL.",
    "bridges": entries,
}
with open(os.path.join(DATA, "bridges.json"), "w") as f:
    json.dump(doc, f, indent=1)

ok = sum(1 for e in entries for c in e["crossings"].values() if c.get("validated") is True)
total = sum(1 for e in entries for c in e["crossings"].values() if c["offered"])
print(f"wrote data/bridges.json: {len(entries)} bridges, {ok} of {total} offered crossings validated")
