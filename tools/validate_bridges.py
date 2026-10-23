# Routes each bridge in both directions with the local routing engine (routing/start.bat must be running) and checks the route
# follows the bridge and does not detour more than 1.8x.
import json, math, urllib.request, urllib.parse
import os
HERE = os.path.dirname(os.path.abspath(__file__)); CACHE = os.path.join(HERE, ".cache")
G = json.load(open(os.path.join(CACHE, "bridge_geometry.json")))
def hav(a, b):
    R = 6371000; la1, lo1, la2, lo2 = map(math.radians, (a[0], a[1], b[0], b[1]))
    return 2*R*math.asin(math.sqrt(math.sin((la2-la1)/2)**2 + math.cos(la1)*math.cos(la2)*math.sin((lo2-lo1)/2)**2))
def route(a, b, profile="bike"):
    q = [("profile", profile), ("points_encoded", "false"), ("instructions", "true"), ("point", f"{a[0]},{a[1]}"), ("point", f"{b[0]},{b[1]}")]
    d = json.load(urllib.request.urlopen("http://localhost:8989/route?" + urllib.parse.urlencode(q), timeout=60))
    p = d["paths"][0]
    line = [(c[1], c[0]) for c in p["points"]["coordinates"]]
    names = []
    for i in p["instructions"]:
        n = i.get("street_name") or "(unnamed)"
        if not names or names[-1] != n: names.append(n)
    return p["distance"], line, names
def densify(pts, step=8):
    # pts are an unordered cloud from several ways; interpolate between consecutive points that are close (same way order)
    out = list(pts)
    for a, b in zip(pts, pts[1:]):
        d = hav(a, b)
        if 0 < d < 300:
            n = int(d // step)
            for i in range(1, n):
                t = i / n
                out.append((a[0] + (b[0]-a[0])*t, a[1] + (b[1]-a[1])*t))
    return out
def on_bridge_fraction(line, bridge_pts):
    bridge_pts = densify(bridge_pts)
    # share of route length that lies within 20 m of the bridge's own geometry
    tot = on = 0
    for p, q in zip(line, line[1:]):
        seg = hav(p, q); tot += seg
        m = ((p[0]+q[0])/2, (p[1]+q[1])/2)
        if min(hav(m, b) for b in bridge_pts) < 20: on += seg
    return on, tot
res = {}
for bid, g in G.items():
    if bid in ("fremont", "marquam"): continue
    row = {}
    for label, a, b in (("eastbound", g["west_approach"], g["east_approach"]), ("westbound", g["east_approach"], g["west_approach"])):
        dist, line, names = route(a, b)
        on, tot = on_bridge_fraction(line, g["pts"])
        straight = hav(a, b)
        uses = on >= 0.5 * g["span_m"]
        detour = dist / straight
        row[label] = dict(uses_bridge=uses, on_bridge_m=round(on), route_m=round(dist), straight_m=round(straight), detour_ratio=round(detour, 2), streets=names[:6], ok=uses and detour <= 1.8)
    res[bid] = row
    for k, v in row.items():
        print(f"{bid:12s} {k:10s} {'PASS' if v['ok'] else 'FAIL'}  on-bridge {v['on_bridge_m']:4d} m of ~{g['span_m']} m, route {v['route_m']} m vs straight {v['straight_m']} m (x{v['detour_ratio']})  {' > '.join(v['streets'])[:90]}")
json.dump(res, open(os.path.join(CACHE, "bridge_validation.json"), "w"), indent=1)
