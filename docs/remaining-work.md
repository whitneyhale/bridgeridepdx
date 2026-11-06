# Remaining Work

Native Android app in three phases, one task at a time. Rewritten 2026-11-05 from the Android plan (claude.ai doc "Bridge Ride PDX: Android plan"; summary in `docs/android-plan.md`). Rules cited are in `docs/routing-rules.md`.

## How a task runs

0. One task in progress. It is done when it works on the phone, its own tests pass, and the full retest passes.
1. Tests for the logic first, then the code.
2. CI runs unit tests, data checks and lint on every push.
3. A release ships with a test card (template in `docs/qa-plan.md`).
4. Whitney rides, answers, sends the GPX; the GPX becomes a replay test.
5. Developer and UX review; findings go into the next task.

## Status

| Done before 2026-11-05 | Evidence |
|---|---|
| Routing engine on the PC, Greenway first profile | `docs/spike-results.md` |
| 14 bridge crossings, 21 of 22 validate | `tools/validate_bridges.py` |
| Name matcher 40 of 40 | `tools/match_bridge.py` |
| Web map page (PC planning), OpenFreeMap tiles | `web/index.html` |

## Phase 1: Ride it (releases 0.1 to 0.3)

Gate A: two recorded rides replay with no backward jumps; one wrong turn warned within about 15 s.

| Task | What | Old slice | Status |
|---|---|---|---|
| 1.8 + 1.1 | App shell, core and app modules, CI, release on new versionName | S0 | Done 2026-11-05 (PR #1, CI green) |
| 1.3 | Recorder: Start, Stop, locked-screen tracking, GPX export (release 0.1) | S1 | Released v0.1.0; waiting on bike test |
| 1.0 | Screen drafts v3 (list in `docs/design/screen-descriptions.md`) | | Prototypes done (Main, Plan, Ride, Ride night, Bridge, Summary); palette question open |
| 1.2 | Map with bridges, Map/Library/Settings tabs (release 0.2) | S0 | Released v0.2.0 (both Maestro flows pass on emulator); waiting on desk test |
| 1.4 | Bike marker, heading wedge, orange after 5 s stale (N-09) | S2 | |
| 1.5 | GPS gaps stay calm (P-02 to P-04) | S3 | |
| 1.6 | Follow a line, off route (N-04), line-only rejoin (N-12) | S4, S5 | |
| 1.7 | End-of-ride rating, Mark spot | | |

## Phase 2: Route it right (0.4 to 0.6)

2a gate: rides 13 and 6 end to end. 2b gate: 4-stop errand ride, first match-rate number.

| Part | Tasks |
|---|---|
| 2a | 2.1 Hawthorne westbound north side (E-07), 2.2 PC to phone, 2.13 route file v1, 2.3 bridge stop by name and next-turn banner (S7, S8), 2.10 navigator corridor, 2.4 ride log (S9), 2.5 Steel and Ross Island bugs |
| 2b | 2.9 best stop order (T-11), 2.11 quick destinations (N-14), 2.12 rider profile v1, 2.6 hosted planner, 2.7 address search, 2.8 engine head-to-head |

## Phase 3: Make it good (0.7 and up)

Gate C: 2-hour locked-screen ride, offline, no crash, under the memory target.

3.1 design system, 3.2 offline map, 3.3 voice and vibration, 3.4 memory and battery, 3.5 on-phone router spike, 3.6 pass by and lift warnings, 3.7 plain-language planning, 3.8 rider profile v2, 3.9 measure on the phone, 3.10 street art, 3.11 themed rides.

## Open questions for Whitney

- How many test rides a week can you do?
- Rerouting default, threshold, walking (routing-rules questions 12 to 14).
- Tagline; the 6 design questions in `docs/design/design-system-prompt.md`; routing-rules questions 2 to 11.
