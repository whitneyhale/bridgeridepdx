"""Finds the bike routes near each bridge approach point, from OpenStreetMap.

For every approach point in tools/.cache/bridge_geometry.json it lists named bike facilities within RADIUS_M:
  path   car-free path (highway=cycleway, or path/footway designated for bikes)
  route  street that belongs to a bicycle route relation (route=bicycle), for example a neighborhood greenway
  lane   street with a bike lane or track

These are OpenStreetMap tags, not the city's official greenway list (rule D-06). Use them to see what is
nearby and to compare against PBOT data later.

Usage: python tools/extract_feeders.py [path/to/Portland.osm.pbf]
Writes tools/.cache/bridge_feeders.json
"""
import osmium, math, json, os, sys, collections

HERE = os.path.dirname(os.path.abspath(__file__))
PBF = sys.argv[1] if len(sys.argv) > 1 else os.path.join(HERE, "..", "routing", "Portland.osm.pbf")
CACHE = os.path.join(HERE, ".cache")
RADIUS_M = 500
KEEP_PER_APPROACH = 10

geom = json.load(open(os.path.join(CACHE, "bridge_geometry.json")))
points = {}
for bid, g in geom.items():
    for side, pt in g["approaches"].items():
        points[(bid, side)] = tuple(pt)


def dist_and_bearing(a, b):
    ky = 110574.0
    kx = 111320.0 * math.cos(math.radians(a[0]))
    dy = (b[0] - a[0]) * ky
    dx = (b[1] - a[1]) * kx
    ang = (math.degrees(math.atan2(dx, dy)) + 360) % 360
    names = ["N", "NE", "E", "SE", "S", "SW", "W", "NW"]
    return math.hypot(dx, dy), names[int((ang + 22.5) // 45) % 8]


# Pass 1: bicycle route relations -> way ids
way_route = collections.defaultdict(set)
for o in osmium.FileProcessor(PBF, osmium.osm.RELATION):
    t = o.tags
    if t.get("route") == "bicycle":
        label = t.get("name") or t.get("ref")
        if not label:
            continue  # unnamed relation: nothing useful to show a rider
        for m in o.members:
            if m.type == "w":
                way_route[m.ref].add(label)
print("bicycle route relations: %d ways covered" % len(way_route))


def kind_of(t, wid):
    hw = t.get("highway")
    if hw == "cycleway":
        return "path"
    if hw in ("path", "footway", "pedestrian") and t.get("bicycle") in ("designated", "yes"):
        return "path"
    if wid in way_route:
        return "route"
    if any(t.get(k) in ("lane", "track", "opposite_lane") for k in
           ("cycleway", "cycleway:left", "cycleway:right", "cycleway:both")):
        return "lane"
    return None


# Pass 2: ways near approach points
found = collections.defaultdict(dict)  # (bid, side) -> {(name, kind): (dist, bearing)}
for o in osmium.FileProcessor(PBF, osmium.osm.NODE | osmium.osm.WAY).with_locations().with_filter(osmium.filter.KeyFilter("highway")):
    if not o.is_way():
        continue
    t = o.tags
    if t.get("bridge") and t.get("name", "").lower().endswith("bridge"):
        continue  # the bridges themselves
    k = kind_of(t, o.id)
    if not k:
        continue
    names = set()
    if t.get("name"):
        names.add(t["name"])
    names |= way_route.get(o.id, set())
    if not names:
        names = {"(unnamed %s)" % t.get("highway", "way")}
    pts = [(n.lat, n.lon) for n in o.nodes if n.location.valid()]
    if not pts:
        continue
    for key, p in points.items():
        best = None
        for q in pts:
            d, b = dist_and_bearing(p, q)
            if best is None or d < best[0]:
                best = (d, b)
        if best[0] <= RADIUS_M:
            for nm in names:
                cur = found[key].get((nm, k))
                if cur is None or best[0] < cur[0]:
                    found[key][(nm, k)] = best

RANK = {"path": 0, "route": 1, "lane": 2}
out = {}
for (bid, side), d in found.items():
    rows = [dict(name=nm, kind=k, distance_m=round(v[0]), direction=v[1]) for (nm, k), v in d.items()
            if not nm.startswith("(unnamed")]
    rows.sort(key=lambda r: (RANK[r["kind"]], r["distance_m"]))
    # keep a mix: all nearby paths and routes first, then lanes to fill
    out.setdefault(bid, {})[side] = rows[:KEEP_PER_APPROACH]
json.dump(out, open(os.path.join(CACHE, "bridge_feeders.json"), "w"), indent=1)
for bid in out:
    for side in out[bid]:
        print(bid, side, [(r["name"], r["kind"], r["distance_m"], r["direction"]) for r in out[bid].get(side, [])][:5])
