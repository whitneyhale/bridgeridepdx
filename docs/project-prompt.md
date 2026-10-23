# Prompt: Bridge Ride PDX project briefing

Paste this into a new chat or give it to a developer before they touch the repo `whitneyhale/bridgeridepdx`. Updated 2026-10-23.

---

You are working on Bridge Ride PDX for Whitney Hale: a free cycling route planner for Portland built around the river bridges. Read this whole file before acting.

## How to work

1. Do not guess. If the repo does not state it, ask Whitney. Use web search for facts about the world and cite the page.
2. Keep files short. Lead with the fact. No intros, no emojis, no checkboxes.
3. Work in small slices from `docs/remaining-work.md`. One slice, one bike test, then stop.
4. Never put Whitney's home address or coordinates near home in the repo. Rides start at "Home".
5. Rule changes go through `docs/routing-rules.md`: change the rule row, add a changelog entry, bump the version, same commit.
6. Design decisions are in `docs/design/design-system-prompt.md` ("Already decided"). Do not change them; ask.

## What it is for

Plan a ride like a Portland rider thinks ("Hawthorne out, Steel back"), on greenways and car-free paths, then ride it with the phone on the handlebars, and keep a log.

## What is unique

- A bridge is a directional crossing with GPS points on both sides and the lane side to ride, not a pin.
- Bridge names work as people say them: nicknames, misspellings, colors ("red bridge" is the Broadway).
- Pass by stops: slotted in where they add the least distance, skipped with a note if they cost too much.
- Overpasses are labeled and low preference because they are tiring; the Blumenauer Bridge is preferred.
- Each bridge knows its category (who shares it), transit, neighbors on the river, and nearby bike routes.
- Free, no account, location history stays on the phone.

## Status

| Part | State | Evidence |
|---|---|---|
| Routing engine (GraphHopper 11.1, Portland map) | Works on Linux; Windows script untested | `docs/spike-results.md` |
| Greenway first profile | Works; ranks west-side riverfront paths too low | ride 6 result |
| Bridge data, 14 crossings | Done | 21 of 22 offered crossings validate |
| Name matcher | Done | 40 of 40 cases |
| Overpasses | Done, weights untested on real rides | `data/overpasses.json` |
| Bridge descriptions | 3 of 14 written | `content/bridges/` |
| Web map page | Shows bridges and GPS | headless Chrome test, 28 points |
| Recording, following, prompts, saving, ride log | Not built | `docs/remaining-work.md` |
| Design system | Not built; prompt ready | `docs/design/design-system-prompt.md` |
| Screen drafts | 8 drafts, not reviewed by Whitney | `docs/design/` |

## Known bugs

1. Hawthorne westbound routes use the south side; westbound bikes belong on the north side. OpenStreetMap does not tag the north side for bikes midspan (E-07).
2. Steel Bridge westbound detours 2.6 times the straight distance; the east approach point sits on N Oregon St (B-08).
3. Ross Island Bridge: the engine never routes over it; not offered (B-07).
4. Lane check cannot measure Broadway eastbound or St. Johns westbound: the route has too few points on the deck (`tools/check_lane_side.py`).
5. Waypoints on one side of the river do not force a bridge; Bridge stops are required (ride 13).
6. The engine leaves the Steel Bridge unnamed in directions.
7. Color-blind checks fail for 5 decided or proposed pairs, worst is route blue vs greenway green (`docs/design/palette-report.md`).
8. Screen drafts: the Plan "add stop" row is cut off by the bottom bar; the Settings Privacy row is hidden under the nav bar.

## Known gotchas

- Phone GPS in a browser needs HTTPS (localhost is exempt). Free GitHub Pages needs a public repo.
- Serve the web page from the repo root; it loads `../data/bridges.json`.
- Test in Chrome on Android. Whitney's everyday browser (DuckDuckGo) is untested.
- Samsung battery settings can stop GPS; see the checklist in `docs/app-plan.md`.
- The browser screen wake lock is released when the app is minimized.
- Android `getLastLocation()` can be old or empty; use a fresh fix.
- OpenStreetMap tags freeway bridge decks `bicycle=no`; the I-5 and I-205 bike paths are separate ways, listed by ID in `tools/extract_bridges.py`.
- `tools/.cache/` is not committed. Rebuilding data needs `routing/Portland.osm.pbf` and the engine running.
- Elevation (Flat style, climb budget) is untested.
- Bridge colors marked unverified come from photo captions.
- Metro Bike There and PBOT data licenses are unknown; use OpenStreetMap only.
- No feed exists yet for bridge lifts or closures.

## Needs more description (ask Whitney)

- The open questions at the end of `docs/routing-rules.md` and in `docs/design/design-system-prompt.md`.
- Esplanade ratings and whether other east-side spines exist.
- What counts as the "most common" bike routes near a rider.
- C-TRAN routes on the Columbia bridges.
- Dark mode colors (`palette.json` is not in the repo).

## Data

| File | Holds | Edit |
|---|---|---|
| `data/bridge_names.json` | Names, aliases, misspellings, colors, category, modes, transit, streets, ends, notes | by hand |
| `data/bridges.json` | Everything above plus axis, ends, approach points, crossings with lane side and validation, position, nearby bike routes | generated |
| `data/overpasses.json` | 71 overpasses: label, category, crosses, effort, preference, weight | generated |
| `data/overpass_overrides.json` | Hand facts (Blumenauer preferred) | by hand |
| `data/corridors.json` | Eastbank Esplanade ways and proposed ratings | by hand |
| `data/descriptions.json` | Compiled bridge descriptions from `content/bridges/*.md` | generated |
| `data/geometry.json` | What each bridge looks like, for icons and illustrations | by hand |
| `routing/greenway_first.json`, `routing/overpass_model.json` | Engine custom models | by hand, generated |
| `docs/design/colors-decided.json` | Wayfinding colors to check | by hand |

Shape of one crossing in `bridges.json`:

```json
"westbound": {"offered": true, "from": [45.512154, -122.66062], "to": [45.514213, -122.675355],
  "validated": true, "lane": {"side": "north", "type": "one-way", "osm_ways": [30260114, 30260211, "..."]}}
```

Planned app records (not built): Route (name, notes, tags, stops in order with type, style and settings, saved path, versions), Stop (type, point, direction, pass distance, detour budget), Ride log entry (name, date, distance, notes, tags, route link, GPX), Recording (fixes with time, lat, lon, accuracy, speed, heading).

## Inputs and outputs

| Piece | Input | Output |
|---|---|---|
| Data tools (`tools/*.py`) | Portland OpenStreetMap file, engine at localhost:8989, hand files | JSON in `tools/.cache/` and `data/` |
| Routing engine | Points, profile, custom model, headings | Path, distance, time, instructions |
| Name matcher | Typed or spoken bridge name | One bridge id, several (ask), or none |
| Phone app (planned) | GPS fixes, taps, search text, GPX files | Map, turn prompts, warnings, GPX export, ride log |
| Checks | Data files, engine | PASS or FAIL lines, `docs/design/palette-report.md` |

## Read next

`README.md`, `docs/requirements.md`, `docs/remaining-work.md`, `docs/routing-rules.md`, `data/README.md`.
