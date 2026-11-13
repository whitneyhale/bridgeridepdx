# Android plan (summary)

Full plan: claude.ai doc "Bridge Ride PDX: Android plan" (private to Whitney), with tabs QA and test plan, and Rider profile. This file holds the decisions; `docs/remaining-work.md` holds the task list. Updated 2026-11-05.

## Decisions (2026-11-05)

| Decision | Rule |
|---|---|
| Native Android first; browser and iOS ports later | N-11 |
| Two models: slow planner (PC, later hosted), fast navigator (phone, offline) | N-12 |
| Rerouting keeps the road the rider chose when it costs little (thresholds proposed) | N-13 |
| Home, Work, favorites one tap away, stored on the phone only | N-14 |
| Fewest turns and Ride back are styles | Y-11, Y-12 |
| The app orders stops by riding cost in the chosen style | T-11 |
| Nav bar: Map, Library, Settings | design |
| Themed rides allowed | design |
| Greenways are the main PBOT signal | data |
| One task at a time; done = works on the phone and the full retest passes | process |
| 2026-11-09: light look E, dark look C; releases by major function with test-result rules (`docs/remaining-work.md`) | design, process |
| 2026-11-09: shrunk release build is what ships; speed is measured only on it | process |

## Stack

| Piece | Choice |
|---|---|
| App | Kotlin, Jetpack Compose, target SDK 36 |
| Map | MapLibre Native, OpenFreeMap tiles (free, no key), two app styles built by `tools/build_map_styles.py` |
| GPS | Fused location in a foreground service of type location |
| Storage | On the phone; GPX export |
| Planner | GraphHopper 11.1 on the PC (hybrid mode for speed); hosted later |
| On-phone router | Spike in Phase 3: BRouter vs GraphHopper |
| Builds | GitHub Actions, signed APK attached to a GitHub Release per tag |

## Modules

| Module | Holds | Tested by |
|---|---|---|
| `android/core` | Pure Kotlin logic: GPX, gaps, off route, matcher, stop order, profile | JUnit on the computer |
| `android/app` | Screens, location service, storage | Maestro flows, bike tests |
| `tools/`, `routing/` | Python data tools, engine config | pytest, validators |

## Signing

Pre-alpha builds use a test key kept in the repo (`android/keystore/test.jks`, passwords in `android/keystore/README.md`) so every build installs over the last one. Never use it for the Play Store; a private key goes in GitHub secrets before anyone else installs the app.
