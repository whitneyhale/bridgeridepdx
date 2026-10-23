"""Builds data/bridges.json from the hand-edited names file and the cached OpenStreetMap results.

Run order (each step writes to tools/.cache/):
  1. python tools/extract_bridges.py [pbf]   bridge ends, approach points, crossings
  2. python tools/validate_bridges.py        needs the routing engine on localhost:8989
  3. python tools/extract_lanes.py [pbf]     which side carries which direction
  4. python tools/extract_feeders.py [pbf]   bike routes near each approach
  5. python tools/build_bridges.py           writes data/bridges.json
"""
import json, os, math, datetime

HERE = os.path.dirname(os.path.abspath(__file__))
DATA = os.path.join(HERE, "..", "data")
CACHE = os.path.join(HERE, ".cache")


def load(name, default=None):
    path = os.path.join(CACHE, name)
    return json.load(open(path)) if os.path.exists(path) else default


_names = json.load(open(os.path.join(DATA, "bridge_names.json")))
names, CATS = _names["bridges"], _names["categories"]
geom = load("bridge_geometry.json")
valid = load("bridge_validation.json", {})
lanes = load("bridge_lanes.json", {})
feeders = load("bridge_feeders.json", {})

KY = 110574.0


def mid(bid):
    a, b = list(geom[bid]["ends"].values())
    return ((a[0] + b[0]) / 2, (a[1] + b[1]) / 2)


def dist_dir(a, b):
    kx = 111320.0 * math.cos(math.radians(a[0]))
    dy, dx = (b[0] - a[0]) * KY, (b[1] - a[1]) * kx
    brg = (math.degrees(math.atan2(dx, dy)) + 360) % 360
    return math.hypot(dx, dy) / 1000, ["N", "NE", "E", "SE", "S", "SW", "W", "NW"][int((brg + 22.5) // 45) % 8]


# Order: Willamette north (downstream) to south, then Columbia west to east, then overpasses
will = sorted([b for b in names if names[b].get("river") == "Willamette"], key=lambda b: names[b]["river_mile"])
col = sorted([b for b in names if names[b].get("river") == "Columbia"], key=lambda b: mid(b)[1])
other = [b for b in names if names[b].get("river") is None]
ORDER = will + col + other

# Right-hand traffic: a one-way bike lane on the right side of travel
EXPECTED_SIDE = {"eastbound": "south", "westbound": "north", "northbound": "east", "southbound": "west"}


def position(bid):
    n = names[bid]
    pos = {}
    if n.get("river") == "Willamette":
        i = will.index(bid)
        pos["sequence"] = f"Willamette bridge {i + 1} of {len(will)}, counted north (downstream) to south (upstream)"
        if i > 0:
            o = will[i - 1]
            pos["north_neighbor"] = {"id": o, "name": names[o]["name"], "river_miles": round(n["river_mile"] - names[o]["river_mile"], 1)}
        if i < len(will) - 1:
            o = will[i + 1]
            pos["south_neighbor"] = {"id": o, "name": names[o]["name"], "river_miles": round(names[o]["river_mile"] - n["river_mile"], 1)}
    elif n.get("river") == "Columbia":
        i = col.index(bid)
        pos["sequence"] = f"Columbia River crossing {i + 1} of {len(col)}, counted west (downstream) to east. Connects Portland, OR to Vancouver, WA"
        for j, key in ((i - 1, "west_neighbor"), (i + 1, "east_neighbor")):
            if 0 <= j < len(col):
                o = col[j]
                km, _ = dist_dir(mid(bid), mid(o))
                pos[key] = {"id": o, "name": names[o]["name"], "km": round(km, 1)}
    else:
        pos["sequence"] = "Not a river bridge"
    near = []
    for o in ORDER:
        if o == bid:
            continue
        km, d = dist_dir(mid(bid), mid(o))
        near.append((km, o, d))
    near.sort()
    pos["nearest"] = [{"id": o, "name": names[o]["name"], "km": round(km, 1), "direction": d} for km, o, d in near[:3]]
    return pos


entries = []
for bid in ORDER:
    n, g = names[bid], geom[bid]
    offered = n["bike_access"] in ("yes", "limited") and bid != "ross-island" and n["category"] not in ("car_only", "rail_only")
    bl = lanes.get(bid, [])
    crossings = {}
    for direction, c in g["crossings"].items():
        row = {"offered": offered, "from": c["from"], "to": c["to"]}
        v = valid.get(bid, {}).get(direction)
        if v:
            row["validated"] = bool(v["ok"])
            row["validation"] = (f"route {v['route_m']} m vs {v['straight_m']} m straight "
                                 f"(x{v['detour_ratio']}), {v['on_bridge_m']} m on the bridge")
        elif offered:
            row["validated"] = None
        side = EXPECTED_SIDE[direction]
        # a lane counts only if it covers at least 40% of the span (short fragments are ramps or tagging noise)
        min_len = 0.4 * (g["span_m"] or 0)
        one_way = [l for l in bl if l["direction"] == direction and l["side"] == side and l["length_m"] >= min_len]
        two_way = [l for l in bl if l["direction"] == "both" and l["length_m"] >= min_len]
        if one_way:
            row["lane"] = {"side": side, "type": "one-way", "osm_ways": one_way[0]["osm_ways"]}
        elif two_way:
            biggest = max(two_way, key=lambda l: l["length_m"])
            if any("+lane" in k for k in biggest["kinds"]):
                row["lane"] = {"side": side, "type": "roadway bike lane, keep right", "osm_ways": biggest["osm_ways"]}
            else:
                row["lane"] = {"side": biggest["side"], "type": "two-way", "osm_ways": biggest["osm_ways"]}
        else:
            row["lane"] = {"side": None, "type": "unknown"}
        crossings[direction] = row
    near_routes = {}
    for side, rows in feeders.get(bid, {}).items():
        own = n["name"].split()[0].lower()
        near_routes[side] = [{"name": r["name"], "kind": r["kind"], "distance_m": r["distance_m"], "direction": r["direction"]}
                             for r in rows if r["kind"] in ("path", "route") and own not in r["name"].lower()][:6]
    entries.append({
        "id": bid, "name": n["name"], "category": n["category"], "category_label": CATS[n["category"]].split(":")[0],
        "modes": n["modes"], "bike_facility": n["bike_facility"], "transit": n.get("transit"), "river": n.get("river"),
        "river_mile": n.get("river_mile"), "position": position(bid),
        "aliases": n["aliases"], "misspellings": n["misspellings"],
        "colors": n["colors"], "colors_verified": n.get("colors_verified"), "colors_note": n.get("colors_note"),
        "year": n["year"], "type": n["type"], "carries": n["carries"], "streets": n.get("streets", []),
        "ends_described": n.get("ends", {}),
        "county_bridge_code": n.get("county_code"), "bike_access": n["bike_access"], "bike_note": n.get("bike_note"),
        "preference": n.get("preference", "normal"),
        "axis": g["axis"], "ends": g["ends"], "approaches": g["approaches"],
        # Marquam's extent includes the freeway, so its length is not meaningful.
        "span_m": None if bid == "marquam" else g["span_m"],
        "crossings": crossings, "lanes": bl, "nearby_bike_routes": near_routes,
        "osm_way_ids": g["osm_ways"],
    })

doc = {
    "generated": datetime.date.today().isoformat(),
    "convention": ("Points are [lat, lon], about 60 m outside each bridge end so they snap to a street or path. "
                   "East-west bridges have eastbound (west to east) and westbound crossings. North-south bridges "
                   "(Columbia, Blumenauer) have northbound and southbound. lane.side is the side of the bridge a rider "
                   "uses for that direction: keep right (rule E-01)."),
    "order": "Willamette north to south, then Columbia west to east, then other crossings.",
    "categories": CATS,
    "attribution": "Positions derived from OpenStreetMap (c) OpenStreetMap contributors, ODbL.",
    "bridges": entries,
}
with open(os.path.join(DATA, "bridges.json"), "w") as f:
    json.dump(doc, f, indent=1)

ok = sum(1 for e in entries for c in e["crossings"].values() if c.get("validated") is True)
total = sum(1 for e in entries for c in e["crossings"].values() if c["offered"])
print(f"wrote data/bridges.json: {len(entries)} bridges, {ok} of {total} offered crossings validated")
