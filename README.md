# Bridge Ride PDX

A free cycling route planner for Portland, built around the river bridges: greenways first, bridges as directional crossings, custom routes, GPS riding, and a ride log. Owner: Whitney Hale.

## Status (2026-10-23)

- Built and checked: routing engine on a Portland map, bridge data for 14 crossings, name matching, overpasses, rules, screen drafts, a web map that shows bridges and GPS.
- Not built: recording, route following, turn prompts, saving, ride log.
- Next: slice S1, record a ride only, tested on the bike. See `docs/remaining-work.md`.

## Start here

| File | What |
|---|---|
| `docs/project-prompt.md` | Briefing for any AI or developer: status, unique parts, bugs, gotchas, data, inputs and outputs |
| `docs/requirements.md` | All requirements on one page |
| `docs/remaining-work.md` | Bike-testable slices, in order |
| `docs/routing-rules.md` | Every routing rule, with change log and one section per bridge |
| `docs/ux-spec.md` | How the app looks and behaves (Wayfinding look) |
| `docs/design/` | Screen drafts, design-system prompt, palette check |
| `docs/test-rides.md` | Whitney's test rides |
| `docs/prototype-spec.md`, `docs/app-plan.md` | PC prototype and Android plan detail |
| `docs/spike-results.md` | Routing engine test results |

## Folders

- `data/` - bridges, overpasses, corridors, bridge geometry, descriptions. See `data/README.md`.
- `content/bridges/` - readable bridge descriptions, one Markdown file per bridge.
- `routing/` - GraphHopper setup. On Windows, double-click `routing/start.bat`, then open http://localhost:8989/maps/.
- `tools/` - Python scripts that build and check the data. Each file says how to run it.
- `web/` - the map page.

## Run the map page

From the repo root:

```
python3 -m http.server 8000
```

Open http://localhost:8000/web/. Phone GPS needs HTTPS, so phone tests use GitHub Pages: https://whitneyhale.github.io/bridgeridepdx/web/ (once the repo is public and Pages is on). Use Chrome on the phone.

## Checks

```
python tools/match_bridge.py      # bridge names, 40 cases
python tools/check_palette.py     # Wayfinding colors, contrast and color blindness
python tools/validate_bridges.py  # bridge crossings, engine must be running
python tools/check_lane_side.py   # lane side per direction, engine must be running
```

Map data (c) OpenStreetMap contributors, ODbL.
