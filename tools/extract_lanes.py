"""Finds which side of each bridge carries which direction of bike traffic, from OpenStreetMap tags.

For each bridge in tools/.cache/bridge_geometry.json it reads the bike-usable ways on the bridge, works out the side
(north/south of an east-west bridge, east/west of a north-south bridge) and the travel direction from the way's
geometry and its oneway tag, and writes tools/.cache/bridge_lanes.json.

A lane has an entry point (where a rider joins it) and an exit point (where they leave it). The app should force the
route through the entry and exit of the lane that matches the rider's direction (rules E-01 to E-06).

Usage: python tools/extract_lanes.py [path/to/Portland.osm.pbf]
"""
import osmium, math, json, os, sys, collections

HERE = os.path.dirname(os.path.abspath(__file__))
PBF = sys.argv[1] if len(sys.argv) > 1 else os.path.join(HERE, "..", "routing", "Portland.osm.pbf")
CACHE = os.path.join(HERE, ".cache")
geom = json.load(open(os.path.join(CACHE, "bridge_geometry.json")))
KY = 110574.0
KX = 111320.0 * math.cos(math.radians(45.52))
want = {w: bid for bid, g in geom.items() for w in g["osm_ways"]}


def bearing(a, b):
    return (math.degrees(math.atan2((b[1]-a[1])*KX, (b[0]-a[0])*KY)) + 360) % 360


def bike_usable(t):
    if t.get("bicycle") == "no":
        return False
    hw = t.get("highway")
    if hw == "cycleway":
        return True
    if hw in ("path", "footway", "pedestrian") and t.get("bicycle") in ("designated", "yes", "permissive"):
        return True
    return any(t.get(k) in ("lane", "track") for k in ("cycleway", "cycleway:left", "cycleway:right", "cycleway:both"))


def dir_label(axis, brg):
    if axis == "ew":
        return "eastbound" if 0 < brg < 180 else "westbound"
    return "northbound" if (brg < 90 or brg > 270) else "southbound"


ways = collections.defaultdict(list)
for o in osmium.FileProcessor(PBF, osmium.osm.NODE | osmium.osm.WAY).with_locations():
    if o.is_way() and o.id in want and bike_usable(o.tags):
        pts = [(n.lat, n.lon) for n in o.nodes if n.location.valid()]
        if len(pts) >= 2:
            ways[want[o.id]].append(dict(id=o.id, tags=dict(o.tags), pts=pts))

out = {}
for bid, ws in ways.items():
    g = geom[bid]
    axis = g["axis"]
    ends = list(g["ends"].values())  # two end points
    (a_lat, a_lon), (b_lat, b_lon) = ends
    lanes = collections.defaultdict(lambda: dict(ways=[], length_m=0.0))
    for w in ws:
        pts = w["pts"]
        mid = pts[len(pts)//2]
        # side relative to the line between the bridge ends
        if axis == "ew":
            t = (mid[1] - a_lon) / ((b_lon - a_lon) or 1e-9)
            line_lat = a_lat + t * (b_lat - a_lat)
            side = "north" if mid[0] > line_lat else "south"
        else:
            t = (mid[0] - a_lat) / ((b_lat - a_lat) or 1e-9)
            line_lon = a_lon + t * (b_lon - a_lon)
            side = "east" if mid[1] > line_lon else "west"
        ow = w["tags"].get("oneway")
        ow_b = w["tags"].get("oneway:bicycle")
        oneway = (ow_b or ow)
        if oneway in ("yes", "1", "true"):
            seq = pts
            d = dir_label(axis, bearing(pts[0], pts[-1]))
        elif oneway == "-1":
            seq = list(reversed(pts))
            d = dir_label(axis, bearing(seq[0], seq[-1]))
        else:
            seq = pts
            d = "both"
        key = (side, d)
        L = sum(math.hypot((q[0]-p[0])*KY, (q[1]-p[1])*KX) for p, q in zip(seq, seq[1:]))
        lanes[key]["ways"].append(w["id"])
        lanes[key]["length_m"] += L
        lanes[key].setdefault("kinds", set()).add(w["tags"].get("highway") + (("+" + w["tags"].get("cycleway:right")) if w["tags"].get("cycleway:right") else ""))
    out[bid] = [dict(side=s, direction=d, length_m=round(v["length_m"]), osm_ways=sorted(v["ways"]), kinds=sorted(v["kinds"]))
                for (s, d), v in sorted(lanes.items())]

json.dump(out, open(os.path.join(CACHE, "bridge_lanes.json"), "w"), indent=1)
for bid, ls in out.items():
    print(bid)
    for l in ls:
        print("   ", l["side"].ljust(5), l["direction"].ljust(10), str(l["length_m"]).rjust(5), "m", len(l["osm_ways"]), "ways", l["kinds"])
