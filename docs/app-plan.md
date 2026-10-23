# App Plan

One sentence: Bridge Ride helps Portland cyclists plan and follow bridge crossings on low-stress routes by prioritizing greenways and letting them pick the bridge and direction.

Target phone: Samsung Galaxy S24 Ultra (Android).

## Decisions so far

- Plan screen: web app (TypeScript) on the PC first. See [prototype-spec.md](prototype-spec.md).
- Ride screen: test as a web app on the phone first. If it loses GPS with the screen locked, build Ride as a native Android app (Kotlin). The tests and this plan carry over either way.
- Routing engine: GraphHopper, already running. See [spike-results.md](spike-results.md).

## Screens

1. **First launch**: short intro. No permission asked yet.
2. **Plan**: map plus editing tools.
3. **Ride**: full-screen map, next-turn banner, a few large buttons.
4. **Library**: saved routes, ride log.
5. **Settings**: default style, units, pass distance and detour budget.

```
First launch -> Plan <-> Library
                 |          |
                 v          v
                Ride <------+
                 |
              Settings (from any screen)
```

## Stories and priority

Must, Should, Could. Won't is listed at the end.

### Plan
- Must: As a rider, I click a start and an end so that I get a route.
- Must: As a rider, I pick a style so that the route matches how I want to ride.
- Must: As a rider, I name and save a route so that I can use it later.
- Should: As a rider, I add stops (Stop, Pass by, Road, Bridge, Destination) so that the route goes where I want.
- Should: As a rider, I drag a point to change the route.
- Could: As a rider, I see alternatives with distance, climb, and stress score.

### Ride
- Must: As a rider, I see my dot on the route and the next turn so that I never have to stop and look.
- Must: As a rider, I am told when I am off route so that I can fix it early.
- Must: As a rider, the app keeps working when my screen locks.
- Should: As a rider, I can reroute from where I am to my destination.
- Should: As a rider, I hear turn prompts.
- Could: As a rider, I report a hazard (glass, closed path).

### Library
- Must: As a rider, I open, rename, and delete saved routes.
- Should: As a rider, I mark a route as ridden with a date, or add a past ride (for example, Banks-Vernonia).
- Should: As a rider, I export a route as GPX.
- Could: As a rider, I share a route with the start and end trimmed.

### Settings
- Should: As a rider, I set my default style and units.
- Could: As a rider, I set pass distance and detour budget.

### Won't (for now)
Accounts, social features, live location sharing, themed rides, other cities.

## Rules

**Permissions**
- Ask for location when the rider taps Start ride, with a one-line reason. Not at launch.
- If denied, Plan and Library still work.
- If only approximate location is granted, say so and ask for precise.

**Background GPS (Android)**
- During a ride, run a foreground service of type location, with a persistent notification.
- Stop GPS when the ride ends.
- Resume the exact route and position after the screen locks or the app is backgrounded.

**Samsung battery settings**
Samsung's One UI restricts background apps more than stock Android. Before the first ride, show a short checklist:
1. Set the app to not be battery-optimized.
2. Make sure it is not on the Sleeping or Deep sleeping apps list.
3. Start the app in the foreground, then lock the screen.

Menu names vary by One UI version, and Samsung can reset these settings after an update, so re-show the checklist after an update.

**Privacy**
- Location history stays on the phone.
- No coordinates in logs.
- Shared routes: trim the start and end, round the distance, and compute distance from the trimmed route only.

**Offline**
- Saved routes and the map along the route work without signal.
- Rerouting needs a connection until the engine runs on the phone.

**Quality**
- Voice prompts handle other apps' audio.
- Map stays smooth (target 60 fps).

## Structure

Three layers.

- **UI**: Plan, Ride, Library, Settings.
- **Navigation logic**: smooth the GPS readings, snap to the route, track progress to the next turn, detect off-route. Pure logic, no map code, testable by replaying recorded rides.
- **Data**: route files, ride log, routing engine client, map tiles.

## Tests

1. Replay a recorded GPX ride into the Ride screen.
2. Replay with a 10-second GPS dropout.
3. Replay with a deliberate wrong turn: off-route triggers once, then clears.
4. Lock the screen mid-ride and unlock: same route, same position.
5. Deny location: Plan and Library still work.
6. Grant approximate only: app warns.
7. Ride with Samsung battery settings changed and unchanged, and compare the recorded tracks.
8. Airplane mode: a saved route still guides.

Decision gate: ride 30 minutes with the web version and the screen locked for 10 of them. If the recorded track has gaps longer than 10 seconds, build Ride natively. The 10 seconds is a starting value.

## Open items

- Wireframes: first drafts done 2026-10-23, see [design/README.md](design/README.md). Not yet reviewed by Whitney.
- Check the license terms for the city's Recommended Bicycle Routes data before using it.
- Check whether Metro publishes the Bike There layer as downloadable data, its terms, and its coverage area. The map page itself does not name its data sources.
- Decide whether to use Metro's six route categories as the labels for the route breakdown and stress score.
- Pick the 10 to 15 test rides.

## Research notes

Findings behind this plan, with sources:

- Metro's [Bike There](https://gis.oregonmetro.gov/bikethere/) map rates every route by comfort in six types: Established Bikeway, Bike Friendly Street (no lane, low traffic, under 25 mph), Designated Bike Lane, Helpful Connection (no lane, medium traffic, over 25 mph), Bike with Caution, and Unpaved Trail. It is a ready-made scale for a stress score. It does not cover hills, route planning, or bridges.
- Existing Portland tools each miss something: the city map has no directions, Pointz needs a connection, Ride with GPS is awkward on phones, Google Maps overstates bikeability ([BikePortland](https://bikeportland.org/2024/03/19/tools-for-finding-my-way-around-portland-by-bike-384887)).
- Cyclists made fewer errors with turn-by-turn than with a straight-line arrow, in a small study ([Bremen](https://www.hci.uni-bremen.de/wp-content/uploads/2020/07/Point_Me_In_the_Right_Direction.pdf)).
- Privacy zones can leak start and end through total distance ([Dark Reading](https://www.darkreading.com/application-security/popular-fitness-apps-leak-location-data-even-when-users-set-privacy-zones)).
- A foreground service of type location keeps GPS running in the background on Android ([Android docs](https://developer.android.com/develop/sensors-and-location/location/permissions)).
- Android quality guidance: ask for location at navigation start, degrade gracefully, resume exact state ([Android quality](https://developer.android.com/docs/quality-guidelines/core-app-quality)).
- Samsung restricts background apps by default and its settings can revert ([Don't Kill My App](https://dontkillmyapp.com/samsung)).
- The web Wake Lock releases when the app is minimized ([Progressier](https://progressier.com/pwa-capabilities/screen-wake-lock)).
- Requirements format: one sentence, screens in user order, stories per screen, MoSCoW ([Smashing Magazine](https://www.smashingmagazine.com/2017/05/writing-mobile-application-requirements/)).
