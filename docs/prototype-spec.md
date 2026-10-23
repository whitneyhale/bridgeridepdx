# Prototype Spec: PC Route Builder

A local app that runs on a PC. Plan a ride on a map, name it, save it, edit it, and control exactly where it goes. No accounts, no phone, no server to rent.

## What you can do

1. Click a start and an end. Get a bike route.
2. Add stops along the way. Each stop has a type:
   - **Via**: the route must pass through this point.
   - **Ride this road**: the route must use a chosen street (pick the street, not a point).
   - **Bridge**: pick a bridge and a direction. The route crosses it that way.
   - **Avoid**: a street or drawn area the route stays out of.
3. Pick a routing style (below).
4. Drag a point, add one, delete one, or reorder them. The route recalculates.
5. Name the route, add notes and tags, save it.
6. Open, rename, duplicate, edit, or delete saved routes.
7. Export to GPX (for a bike computer or phone). Import GPX.

## Routing styles for cyclists

| Style | What it does |
|---|---|
| Greenway first | Strongly prefers neighborhood greenways, paths, and bike boulevards. Accepts a longer route. |
| Balanced | Greenways when the detour is small, direct otherwise. |
| Fastest safe | Shortest route that still avoids high-stress roads. |
| Low stress | Avoids roads with high speed limits, many lanes, and no bike lane. |
| Flat | Penalizes climbing. Max grade setting. |
| Scenic | Prefers riverfront, parks, and paths. |
| Round trip | Give a start, a distance or time, and a direction. Returns a loop. |
| Smooth surface | Avoids gravel, cobbles, and rough paving. |

Settings that apply on top of any style:

- **Streetcar and MAX tracks**: avoid or cross at a safe angle only. Portland-specific hazard.
- **Intersections**: penalize unprotected left turns across busy roads and unsignaled arterial crossings. Prefer signals and crossing islands.
- **Dismount sections**: avoid stairs and walk-your-bike segments.
- **No backtracking**: do not reuse a road already ridden on this route.
- **Climb budget**: cap total elevation gain.
- **Night**: prefer lit roads and paths.
- **Alternatives**: show up to three options with distance, time, climbing, and a stress score.

Each route shows an elevation profile and a breakdown: percent on greenway, path, bike lane, and shared road.

## Bridges

- Each bridge has two directional crossings: eastbound and westbound (see `data/bridges.json`).
- Typing "burnside", "burn side", or a color resolves to the same bridge.
- A bridge stop becomes two forced points: the approach and the exit, in the chosen direction.
- Bridges closed to bikes (for example, the Marquam) are not offered.

## Route data stored

For each saved route:

- Name, notes, tags, created and edited dates.
- Ordered stops with their types.
- Routing style and settings.
- The resulting path, saved as drawn. A saved route does not silently change when map data changes. A "Re-route" button recalculates it on request.

Edits keep the previous version so you can undo back.

## How it is built

```
Browser (map + sidebar)  <->  Local app (Python)  <->  Routing engine (GraphHopper)
                                   |
                               SQLite file (routes)
```

- **Map UI**: web page at localhost. MapLibre or Leaflet, OpenStreetMap tiles.
- **Local app**: Python (FastAPI). Saves routes to SQLite, resolves bridge names, calls the routing engine.
- **Routing engine**: GraphHopper, a single Java program. It loads a Portland-area OpenStreetMap extract and elevation data. Its custom model feature lets us write the cyclist rules above as weights and exclusions, and it supports via points, avoid areas, round trips, and alternatives.
- **Run it**: one start script launches both programs. Works on Windows, Mac, or Linux.
- **Alternative engine**: BRouter. Also runs from a single jar and has strong custom bike profiles. Pick whichever passes the first spike test.

## Data sources

- OpenStreetMap, Oregon or Portland-area extract (Geofabrik or BBBike).
- Elevation: SRTM or the Oregon lidar-based data if available.
- Portland's official bike network and greenway data from the city's open data, used as a boost layer on top of OpenStreetMap. Needed if OpenStreetMap greenway tags turn out to be incomplete.

## Build order

1. **Spike (first)**: run GraphHopper on a Portland extract. Route between two points over the Hawthorne and compare against what you would ride. Check how well greenways are tagged in OpenStreetMap. This decides whether the city data layer is needed in phase 1 or later.
2. **Map and basic routing**: click start and end, draw the route, show distance, time, climb.
3. **Stops and edits**: via, drag, add, delete, reorder.
4. **Save and manage**: name, notes, tags, save, open, rename, duplicate, delete, GPX export and import.
5. **Cyclist styles**: greenway first, balanced, low stress, flat, plus the settings above.
6. **Ride this road, Bridge, Avoid**: the constrained stops.
7. **Round trip and alternatives**.

## Done means

- A route from any two Portland points returns in under 3 seconds on a normal PC.
- Greenway first produces a route that matches Portland's official greenways for the test routes you pick, and you agree it looks right.
- A Bridge stop crosses the named bridge in the named direction every time.
- A saved route reopens identically after restarting the app.
- GPX export opens correctly in a bike computer or mapping app.

## Test routes (fill in)

List 10 to 15 rides you actually do or want to do, each with start, end, and the bridge or street you expect. These become the pass/fail checks for routing realism.

## Out of scope for the prototype

Phone app, live GPS tracking, turn-by-turn voice, sharing links, user accounts, themed rides. These come after routing is trusted.

## Open questions

- Windows, Mac, or Linux PC? Decides the start script.
- Is Java OK to install? GraphHopper needs it. If not, we use a different engine.
- Which 10 to 15 rides should be the test routes?
