"""Builds the two app map styles from OpenFreeMap's Liberty style.

  E (light, Portland Explorer): cream land, white streets, tan arterials, sticker ink labels.
  C (dark, Modern Urban Cyclist): asphalt ground, dark street grid, no fills but water.

Reads docs/design/map-style-source.json (a saved copy of
https://tiles.openfreemap.org/styles/liberty; refresh it with --fetch) and writes
android/app/src/main/assets/style_e.json and style_c.json. Both are committed, so the
app and CI never need this tool at build time.

Rules kept here, not in the app:
  - Points of interest, shields, 3D buildings and the shaded-relief raster are dropped: less clutter.
  - Bike paths (OpenStreetMap cycleways) are thin and dotted in the greenway color.
    The route line (drawn by the app) is solid with an outline, so the two differ by shape,
    not only by color (palette fix 1, 2026-11-09).
  - Nothing in the base map uses the route color.

Usage: python tools/build_map_styles.py [--fetch]
"""
import copy, json, os, sys, urllib.request

HERE = os.path.dirname(os.path.abspath(__file__))
SOURCE = os.path.join(HERE, "..", "docs", "design", "map-style-source.json")
ASSETS = os.path.join(HERE, "..", "android", "app", "src", "main", "assets")
LIBERTY_URL = "https://tiles.openfreemap.org/styles/liberty"

LOOKS = {
    "e": {
        "name": "Bridge Ride E (light)",
        "route": "#0E7C74",
        "land": "#FFF0D6", "block": "#FBE3BC", "park": "#CDE8BF", "water": "#9FD3D6",
        "road": {"motorway": "#F2CC8F", "major": "#F7D9A8", "mid": "#FFFFFF", "minor": "#FFFFFF", "path": "#EBD3A6", "rail": "#C9B58F"},
        "casing": {"motorway": "#D9B070", "major": "#E3C084", "mid": "#E9D2A6", "minor": "#F0DDB8", "path": "#F0DDB8"},
        "bike": "#3F7D3A",
        "boundary": "#C9B58F",
        "text": {"road": "#3A4767", "place": "#1D2B4F", "water": "#2B6E77"}, "halo": "#FFF5E1",
        "arrow_opacity": 0.5,
    },
    "c": {
        "name": "Bridge Ride C (dark)",
        "route": "#22C55E",
        "land": "#0B0B0C", "block": "#101012", "park": "#101712", "water": "#1B3D4F",
        "road": {"motorway": "#4A4A52", "major": "#3A3A40", "mid": "#2C2C30", "minor": "#1F1F23", "path": "#26262A", "rail": "#2C2C30"},
        "casing": {"motorway": "#000000", "major": "#000000", "mid": "#000000", "minor": "#000000", "path": "#000000"},
        "bike": "#3DD6F5",
        "boundary": "#3A3A40",
        "text": {"road": "#9A9AA0", "place": "#F5F5F5", "water": "#6FA3B8"}, "halo": "#0B0B0C",
        "arrow_opacity": 0.35,
    },
}

DROP_IDS = {"natural_earth", "park_outline", "landcover_ice", "landcover_wetland", "landcover_sand",
            "landuse_residential", "landuse_hospital", "landuse_school", "road_area_pattern", "building-3d",
            "airport", "boundary_disputed"}
DROP_PREFIXES = ("poi_", "highway-shield", "road_shield")
PARK_IDS = {"park", "landcover_wood", "landcover_grass", "landuse_pitch", "landuse_track", "landuse_cemetery"}


def road_kind(layer_id):
    if "rail" in layer_id:
        return "rail"
    if "path_pedestrian" in layer_id:
        return "path"
    if "motorway" in layer_id:
        return "motorway"
    if "trunk_primary" in layer_id:
        return "major"
    if "secondary_tertiary" in layer_id:
        return "mid"
    return "minor"  # minor, street, link, service_track


def bike_layer(layer_id, bridge, look):
    """Cycleways drawn thin and dotted in the greenway color, above the streets."""
    brunnel = ["==", ["get", "brunnel"], "bridge"] if bridge else ["match", ["get", "brunnel"], ["bridge", "tunnel"], False, True]
    return {
        "id": layer_id, "type": "line", "source": "openmaptiles", "source-layer": "transportation", "minzoom": 12,
        "filter": ["all", brunnel, ["==", ["get", "class"], "path"],
                   ["any", ["==", ["get", "subclass"], "cycleway"], ["==", ["get", "bicycle"], "designated"]]],
        "layout": {"line-cap": "round", "line-join": "round"},
        "paint": {"line-color": look["bike"], "line-dasharray": [0.1, 2.2],
                  "line-width": ["interpolate", ["exponential", 1.2], ["zoom"], 12, 1.4, 16, 3, 20, 6]},
    }


def build(source, look):
    out = {k: copy.deepcopy(v) for k, v in source.items() if k != "layers"}
    out["name"] = look["name"]
    out["sources"] = {"openmaptiles": copy.deepcopy(source["sources"]["openmaptiles"])}
    out["metadata"] = {"bridgeride:generated-by": "tools/build_map_styles.py",
                       "bridgeride:based-on": "OpenFreeMap Liberty (fork of OSM Liberty)"}
    layers = []
    for src in source["layers"]:
        lid = src["id"]
        if lid in DROP_IDS or lid.startswith(DROP_PREFIXES) or lid.endswith("_hatching"):
            continue
        l = copy.deepcopy(src)
        paint = l.setdefault("paint", {})
        t, sl = l["type"], l.get("source-layer", "")
        if t == "background":
            paint["background-color"] = look["land"]
        elif lid in PARK_IDS:
            l["paint"] = {"fill-color": look["park"]}
        elif sl in ("water",):
            l["paint"] = {"fill-color": look["water"]}
        elif sl == "waterway" and t == "line":
            paint["line-color"] = look["water"]
        elif sl == "aeroway":
            if t == "fill":
                l["paint"] = {"fill-color": look["block"]}
            else:
                paint["line-color"] = look["road"]["minor"]
        elif sl == "building":
            l["paint"] = {"fill-color": look["block"]}
        elif sl == "boundary":
            paint["line-color"] = look["boundary"]
        elif sl == "transportation" and t == "line":
            kind = road_kind(lid)
            casing = lid.endswith("_casing")
            if kind == "rail":
                paint["line-color"] = look["road"]["rail"]
            else:
                paint["line-color"] = (look["casing"] if casing else look["road"])[kind]
        elif sl == "transportation" and t == "symbol":  # one-way arrows
            paint["icon-opacity"] = look["arrow_opacity"]
        elif t == "symbol":
            role = "water" if sl in ("waterway", "water_name") else "place" if sl == "place" else "road"
            paint["text-color"] = look["text"][role]
            paint["text-halo-color"] = look["halo"]
            paint["text-halo-width"] = 1.4
            paint.pop("text-halo-blur", None)
            if sl == "place":  # no city dots from the sprite
                l.get("layout", {}).pop("icon-image", None)
        else:
            raise SystemExit(f"unhandled layer {lid} ({t}, {sl}): add a rule")
        layers.append(l)
        if lid == "road_motorway":
            layers.append(bike_layer("bike_path", False, look))
        if lid == "bridge_motorway":
            layers.append(bike_layer("bike_path_bridge", True, look))
    out["layers"] = layers
    used = json.dumps(layers).upper()
    if look["route"].upper() in used:
        raise SystemExit(f"{look['name']}: the base map uses the route color {look['route']}")
    return out


def main():
    if "--fetch" in sys.argv:
        with urllib.request.urlopen(LIBERTY_URL, timeout=60) as r:
            data = json.load(r)
        with open(SOURCE, "w") as f:
            json.dump(data, f, indent=1, sort_keys=True)
            f.write("\n")
    source = json.load(open(SOURCE))
    os.makedirs(ASSETS, exist_ok=True)
    for key, look in LOOKS.items():
        style = build(source, look)
        path = os.path.join(ASSETS, f"style_{key}.json")
        with open(path, "w") as f:
            json.dump(style, f, separators=(",", ":"), sort_keys=True)
            f.write("\n")
        print(f"style_{key}.json: {len(style['layers'])} layers, {os.path.getsize(path)} bytes")


if __name__ == "__main__":
    main()
