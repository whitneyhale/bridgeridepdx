# Bridge Ride PDX: main screens (v2, new theme)

Rebuilt 2026-10-23 from the first drafts in this folder. Copied from the claude.ai project on 2026-11-05. The v2 images (`01-map` to `07-first-launch`, light and dark) live in the Figma file "Bridge Ride PDX - phone screens v2", not in this repo.

Light and dark for every screen except First launch, which is always forest green. Theme: Forest, Rose, Greenway, Willamette, Columbia. Heading font in the images is a stand-in (DejaVu Sans Condensed); the real one is Barlow Semi Condensed. Values in [brackets] are not computed yet. The map is a stylised, not-to-scale drawing, not real tiles. Warnings stay orange (decision still open).

## 1. Map (idle)
Search bar with rose mark ("Where to? Try "the hawthorne""), layers and locate buttons, filter chips (East side, South of Burnside, Morrison to Hawthorne; the row scrolls). Map shows the Willamette, greenways in green with white casing, car-free paths dashed, bridges as bars with name pills (Hawthorne highlighted, Marquam dimmed and dashed for no bikes), your position in blue with heading. Sheet "Near you": two bridge cards with layer 2 icons (Hawthorne, Morrison) and a bike routes row (Eastbank Esplanade). Four-tab nav: Map, Plan, Library, Settings.

## 2. Plan a route
Route name, summary (Loop, 5.5 mi, Greenway first), Save. Small map with the loop route. Five stop rows with type glyphs and drag handles: Start, Bridge westbound, Road northbound, Bridge eastbound, Destination (loop). "Add a stop" row now fully visible. Style chips (Greenway first, Balanced, Low stress, Scenic) and a large Start ride button with the bicycle mark.

## 3. Ride
Forest next-turn banner (500 ft, Keep right onto Hawthorne Bridge, North side path, Westbound). Orange pill "GPS estimated, last fix 12 s ago". Lift bridge warning card. Map with your arrow inside a dashed uncertainty circle and the route in Willamette blue. Bottom panel: next leg, [mi] left, Arrive [time], and Pause, Re-route, End (End in rose).

## 4. Bridge sheet
Hero scene with the layer 2 Hawthorne icon and the bike with rose basket. Tags (Mixed use bridge, Lift bridge), name, one-sentence summary, Cross eastbound and Cross westbound buttons. Which side: North = westbound path, South = eastbound path, with the data caveat. Shared with chips and Where it is. The sheet scrolls; Getting on and off and Good to know sit below.

## 5. Library
Planned / Ride log toggle, search. Two route cards with map thumbnails, length and tags (Hawthorne out, Steel back; Sellwood loop). Recently ridden entry (Banks-Vernonia Trail, [date]). Log a past ride.

## 6. Settings
Routing (default style, pass-by 150 m and 400 m), Crossings (overpasses, stairs, streetcar and MAX tracks), Riding (units, Samsung battery check), Privacy (trim start and end when sharing). Fits above the nav with the Privacy row fully visible.

## 7. First launch
Always forest. Columbia band across the top (wider, bluer), Willamette below with Steel, Hawthorne and Sellwood icons, bike with rose basket. Name, pitch ending "Free.", three numbered steps (rose circles), Samsung battery note, Get started.

## Fixed from the first drafts
Plan: "Add a stop" row was cut off by the bottom bar. Settings: bottom nav covered the Privacy row. Both now fit with 8 px or more clearance.

## Needed in v3 (plan task 1.0, decided 2026-11-05)

- Your position is the bike marker (N-09), never a blue dot or arrow. v2 Map and Ride show an arrow: fix.
- Nav: Map, Library, Settings. The Plan tab goes; search starts plans.
- Map: the label chips become Home, Work and favorites (N-14). Layers button 48 px.
- Ride: one status line, most urgent first (lift, off route, GPS). Lift warning only within half a mile. No Re-route button (N-13). End is press-and-hold 1 s. A Destination button opens Home, Work, favorites. Dark variant for night riding.
- Plan: one "Style" control instead of chips. Stops as numbered circles placed where they really are; best order line (T-11).
- Bridge sheet: one Cross button from your side of the river; details fold below; Sources row full width.
- Library: toggle 48 px; no test labels on cards.
- First launch: river picture, one line, Get started; Samsung battery steps move to the first Start ride.
- New: end-of-ride card (rating, bridges crossed as icons) for task 1.7.

## Still open
Logo pick (A, B or C), unverified bridge colors, map base (restyled tiles or default), orange vs red for warnings, Metro Bike There license for route-type colors, tagline.
