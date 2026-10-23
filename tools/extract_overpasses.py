"""Finds pedestrian and cyclist overpasses in OpenStreetMap: foot, path, and cycle bridges that cross a
railway, MAX line, streetcar, freeway, or major road. Bridges over water are not included here.

Usage: python tools/extract_overpasses.py [path/to/Portland.osm.pbf]
Writes tools/.cache/overpasses_raw.json. data/overpasses.json is built from it by tools/build_overpasses.py.
"""
import osmium, math, json, os, sys, collections

HERE = os.path.dirname(os.path.abspath(__file__))
PBF = sys.argv[1] if len(sys.argv) > 1 else os.path.join(HERE, "..", "routing", "Portland.osm.pbf")
CACHE = os.path.join(HERE, ".cache"); os.makedirs(CACHE, exist_ok=True)
# Portland city area (the extract also covers Hillsboro, Vancouver, Gresham, and others)
S, N, W, E = 45.43, 45.66, -122.80, -122.47
BIKE_OK = {"yes", "designated", "permissive", "dismount"}
OBSTACLE_HIGHWAY = {"motorway", "motorway_link", "trunk", "trunk_link", "primary", "primary_link", "secondary", "tertiary"}
OBSTACLE_RAIL = {"rail": "railroad", "light_rail": "MAX light rail", "tram": "streetcar or tram", "narrow_gauge": "railroad"}
KY = 110574.0
KX = 111320.0 * math.cos(math.radians(45.52))
GRID = 0.001  # degrees, about 100 m


def inside(lat, lon):
    return S <= lat <= N and W <= lon <= E


def seg_cross(p1, p2, p3, p4):
    def ccw(a, b, c):
        return (c[0] - a[0]) * (b[1] - a[1]) - (b[0] - a[0]) * (c[1] - a[1])
    d1, d2 = ccw(p3, p4, p1), ccw(p3, p4, p2)
    d3, d4 = ccw(p1, p2, p3), ccw(p1, p2, p4)
    return (d1 * d2 < 0) and (d3 * d4 < 0)


def length_m(pts):
    return sum(math.hypot((b[0]-a[0])*KY, (b[1]-a[1])*KX) for a, b in zip(pts, pts[1:]))


def layer_of(t):
    try:
        return int(t.get("layer", "0"))
    except ValueError:
        return 0


candidates, obstacles = [], []
step_nodes = set()
for o in osmium.FileProcessor(PBF, osmium.osm.NODE | osmium.osm.WAY).with_locations():
    if not o.is_way():
        continue
    t = o.tags
    hw, rw = t.get("highway"), t.get("railway")
    pts = [(n.lat, n.lon) for n in o.nodes if n.location.valid()]
    if len(pts) < 2 or not inside(*pts[0]):
        continue
    ids = [n.ref for n in o.nodes]
    if hw == "steps":
        step_nodes.update(ids)
    is_foot_bridge = (hw in ("footway", "path", "cycleway", "pedestrian") and t.get("bridge") not in (None, "no")
                      and t.get("bicycle") != "no")
    if is_foot_bridge and (hw == "cycleway" or t.get("bicycle") in BIKE_OK):
        candidates.append(dict(id=o.id, tags=dict(t), pts=pts, ids=ids))
    if rw in OBSTACLE_RAIL and t.get("service") not in ("yard", "siding", "spur") and not t.get("tunnel"):
        obstacles.append(dict(kind=OBSTACLE_RAIL[rw], name=t.get("name") or t.get("operator") or "", layer=layer_of(t), pts=pts, ids=ids, bridge=bool(t.get("bridge"))))
    elif hw in OBSTACLE_HIGHWAY and not t.get("tunnel"):
        obstacles.append(dict(kind="freeway" if hw.startswith(("motorway", "trunk")) else "major road", name=t.get("name") or t.get("ref") or "", layer=layer_of(t), pts=pts, ids=ids, bridge=bool(t.get("bridge"))))

grid = collections.defaultdict(list)
for i, ob in enumerate(obstacles):
    cells = {(int(p[0] / GRID), int(p[1] / GRID)) for p in ob["pts"]}
    # also cover cells between far-apart points
    for a, b in zip(ob["pts"], ob["pts"][1:]):
        n = int(max(abs(b[0]-a[0]), abs(b[1]-a[1])) / GRID) + 1
        for k in range(n + 1):
            cells.add((int((a[0] + (b[0]-a[0])*k/n) / GRID), int((a[1] + (b[1]-a[1])*k/n) / GRID)))
    for c in cells:
        grid[c].append(i)

found = []
for c in candidates:
    lay = layer_of(c["tags"]) or 1
    hit = {}
    cells = set()
    for a, b in zip(c["pts"], c["pts"][1:]):
        n = int(max(abs(b[0]-a[0]), abs(b[1]-a[1])) / GRID) + 1
        for k in range(n + 1):
            cells.add((int((a[0] + (b[0]-a[0])*k/n) / GRID), int((a[1] + (b[1]-a[1])*k/n) / GRID)))
    for cell in cells:
        for i in grid.get(cell, []):
            ob = obstacles[i]
            if ob["layer"] >= lay or set(ob["ids"]) & set(c["ids"]):
                continue
            if ob["bridge"] and ob["layer"] >= lay:
                continue
            for a, b in zip(c["pts"], c["pts"][1:]):
                if any(seg_cross(a, b, p, q) for p, q in zip(ob["pts"], ob["pts"][1:])):
                    hit[(ob["kind"], ob["name"])] = hit.get((ob["kind"], ob["name"]), 0) + 1
                    break
    if hit:
        t = c["tags"]
        found.append(dict(
            osm_way=c["id"], name=t.get("name"), highway=t.get("highway"), bicycle=t.get("bicycle"), layer=t.get("layer"),
            start=[round(c["pts"][0][0], 6), round(c["pts"][0][1], 6)], end=[round(c["pts"][-1][0], 6), round(c["pts"][-1][1], 6)],
            length_m=round(length_m(c["pts"])), crosses=[dict(kind=k, name=n) for (k, n) in sorted(hit)],
            steps_attached=bool(set(c["ids"]) & step_nodes), pts=[[round(p[0], 6), round(p[1], 6)] for p in c["pts"]]))

json.dump(found, open(os.path.join(CACHE, "overpasses_raw.json"), "w"), indent=1)
print(len(candidates), "bike-usable foot/path bridges in Portland,", len(found), "cross a railway, freeway, or major road")
for f in sorted(found, key=lambda r: -r["start"][0]):
    print(f["osm_way"], (f["name"] or "(unnamed)")[:42].ljust(42), f["highway"], f["bicycle"], f["length_m"], "m",
          "steps" if f["steps_attached"] else "", "|", ", ".join(c["kind"] + (" " + c["name"] if c["name"] else "") for c in f["crosses"]), f["start"])
