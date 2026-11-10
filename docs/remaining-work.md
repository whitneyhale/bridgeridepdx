# Remaining Work

Native Android app, released one major function at a time. Updated 2026-11-09 (releases and test-result rules); rewritten 2026-11-05 from the Android plan (claude.ai doc "Bridge Ride PDX: Android plan"; summary in `docs/android-plan.md`). Rules cited are in `docs/routing-rules.md`.

## How a task runs

0. One task in progress. It is done when it works on the phone, its own tests pass, and the full retest passes.
1. Tests for the logic first, then the code.
2. CI runs unit tests, data checks and lint on every push.
3. A release ships with a test card (template in `docs/qa-plan.md`).
4. Whitney rides, answers, sends the GPX; the GPX becomes a replay test.
5. Developer and UX review; findings go into the next task.

## Status

| Part | State |
|---|---|
| Routing engine on the PC, Greenway first profile | Works (`docs/spike-results.md`) |
| 14 bridge crossings; name matcher | 21 of 22 validate; 40 of 40 |
| 0.1 recorder, GPX export | Released; bike test folded into the 0.2.1 card |
| 0.2 map with bridge ends, tabs | Released; desk test folded into the 0.2.1 card |
| 0.2.1 E and C looks, shrunk release build | In CI |

## Decisions 2026-11-09

- Light look is E (Portland Explorer), dark look is C (Modern Urban Cyclist), from the "Five Design Directions" canvas. Colors: `docs/design/colors-e.json`, `colors-c.json`.
- Palette fixes: bridge art never in the route color (C bridges are white); the route is solid with an outline and greenways are thin and dotted; warnings always carry the warning icon. E hazard darkened from #D9480F to #D44304 so white text reaches 4.5:1.
- Route color as drawn: teal by day, green by night.
- Releases by major function. Ride basics (0.3) before the offline map and on-phone routing (0.4, moved up from Phase 3).
- Not taken unless Whitney says so: cloud backup or telemetry sync, store-rating targets, Espresso (Maestro stays).

## Releases

Each release adds one major function. Claude's automated tests run first; it ships only if they pass. Then Whitney tests it.

| Release | Major function | Claude's tests | Whitney's test |
|---|---|---|---|
| 0.2.1 | E and C looks; shrunk release build | Unit tests, lint, palette check, Maestro on the release build with screenshots of both looks | Desk: both looks readable outside; open bike test; speed check with the phone plugged in |
| 0.3 | Ride basics: bike marker (1.4), GPS gaps (1.5), follow a line and off-route warning (1.6) | Unit tests; replay of recorded rides with gaps cut out; emulator ride with simulated GPS | Bike: one loop over a bridge and under the Esplanade decks; one wrong turn on purpose (Gate A) |
| 0.4 | Offline Portland map; routes planned on the phone | Unit tests with coverage; 30-mile route under 1.5 s; emulator run in airplane mode | Plan a known ride in airplane mode; compare it to how you would ride it |
| 0.5 | Bridge stops by name, turn banner, bridge art in the navigation view (2.1, 2.3, 2.5) | Rides 13 and 6 end to end; lane-side checks | Bike: ride 13 with the banner |
| 0.6 | Ride log (2.4), bridge passport, diagnostic export, end-of-ride rating (1.7) | Unit tests; emulator crossing stamps the passport | Cross a bridge; send the diagnostic file |
| 0.7 | Stop order (2.9), quick destinations (2.11), rider profile (2.12), address search (2.7) | Stop-order tests; first match-rate number | 4-stop errand ride |
| 0.8 | Voice, battery, themed rides, street art (3.3, 3.4, 3.10, 3.11) | 4-hour simulated ride; memory check | 2-hour locked-screen ride, offline (Gate C) |

## What happens after a test result

| Result | What Claude does |
|---|---|
| Claude's tests fail | Fix, add a test for the bug, rerun. Nothing ships until they pass. |
| Whitney passes it | Start the next release. |
| Whitney fails a step | Fix in a point release (0.3.1). Her GPX or screenshot becomes an automated test. She retests only the failed step. |
| The same step fails twice | Stop. Write up the cause and the options; Whitney chooses. |
| No result yet | Build the next release, but never more than one release ahead of her last result. |

## Not in 0.2.1 (moved)

- E sticker icons and the 14 bridge icons as Android vector files: with the screens that show them (bike marker 0.3, bridge art 0.5, passport 0.6).
- App-specific baseline profile: after the first speed numbers from the phone.
- PBOT greenways on the map: the map shows OpenStreetMap bike paths dotted; real greenway data needs the license check.

## Open questions for Whitney

- How many test rides a week can you do?
- What should the app "deal with"? (message of 2026-11-09 cut off; assumed GPS drops and weak signal)
- Rerouting default, threshold, walking (routing-rules questions 12 to 14).
- Tagline; the 6 design questions in `docs/design/design-system-prompt.md`; routing-rules questions 2 to 11.
