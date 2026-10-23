# Finds each bridge's west and east ends in the Portland OpenStreetMap extract and offsets 60 m outward to get approach points.
# Needs: pip install osmium; routing/Portland.osm.pbf (run routing/start.bat once to download it).
import osmium, math, json, re, collections
import os
HERE = os.path.dirname(os.path.abspath(__file__))
import sys
PBF = sys.argv[1] if len(sys.argv) > 1 else os.path.join(HERE, "..", "routing", "Portland.osm.pbf")
CACHE = os.path.join(HERE, ".cache"); os.makedirs(CACHE, exist_ok=True)
TARGETS = {
 "st-johns": r"^st\.? johns bridge|^saint johns bridge", "fremont": r"^fremont bridge", "broadway": r"^broadway bridge",
 "steel": r"^steel bridge", "burnside": r"^burnside bridge", "morrison": r"^morrison bridge", "hawthorne": r"^hawthorne bridge",
 "marquam": r"^marquam", "tilikum": r"^tilikum", "ross-island": r"^ross island bridge", "sellwood": r"^sellwood bridge",
}
# Columbia River crossings to Vancouver, WA run north-south. Their bike paths are separate OSM ways from the
# motorway deck (which is tagged bicycle=no), so they are listed by way id.
COLUMBIA = {
 "interstate-5": [30373423, 30374329, 328907395, 328907396, 345802111, 345802114, 345802153, 345802165],
 "interstate-205": [334634081, 334634082],
 # Not a river bridge: the Blumenauer bike and pedestrian bridge over I-84 runs north-south too.
 "blumenauer": [1123764541],
}
def hav(a, b):
    R = 6371000; la1, lo1, la2, lo2 = map(math.radians, (a[0], a[1], b[0], b[1]))
    return 2*R*math.asin(math.sqrt(math.sin((la2-la1)/2)**2 + math.cos(la1)*math.cos(la2)*math.sin((lo2-lo1)/2)**2))
def bikeable(t):
    return t.get("highway") not in (None, "motorway", "motorway_link", "trunk_link", "steps", "platform") and t.get("bicycle") != "no"
ways = collections.defaultdict(list)
for o in osmium.FileProcessor(PBF, osmium.osm.NODE | osmium.osm.WAY).with_locations().with_filter(osmium.filter.KeyFilter("bridge")):
    nm = o.tags.get("name") or ""
    for bid, pat in TARGETS.items():
        if re.search(pat, nm, re.I):
            ways[bid].append(dict(id=o.id, tags=dict(o.tags), pts=[(n.lat, n.lon) for n in o.nodes if n.location.valid()]))
OFFSET_M = 60
KY = 110574
def offset(p, ux, uy, kx):
    return [round(p[0] + uy*OFFSET_M/KY, 6), round(p[1] + ux*OFFSET_M/kx, 6)]

colways = collections.defaultdict(list)
want = {i: bid for bid, ids in COLUMBIA.items() for i in ids}
for o in osmium.FileProcessor(PBF, osmium.osm.NODE | osmium.osm.WAY).with_locations().with_filter(osmium.filter.KeyFilter("highway")):
    if o.is_way() and o.id in want:
        colways[want[o.id]].append(dict(id=o.id, tags=dict(o.tags), pts=[(n.lat, n.lon) for n in o.nodes if n.location.valid()]))

out = {}
def finish(bid, use, axis):
    pts = [p for w in use for p in w["pts"]]
    kx = 111320 * math.cos(math.radians(pts[0][0]))
    if axis == "ew":
        A = min(pts, key=lambda p: p[1]); B = max(pts, key=lambda p: p[1]); na, nb = "west", "east"
    else:
        A = min(pts, key=lambda p: p[0]); B = max(pts, key=lambda p: p[0]); na, nb = "south", "north"
    L = hav(A, B)
    dx = (A[1]-B[1]) * kx; dy = (A[0]-B[0]) * KY; n = math.hypot(dx, dy) or 1
    ap = {na: offset(A, dx/n, dy/n, kx), nb: offset(B, -dx/n, -dy/n, kx)}
    names = (("eastbound", "westbound") if axis == "ew" else ("northbound", "southbound"))
    cross = {names[0]: dict(**{"from": ap[na], "to": ap[nb]}), names[1]: dict(**{"from": ap[nb], "to": ap[na]})}
    hw = collections.Counter((w["tags"].get("highway") or "rail", w["tags"].get("bicycle", "-")) for w in use)
    bk = [w for w in use if bikeable(w["tags"])]
    out[bid] = dict(axis=axis, ends={na: [round(A[0],6), round(A[1],6)], nb: [round(B[0],6), round(B[1],6)]}, approaches=ap, crossings=cross,
                    span_m=round(L), osm_ways=sorted(w["id"] for w in use), pts=[list(p) for p in pts],
                    tag_summary={f"{h}/{b}": c for (h, b), c in hw.items()})

for bid, ws in ways.items():
    bk = [w for w in ws if bikeable(w["tags"])]
    finish(bid, bk or ws, "ew")
for bid, ws in colways.items():
    finish(bid, ws, "ns")
json.dump(out, open(os.path.join(CACHE, "bridge_geometry.json"), "w"), indent=1)
for bid, d in out.items():
    print(f"{bid:15s} {d['axis']} span {d['span_m']:5d} m  ends {d['ends']}  approaches {d['approaches']}")
