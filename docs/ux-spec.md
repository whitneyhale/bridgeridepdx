# UX Spec

How the app looks and behaves, for whoever builds the screens. Look: Wayfinding (decided). Updated 2026-10-23.

Sources of truth, in order: Whitney's words, `docs/design/design-system-prompt.md` ("Already decided"), this file, the screen drafts in `docs/design/`.

## Principles

1. **Glance, then ride.** While riding show only the next turn, its lane side, the distance, and state. Everything else waits. Interaction is a few big buttons ([DFKI Cyclo study](https://dfki.de/fileadmin/user_upload/import/7091_MGIA099_Cyclo_cr-draft.pdf)).
2. **Keep right, say the side.** Bridge instructions name the side and direction: "Keep right onto Hawthorne Bridge, north side path, westbound" (E-01).
3. **Color plus shape.** Never color alone. The route line is thicker than greenways and has a casing; warnings carry an icon and words. Required by the palette check: route blue and greenway green fail color-blind separation.
4. **Outdoor first.** High contrast, large text, one hand, gloves ([outdoor software notes](https://blog.apario.net/on-writing-software-for-people-who-work-outside)).
5. **Honest state.** GPS estimated, GPS lost, off route, closed, lift ahead: each shown in words.

## Decided look (do not change without asking)

- Fonts: Barlow Semi Condensed for headings, signs, and big numbers; Inter for text.
- Colors: ink #15211D, ground #F3F5F2, greenway #1B6B45, river blue #1F5F8B, signal orange #B4480F for warnings only. Proposed: forest #0F3D2B, rose #9B1B30, Columbia #1769B8.
- Theme: roses, bikes, bridges; cute, chunky, rounded marks; one cartoon icon per bridge from `data/geometry.json`.
- Touch targets 44 px or larger; ride controls 64 px.
- Light and dark themes.

Checked with `tools/check_palette.py` (`docs/design/palette-report.md`): all 20 contrast pairs pass; 5 color-blind pairs fail and are questions for Whitney, not changes.

## Rider marker

A small bike, not an arrow (Whitney, 2026-10-23).

- 38 px disc in river blue with a 3 px white ring and a white bicycle glyph (two wheels and a frame, 2.2 px stroke).
- Heading: a small wedge on the ring's edge, pointing the way of travel. Use GPS heading when moving; use the route direction when slow (N-02).
- GPS estimated: disc turns signal orange and a dashed circle shows the uncertainty, growing with time (P-02).
- Turn arrows in the next-turn banner stay arrows; they show the maneuver, not the rider.

## Screens

| Screen | Must show | States to draw |
|---|---|---|
| Map (idle) | Search, where you are (east or west, north or south of Burnside, bridge band), nearby bike routes, nearest bridges | location denied, approximate location only, offline |
| Plan | Stops by type, routing style, summary, Save, Start ride | pass-by skipped ("would add 1.2 km"), route error |
| Ride | Next-turn banner with lane side, bike marker, next leg, Pause, Re-route, End | GPS estimated, GPS lost, off route, lift warning, closed side |
| Bridge sheet | Category, who shares it, which side for each direction, getting on and off, description | closed to bikes (Fremont, Marquam), side closed |
| Library | Planned and Ride log, search | empty |
| Settings | Default style, pass-by distance and detour, overpass preference, units, battery check, privacy | |
| First launch | Pitch, three steps, Samsung battery note; no permission prompt yet | |
| PC Plan | Stops panel, style, stats, map with legend | engine not running |

Fix from the draft review: the Plan "add stop" row and the Settings Privacy row sit under the bottom bar.

## Components and behavior

- **Bottom sheet** on map screens: non-modal, map stays usable, three snap points (peek with title and main action, half, full), drag handle plus a visible Close ([bottom sheet guidance](https://www.eleken.co/blog-posts/bottom-sheet-ui)).
- **Next-turn banner**: ink panel, distance in Barlow (44 px), instruction (18 px), lane side chip.
- **Status pill**: one line, icon and words.
- **Stop row**: type tile, overline with type and direction, name, one caption line.
- **Style chips**: selected state has a check icon, not color alone.
- **Ride controls**: Pause, Re-route, End at 64 px.
- **Night mode**: dark base, bright lines and labels ([Mapbox Navigation Night](https://docs.mapbox.com/map-styles/reference/navigation-night.md)); check the map at several zoom levels and in night mode ([Google map style checklist](https://developers.google.com/maps/documentation/maps-static/cloud-customization/design-resources-leg)).
- **Route summary before Start**: distance, time, share on greenways, bridges used, like Apple Maps' elevation and route card ([9to5Mac](https://9to5mac.com/2020/10/14/how-to-use-apple-maps-cycling-directions-ios-14/)).

## Map layers

| Layer | Style |
|---|---|
| Route | river blue, 6 px, white casing 2 px each side, always on top |
| Greenways, car-free paths | greenway green, 4 px; neighborhood greenways dashed |
| Bike lanes | greenway green, 2 px |
| Bikeable bridges | ink at 60%, name label from zoom 13 |
| Bridges closed to bikes | gray, no emphasis |
| Overpasses | small icon with an orange ring (low preference); Blumenauer with a green ring |
| Rider | bike marker above |

## Accessibility

Real buttons and links, visible focus, labels on icon buttons, 3:1 for icons and lines and 4.5:1 for text ([WCAG 2.2 non-text contrast](https://www.w3.org/WAI/WCAG22/Understanding/non-text-contrast.html)), reduced motion respected, every map state also in words.
