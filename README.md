# Bridge Ride PDX

A free cycling route planner for Portland, built around the Willamette River bridges.

## What it does

- Routes across the Willamette using Portland's bridges, with greenways prioritized.
- Custom routes you create and save, with no paywall.
- Route preference settings (greenway vs. direct, hills, traffic comfort).
- Turn-by-turn directions.
- Live GPS tracking.
- Route sharing.

## Core idea

A bridge is a directional crossing, not a point. "Burnside eastbound" and "Burnside westbound" are different crossings. Colloquial names, misspellings, and bridge colors should all resolve to the right bridge.

## Status

Starter scaffold. See [docs/requirements.md](docs/requirements.md) for scope and priorities.

## Layout

- `docs/requirements.md` - goals, priorities, safety rules, test targets
- `docs/prototype-spec.md` - PC prototype: features, cyclist routing styles, architecture, build order
- `docs/app-plan.md` - Android app plan: screens, stories, rules, structure, tests, research notes
- `docs/test-rides.md` - pass/fail test rides for routing realism
- `docs/spike-results.md` - first routing engine test results
- `routing/` - local routing engine setup (GraphHopper). On Windows, double-click `routing/start.bat`, then open http://localhost:8989/maps/
- `data/bridges.json` - bridges, aliases, and directional crossings
- `web/index.html` - starter map page (Leaflet + OpenStreetMap)

## Run locally

```
python3 -m http.server 8000
```

Run it from the repo root. Open http://localhost:8000/web/ on your computer. To test on a phone on the same Wi-Fi, open `http://<your-computer-ip>:8000/web/`. Phone GPS needs HTTPS or localhost, so for on-phone GPS testing use a deployed copy.
