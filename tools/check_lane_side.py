"""Checks that a route over each bridge uses the lane on the correct side for its direction (rule E-01, keep right).

Needs the routing engine on localhost:8989 and data/bridges.json. Only crossings with a one-way lane are checked.
Usage: python tools/check_lane_side.py [heading]   (pass "heading" to also send the travel heading at both points)
"""
import json, math, os, sys, urllib.request

HERE = os.path.dirname(os.path.abspath(__file__))
B = json.load(open(os.path.join(HERE, "..", "data", "bridges.json")))["bridges"]
USE_HEADING = len(sys.argv) > 1 and sys.argv[1] == "heading"
KY = 110574.0
HEADING = {"eastbound": 90, "westbound": 270, "northbound": 0, "southbound": 180}


def route(a, b, heading=None):
    body = {"points": [[a[1], a[0]], [b[1], b[0]]], "profile": "bike", "points_encoded": False}
    if heading is not None:
        body["headings"] = [heading, heading]
        body["ch.disable"] = True
    req = urllib.request.Request("http://localhost:8989/route", json.dumps(body).encode(), {"Content-Type": "application/json"})
    return [(c[1], c[0]) for c in json.load(urllib.request.urlopen(req, timeout=60))["paths"][0]["points"]["coordinates"]]


def side_of(p, a, b, axis):
    if axis == "ew":
        t = (p[1] - a[1]) / ((b[1] - a[1]) or 1e-9)
        return None if not 0.15 < t < 0.85 else ("north" if p[0] > a[0] + t * (b[0] - a[0]) else "south")
    t = (p[0] - a[0]) / ((b[0] - a[0]) or 1e-9)
    return None if not 0.15 < t < 0.85 else ("east" if p[1] > a[1] + t * (b[1] - a[1]) else "west")


fails = 0
for br in B:
    a, b = list(br["ends"].values())
    for d, c in br["crossings"].items():
        if not c["offered"] or c["lane"]["type"] != "one-way":
            continue
        line = route(c["from"], c["to"], HEADING[d] if USE_HEADING else None)
        sides = [s for s in (side_of(p, a, b, br["axis"]) for p in line) if s]
        got = max(set(sides), key=sides.count) if sides else None
        ok = got == c["lane"]["side"]
        fails += not ok
        print(("PASS" if ok else "FAIL"), br["id"].ljust(12), d.ljust(10), "expected", c["lane"]["side"], "got", got)
print("failures:", fails)
