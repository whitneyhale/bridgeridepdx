"""Compiles content/bridges/*.md into data/descriptions.json for the app.

Each file has a front matter block (between --- lines) and a Markdown body with ## sections.
Checks: id must exist in data/bridges.json, summary 140 characters or fewer, at least one source, status draft or reviewed.
Usage: python tools/build_descriptions.py
"""
import json, os, re, glob, sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.join(HERE, "..")
ids = {b["id"] for b in json.load(open(os.path.join(ROOT, "data", "bridges.json")))["bridges"]}
REQUIRED = ("id", "title", "summary", "status", "author", "last_reviewed", "sources")


def front_matter(text):
    m = re.match(r"^---\n(.*?)\n---\n(.*)$", text, re.S)
    if not m:
        raise ValueError("missing front matter")
    meta, key = {}, None
    for line in m.group(1).splitlines():
        line = re.sub(r"\s+#.*$", "", line)
        if not line.strip():
            continue
        if line.startswith("  - ") and key:
            meta.setdefault(key, []).append(line[4:].strip())
        else:
            key, _, val = line.partition(":")
            key, val = key.strip(), val.strip()
            meta[key] = val if val else []
    return meta, m.group(2).strip()


def sections(body):
    out, cur = {}, None
    for line in body.splitlines():
        h = re.match(r"^## (.+)$", line)
        if h:
            cur = h.group(1).strip()
            out[cur] = []
        elif cur:
            out[cur].append(line)
    return {k: "\n".join(v).strip() for k, v in out.items()}


errors, items = [], {}
for path in sorted(glob.glob(os.path.join(ROOT, "content", "bridges", "*.md"))):
    name = os.path.basename(path)
    if name.startswith("_"):
        continue
    meta, body = front_matter(open(path).read())
    for k in REQUIRED:
        if not meta.get(k):
            errors.append(f"{name}: missing {k}")
    if meta.get("id") not in ids:
        errors.append(f"{name}: id {meta.get('id')!r} is not in data/bridges.json")
    if len(meta.get("summary", "")) > 140:
        errors.append(f"{name}: summary is {len(meta['summary'])} characters (max 140)")
    if meta.get("status") not in ("draft", "reviewed"):
        errors.append(f"{name}: status must be draft or reviewed")
    items[meta.get("id")] = {k: meta.get(k) for k in REQUIRED if k != "id"} | {"sections": sections(body), "markdown": body}

if errors:
    print("\n".join(errors))
    sys.exit(1)
json.dump({"generated_from": "content/bridges/*.md", "descriptions": items},
          open(os.path.join(ROOT, "data", "descriptions.json"), "w"), indent=1)
print(f"wrote data/descriptions.json: {len(items)} descriptions; missing for: {', '.join(sorted(ids - set(items)))}")
