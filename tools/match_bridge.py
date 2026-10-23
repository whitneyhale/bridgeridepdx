"""Matches what a rider types or says to a bridge. Reference implementation; the app ports this logic.

Order: exact alias match, then color match, then fuzzy match on names, aliases, and misspellings.
Returns a list of candidates. More than one candidate means the app should ask which bridge.
"""
import json, os, re, difflib

HERE = os.path.dirname(os.path.abspath(__file__))
STOP = {"the", "bridge", "bridges", "crossing", "br", "brdg", "brg", "over", "river", "willamette"}
FUZZY_MIN = 0.78
COLOR_WORDS = {"red", "green", "pale", "black", "gray", "grey", "brown", "blue"}


def norm(text):
    words = re.sub(r"[^a-z0-9 ]", " ", text.lower().replace("'", "")).split()
    return " ".join(w for w in words if w not in STOP)


def load():
    return json.load(open(os.path.join(HERE, "..", "data", "bridges.json")))["bridges"]


def match(query, bridges=None):
    bridges = bridges or load()
    q = norm(query)
    if not q:
        return []
    # 1. exact alias, misspelling, or official name
    exact = [b["id"] for b in bridges
             if q in {norm(x) for x in b["aliases"] + b["misspellings"] + [b["name"]]}]
    if exact:
        return exact
    # 2. color ("the red bridge")
    words = set(q.split())
    if words and words <= COLOR_WORDS:
        colored = [b["id"] for b in bridges
                   if words & {w for c in b["colors"] for w in c.split()}]
        if colored:
            return colored
    # 3. fuzzy, best score per bridge
    scores = {}
    for b in bridges:
        for x in b["aliases"] + b["misspellings"] + [b["name"]]:
            s = difflib.SequenceMatcher(None, q, norm(x)).ratio()
            scores[b["id"]] = max(scores.get(b["id"], 0), s)
    ranked = sorted(((s, i) for i, s in scores.items() if s >= FUZZY_MIN), reverse=True)
    if not ranked:
        return []
    best = ranked[0][0]
    return [i for s, i in ranked if best - s < 0.04]


if __name__ == "__main__":
    cases = json.load(open(os.path.join(HERE, "..", "data", "bridge_match_cases.json")))["cases"]
    bridges = load()
    failed = 0
    for c in cases:
        got = sorted(match(c["query"], bridges))
        want = sorted(c["expect"])
        ok = got == want
        failed += not ok
        print(("PASS" if ok else "FAIL"), f"{c['query']!r:32} -> {got}" + ("" if ok else f"  (wanted {want})"))
    print(f"\n{len(cases) - failed} of {len(cases)} passed")
    raise SystemExit(1 if failed else 0)
