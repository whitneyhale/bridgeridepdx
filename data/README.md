# Data files

Reference data for Bridge Ride PDX. Rules that use this data are in [docs/routing-rules.md](../docs/routing-rules.md).

## Files

| File | Edit by hand? | What it is |
|---|---|---|
| `bridge_names.json` | Yes | Names, nicknames, misspellings, colors, years, types, what each bridge carries, bike access, county codes. |
| `bridges.json` | No (generated) | The app's bridge reference. Names above plus two GPS positions per crossing. |
| `bridge_match_cases.json` | Yes | Test cases for name matching. Add real mistakes riders make. |
| `overpasses.json` | No (generated) | Pedestrian and mixed use overpasses with the "overpass" label, preference, effort, what they cross. |
| `overpass_overrides.json` | Yes | Hand facts for individual overpasses (Blumenauer: preferred). |
| `corridors.json` | Yes | Named street corridors with ratings. Eastbank Esplanade first. Ratings are proposed, not confirmed. |

## Bridge positions

- Points are `[lat, lon]`.
- East-west bridges: eastbound goes from the west approach to the east approach. North-south bridges (Columbia, Blumenauer): northbound from the south approach.
- `category`: mixed_use, transit_and_pedestrian, pedestrian_only, car_only, freeway_with_path, pedestrian_overpass (definitions in `bridge_names.json`).
- `crossings.<direction>.lane.side`: the side to ride for that direction (keep right).
- `position`: neighbors along the river and the three nearest crossings.
- Each point sits about 60 m outside the bridge end so it snaps to a street or path.
- `crossings.<direction>.offered` is false for bridges closed to bikes (Fremont, Marquam).
- `crossings.<direction>.validated` is true when routing the crossing in the engine followed the bridge. Rule B-09.

## Regenerate bridges.json

You need the Portland map file and the routing engine. Steps are in `routing/`.

```
python tools/extract_bridges.py     # reads routing/Portland.osm.pbf, needs: pip install osmium
python tools/validate_bridges.py    # engine must be running on localhost:8989
python tools/extract_lanes.py       # which side carries which direction
python tools/extract_feeders.py     # bike routes near each bridge end
python tools/build_bridges.py       # writes data/bridges.json
python tools/extract_overpasses.py
python tools/build_overpasses.py    # writes data/overpasses.json and routing/overpass_model.json
python tools/build_bridge_doc.py    # rewrites section 9 of docs/routing-rules.md
python tools/match_bridge.py        # name matching tests
python tools/check_lane_side.py     # lane side check, engine must be running
```

The first two write to `tools/.cache/` (not committed).

## Matching names

`tools/match_bridge.py` lowercases the text, drops filler words ("the", "bridge", "crossing"), then tries an exact match on names, nicknames, and misspellings, then a color match, then a fuzzy match. More than one result means the app should ask which bridge.

## Known gaps

- Misspellings are starter guesses, not collected from real use.
- Colors come from Wikipedia photo captions, not paint specifications.
- Steel Bridge westbound and both Ross Island crossings fail validation (rules B-07, B-08).
- County bridge codes are filled in for five bridges only.

## Credit

Positions are derived from OpenStreetMap (c) OpenStreetMap contributors, ODbL.
