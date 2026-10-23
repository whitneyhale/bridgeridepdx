# Remaining Work

Small slices Whitney can test on her bike, one at a time, to avoid drift. Each slice ends in a working app and a pass or fail ride. Updated 2026-10-23. Planned with the planning-and-task-breakdown method: vertical slices, acceptance checks, checkpoints.

## Where things stand

Done and checked on 2026-10-23:

- Routing engine runs on a Portland map; Greenway first profile works (`docs/spike-results.md`).
- Bridge data: 14 crossings with GPS points both sides, lane side, position, category, transit. 21 of 22 offered crossings route correctly (`tools/validate_bridges.py`).
- Bridge name matching: 40 of 40 test cases (`tools/match_bridge.py`).
- Overpasses: 71 found, labeled, low preference; Blumenauer preferred (`data/overpasses.json`).
- Rules document 0.3.0 (`docs/routing-rules.md`), screen drafts (`docs/design/`), design-system prompt.
- Web map page (`web/index.html`) loads in a browser, draws 28 bridge points, and locks GPS (headless Chrome test).

Not built yet: everything a rider touches. No recording, no route following, no turn prompts, no saving.

## Order

```
S0 hosting ─ S1 record ─ S2 see me ─ S3 GPS gaps
                 │
                 └─ S4 follow a line ─ S5 off route ─ S8 turn prompts
S6 plan on PC ─ S4
S6 ─ S7 bridge stop
S1 + S4 ─ S9 ride log
```

## Slices

### S0. Phone can open the test page
- What: repo public, GitHub Pages on, test page at `https://whitneyhale.github.io/bridgeridepdx/web/`.
- Pass: page opens in Chrome on the S24 Ultra and asks for location.
- Depends on: nothing. Size: XS (settings only).

### S1. Record a ride only (first bike test)
- What: Start and Stop buttons. Saves every GPS fix (time, position, accuracy, speed, heading) on the phone. Keeps the screen on while recording. Exports a GPX file.
- Bike test: 20 minutes with the screen on, then 10 minutes with the screen locked.
- Pass: the GPX opens in a GPX viewer and the track follows the streets you rode. Gaps over 10 seconds while the screen was on: none. Gaps while locked: record how long. This answers the native-app question (N-08).
- Depends on: S0. Size: S (`web/record.html`, one script).

### S2. See myself on the map
- What: bike marker with a heading wedge and an accuracy circle. Turns orange when the fix is older than 5 seconds.
- Bike test: ride two blocks, stop, turn around.
- Pass: marker stays on your street, wedge points the way you ride, turns orange when you stand under a bridge deck.
- Depends on: S0. Size: S.

### S3. GPS gaps stay calm
- What: replay an S1 recording at the desk with a 10-second gap cut out. Marker moves along the route as "estimated", then rejoins ahead (P-02 to P-04).
- Bike test: ride the Esplanade under the Morrison and Burnside decks.
- Pass: no jump backward, no jump to the deck above, estimated state shown and cleared.
- Depends on: S1, S2. Size: M.

### S4. Follow a line
- What: load a GPX route (ride 13 exported from the PC) and draw it under the bike marker. No prompts.
- Bike test: ride ride 13 following only the line.
- Pass: you can follow it without stopping to read the screen.
- Depends on: S2, S6 (or any GPX). Size: S.

### S5. Off route
- What: off route after 3 readings 40 m from the line (N-04). One warning, clears when back on.
- Bike test: take one wrong turn on purpose.
- Pass: one warning within about 15 seconds, none during normal riding.
- Depends on: S4. Size: S.

### S6. Plan a route on the PC
- What: click start and end on the PC map, get a Greenway first route from the local engine, export GPX.
- Desk test: ride 13 start and end.
- Pass: route returns in under 3 seconds; GPX opens on the phone in S4.
- Depends on: engine running (`routing/start.bat`). Size: M.

### S7. Bridge stop with the right side
- What: add "Hawthorne westbound" by name; route goes through the north-side lane entry.
- Desk test, then bike: ride 13 outbound.
- Pass: route crosses the Hawthorne on the north side. Known bug E-07 makes this fail today; fix the lane data first.
- Depends on: S6. Size: M.

### S8. Next-turn banner
- What: distance and instruction for the next turn, lane side on bridges.
- Bike test: ride 13 with the banner.
- Pass: every turn shown before you reach it; no wrong-side bridge instruction.
- Depends on: S5, S7. Size: M.

### S9. Ride log
- What: a finished S1 recording becomes a ride log entry with name, date, distance; Banks-Vernonia added by hand.
- Pass: both show in the list after closing and reopening the page.
- Depends on: S1. Size: S.

## Checkpoints

- After S0 to S2: Whitney reviews on the bike. Decide web or native for Ride (N-08).
- After S3 to S5: replay tests pass on two recorded rides.
- After S6 to S8: ride 13 and ride 6 pass end to end.

## Later

Pass by stops, Road stops, Avoid, styles beyond Greenway first, alternatives, round trip, closures and lifts, sharing, dark mode, the design system build (`docs/design/design-system-prompt.md`).

## Risks

| Risk | Impact | What we do |
|---|---|---|
| Browser stops GPS with the screen locked | High | S1 measures it first; native Ride screen if it fails |
| OpenStreetMap lane data wrong (Hawthorne) | Medium | Fix tags or penalize the wrong side before S7 |
| Free hosting needs a public repo | Low | Privacy scan done: no home address or email in files or history |
| Map data licenses (Metro, PBOT) unknown | Medium | Use OpenStreetMap only until checked |

## Open questions

- Which C-TRAN routes use the I-5 and I-205 bridges?
- Answers to the six questions in `docs/design/design-system-prompt.md`.
- The 11 questions at the end of `docs/routing-rules.md`.
