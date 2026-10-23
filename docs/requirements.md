# Requirements

Bridge Ride PDX: a free cycling route planner for Portland built around the river bridges. Owner: Whitney Hale. Updated 2026-10-23. Rule IDs point to `docs/routing-rules.md`.

## Must

1. **Free.** Planning, saving, and riding never sit behind a paywall (G-01). No accounts, no social features.
2. **Bridges are directional crossings.** Each bridge is eastbound or westbound (northbound or southbound for the Columbia bridges and Blumenauer), with GPS points on both sides and the lane side to ride (B-01 to B-16, E-01 to E-08).
3. **Bridge names work any way you say them**: nicknames, misspellings, colors (B-04, B-05).
4. **Greenways first.** Cars on car roads, bikes on bike routes (S-01, Y-01, H-01 to H-12). Eastbank Esplanade is the preferred north-south spine on the east side (H-08).
5. **Custom routes** with five stop types: Stop, Pass by, Road, Bridge, Destination; Avoid is a rule (T-01 to T-10).
6. **Save, name, edit, export GPX.** A saved route never changes on its own (G-03).
7. **Ride with GPS** on a Samsung S24 Ultra: position with heading, next turn, off-route warning, keeps working with the screen locked (N-01 to N-08).
8. **Survive GPS gaps** near and under bridges: estimated position along the route, rejoin ahead, no false off-route (P-01 to P-08).
9. **Ride log** of completed rides, including rides outside the map (D-05).
10. **Privacy.** Location history stays on the phone; shared routes trim start and end (D-01, D-02). No home address in the repo.

## Should

- Routing styles: Greenway first, Balanced, Fastest safe, Low stress, Flat, Scenic, Round trip, Smooth surface (Y-01 to Y-09).
- Overpasses are low preference and labeled; Blumenauer is preferred (O-01 to O-08).
- Bridge categories by who shares them (C-01 to C-10) and readable bridge descriptions (D-07).
- Location context: east or west side, north or south of Burnside, nearest bridges and bike routes (L-01 to L-07).
- Closures and lifts change which bridge or side is offered (X-01 to X-06).
- Light and dark themes in the Wayfinding look (`docs/design/design-system-prompt.md`).

## Platform

- PC prototype first (TypeScript web app, MapLibre), then the phone. Python only for data scripts.
- Routing engine: GraphHopper 11.1 on the PC, OpenStreetMap Portland extract.
- Phone testing in Chrome on Android over HTTPS (GitHub Pages once the repo is public).
- Decision gate: if a 30-minute ride with the screen locked for 10 minutes has GPS gaps over 10 seconds, build the Ride screen natively in Kotlin (N-08).

## Test targets

- Routing realism against Whitney's rides (`docs/test-rides.md`).
- Routing speed under 3 seconds on a normal PC; memory noted.
- GPS through drops and uncertain position, checked by replaying recorded rides.

## Won't (this version)

Accounts, social features, live location sharing, themed rides, other cities.
