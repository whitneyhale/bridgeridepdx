# Bridge Ride PDX: main screens

Renders of the first drafts in `docs/design/` (2026-10-23, not yet reviewed). Look: Wayfinding. Heading font in the images is a stand-in (DejaVu Sans Condensed); the real one is Barlow Semi Condensed. Values in [brackets] are not computed yet.

## 1. Map (idle) - `01-map.png`
Search bar ("Where to? Try "the hawthorne""), map layers button, filter chips (East side, South of Burnside, Between Morrison and Hawthorne). Map shows the river, bridges as gray bars with names, greenways in green, your position in blue. Bottom sheet "Near you" lists nearby bike routes (Eastbank Esplanade, Salmon-Taylor, Clay Street) and two nearest bridge cards (Hawthorne, Morrison). Four-tab nav: Map, Plan, Library, Settings.

## 2. Plan a route - `02-plan.png`
Route name and summary (Loop, 5.5 mi, Greenway first), Save button, small map. Stops list with type labels: Start, Bridge westbound, Road northbound, Bridge eastbound, Destination (loop). Drag to reorder. Bottom: style chips (Greenway first, Balanced, Low stress, more by scroll) and a large Start ride button.
Seen in the render: the dashed "add stop" row is cut off by the bottom bar.

## 3. Ride - `03-ride.png`
Dark next-turn banner (500 ft, Keep right onto Hawthorne Bridge, North side path, westbound). Orange pill "GPS estimated, last fix 12 s ago". Lift bridge warning. Map with your arrow inside a dashed uncertainty circle. Bottom panel: next leg, [mi] left, Arrive [time]; Pause, Re-route, End buttons.

## 4. Bridge sheet - `04-bridge-sheet.png`
Category tag (Mixed use bridge), name, one-sentence summary, Cross eastbound and Cross westbound buttons. "Which side" shows North = westbound path, South = eastbound path, with a data caveat. "Shared with" chips (Cars, TriMet lines, Bikes, People walking). Where it is, Getting on and off.

## 5. Library - `05-library.png`
Planned / Ride log toggle, search by name or tag. Saved route cards with length and tags (Hawthorne out, Steel back; Sellwood loop). Recently ridden entry (Banks-Vernonia Trail, [date]). "+ Log a past ride".

## 6. Settings - `06-settings.png`
Routing: default style, pass-by distance (150 m), pass-by detour (400 m). Crossings: overpass preference, avoid stairs, streetcar and MAX track warning. Riding: units, Samsung battery check. Privacy: trim start and end when sharing.
Seen in the render: the bottom nav covers the Privacy row until you scroll.

## 7. First launch - `07-first-launch.png`
Dark screen. Logo mark (river with three bridge bars), "Bridge Ride PDX", one-line pitch ending "Free." Three numbered steps (say a bridge any way, pick direction, save and ride). Samsung battery note, location asked at Start ride. Get started button.

## 8. PC plan - `08-pc-plan.png`
Desktop prototype page: top bar (name, Open, Export GPX, Save), left stops panel with the five stop types plus Avoid, routing style, stats (5.5 mi, [%] on greenway or path, 2 bridges), large map with legend (Your route, Greenway or path, Other bridge).

Update 2026-10-23: the rider position on Map and Ride is now a small bike marker with a heading wedge, not an arrow or dot.
