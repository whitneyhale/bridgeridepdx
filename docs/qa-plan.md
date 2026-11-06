# QA plan (summary)

Full version: the "QA and test plan" tab of the claude.ai Android plan. Updated 2026-11-05.

## Requirements

1. A release ships only when unit tests, data checks, lint and replay tests pass in CI and Whitney answers its test card.
2. A ride bug becomes a failing replay test first, then the fix.
3. Severity: S1 blocks the release (wrong bridge or lane side, banned way, crash mid-ride, lost ride); S2 next task; S3 when convenient.
4. One task at a time; a failed retest reopens the task that broke it.
5. Every rule ID in `docs/routing-rules.md` gets a test named after it (for example `N04_off_route_after_three_readings`).
6. Debug log on the phone records fixes and decisions; exported with the GPX; nothing uploads.

## Test card (every release)

```
Release: 0.x.y   Task: 1.3   Slice: S1
What changed: one line
Bike test: route, length, what to do
Pass when: observable result
Questions (max 3):
1.
Send back: pass or fail, GPX, answers
```

## Bug report (GitHub Issue)

```
Severity: S1 / S2 / S3
Release: 0.x.y   Rule ID (if known):
Where: street or bridge, direction
What happened:
What should have happened:
Attached: GPX and debug log from the ride
```
