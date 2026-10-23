# Finds each bridge's west and east ends in the Portland OpenStreetMap extract and offsets 60 m outward to get approach points.
# Needs: pip install osmium; routing/Portland.osm.pbf (run routing/start.bat once to download it).
import osmium, math, json, re, collections
import os
HERE = os.path.dirname(os.path.abspath(__file__))
PBF = os.path.join(HERE, "..", "routing", "Portland.osm.pbf")
CACHE = os.path.join(HERE, ".cache"); os.makedirs(CACHE, exist_ok=True)
TARGETS = {
 "st-johns": r"^st\.? johns bridge|^saint johns bridge", "fremont": r"^fremont bridge", "broadway": r"^broadway bridge",
 "steel": r"^steel bridge", "burnside": r"^burnside bridge", "morrison": r"^morrison bridge", "hawthorne": r"^hawthorne bridge",
 "marquam": r"^marquam", "tilikum": r"^tilikum", "ross-island": r"^ross island bridge", "sellwood": r"^sellwood bridge",
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
out = {}
for bid, ws in ways.items():
    bk = [w for w in ws if bikeable(w["tags"])]
    use = bk or ws
    pts = [p for w in use for p in w["pts"]]
    W = min(pts, key=lambda p: p[1]); E = max(pts, key=lambda p: p[1])
    L = hav(W, E)
    # unit vector from E to W in meters-ish
    kx = 111320 * math.cos(math.radians(W[0])); ky = 110574
    dx = (W[1]-E[1]) * kx; dy = (W[0]-E[0]) * ky; n = math.hypot(dx, dy) or 1
    def off(p, sx, sy): return (round(p[0] + sy*OFFSET_M/ky, 6), round(p[1] + sx*OFFSET_M/kx, 6))
    wa = off(W, dx/n, dy/n); ea = off(E, -dx/n, -dy/n)
    hw = collections.Counter((w["tags"].get("highway") or "rail", w["tags"].get("bicycle", "-")) for w in use)
    out[bid] = dict(west_end=[round(W[0],6), round(W[1],6)], east_end=[round(E[0],6), round(E[1],6)], west_approach=list(wa), east_approach=list(ea),
                    span_m=round(L), osm_ways=sorted(w["id"] for w in use), pts=[list(p) for p in pts], bikeable_ways=len(bk), all_ways=len(ws),
                    tag_summary={f"{h}/{b}": c for (h, b), c in hw.items()})
json.dump(out, open(os.path.join(CACHE, "bridge_geometry.json"), "w"), indent=1)
for bid, d in out.items():
    print(f"{bid:12s} span {d['span_m']:5d} m  bikeable {d['bikeable_ways']}/{d['all_ways']}  W {d['west_end']} -> approach {d['west_approach']}   E {d['east_end']} -> approach {d['east_approach']}")
