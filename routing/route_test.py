import json, urllib.request, urllib.parse, sys
def route(profile, a, b):
    q = urllib.parse.urlencode([("profile", profile), ("point", a), ("point", b), ("points_encoded", "false"), ("instructions", "true"), ("details", "road_class")])
    d = json.load(urllib.request.urlopen("http://localhost:8989/route?" + q, timeout=60))
    p = d["paths"][0]
    names = []
    for i in p["instructions"]:
        n = i.get("street_name") or "(unnamed)"
        if not names or names[-1] != n: names.append(n)
    rc = {}
    for s, e, c in p["details"]["road_class"]:
        pts = p["points"]["coordinates"][s:e+1]
        # approximate segment length in km
        import math
        L = sum(math.hypot((pts[k+1][0]-pts[k][0])*78.5, (pts[k+1][1]-pts[k][1])*111.2) for k in range(len(pts)-1))
        rc[c] = rc.get(c, 0) + L
    tot = sum(rc.values()) or 1
    return p["distance"]/1000, p["time"]/60000, names, {k: round(100*v/tot) for k, v in sorted(rc.items(), key=lambda x: -x[1])}
tests = {
 "SE Hawthorne & 20th -> Pioneer Square": ("45.5121,-122.6451", "45.5189,-122.6794"),
 "N Williams -> Alberta Arts (NE)":     ("45.5260,-122.6670", "45.5590,-122.6420"),
}
for label, (a, b) in tests.items():
    print("==", label)
    for prof in ("bike", "greenway_first"):
        try:
            km, mn, names, rc = route(prof, a, b)
            print(f"  {prof}: {km:.2f} km, {mn:.1f} min | classes %: {rc}")
            print("    streets:", " > ".join(names[:25]))
        except Exception as e:
            print(f"  {prof}: ERROR {e}")
