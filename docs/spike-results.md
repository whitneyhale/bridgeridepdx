# Spike Results: Routing Engine

Date: 2026-10-23. GraphHopper 11.1, Portland extract from BBBike (OpenStreetMap, dated 2026-10-17), run on Linux with Java 21.

## What worked

- GraphHopper imports the Portland extract and answers bike routes quickly.
- Two profiles run side by side: `bike` (built-in balanced) and `greenway_first` (custom, in `routing/greenway_first.json`).
- It ships a clickable map at http://localhost:8989/maps/ where you can pick a profile.
- Bridges are in the data: Hawthorne, Morrison, and the Eastbank Esplanade all appeared in routes.

## Test results

| Route | Profile | Distance | Notes |
|---|---|---|---|
| SE Hawthorne & 20th to Pioneer Square | bike | 3.39 km | Hawthorne Bridge. 46% on secondary or primary roads. |
| Same | greenway_first | 3.46 km | Side streets, then the Morrison Bridge. 3% secondary, 0% primary. |
| N Williams to Alberta Arts | bike | 6.24 km | 48% on secondary or tertiary roads. |
| Same | greenway_first | 6.04 km | 60% residential. Followed Multnomah, Tillamook, Siskiyou, Klickitat, Alameda, Going. |

The greenway profile moves routes onto side streets at almost no extra distance. The Northeast route follows streets that look like Portland greenways, but it has not been checked against the city's official greenway data.

## Findings

- The greenway profile scores streets by road class, speed limit, and bike route relations. It does not know which streets the city calls greenways. If the Northeast result does not match your rides, add the city's bike network data as a boost layer.
- The greenway profile chose the Morrison Bridge over the Hawthorne in the first test. A Bridge stop forces a specific bridge, so the style alone will not pick your favorite one.
- Elevation was not tested. Flat style and climb budget need elevation data (SRTM), which could not be downloaded from the test machine. Test on your PC.
- `config.yml` allows snapping points onto bridges (the engine's default blocks it). Untested, and relevant to Bridge stops.

## Not yet tested

- Elevation, Flat style, climb budget.
- Round trip and alternatives.
- Via points, Road stops, Bridge stops, Avoid areas.
- Any comparison against the city's official bike routes.
- The Windows start script (`routing/start.bat`). Written, not run.
