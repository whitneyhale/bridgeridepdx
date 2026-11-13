"""Write the Maestro flows that ride a built-in line on the emulator (release 0.3).

The flows need GPS points along the real line: before a bridge, over it, off the line, back on.
This reads android/app/src/main/assets/rides/hawthorne-steel.csv and data/bridges.json and writes
android/maestro/04-follow-a-line.yaml (light look) and 05-ride-dark.yaml (dark look).
CI runs this and fails if the committed flows are out of date, so they cannot drift from the ride file.
"""
import json
import math
import pathlib

ROOT = pathlib.Path(__file__).resolve().parent.parent
RIDE = ROOT / "android" / "app" / "src" / "main" / "assets" / "rides" / "hawthorne-steel.csv"
OUT = ROOT / "android" / "maestro"
BRIDGES = {b["id"]: b for b in json.loads((ROOT / "data" / "bridges.json").read_text(encoding="utf-8"))["bridges"]}
R = 6371008.8
STEP_M = 20.0  # one point every 20 m, ridden at 20 m/s: one fix a second


def dist(a, b):
    d_lat = math.radians(b[0] - a[0])
    d_lon = math.radians(b[1] - a[1])
    h = math.sin(d_lat / 2) ** 2 + math.cos(math.radians(a[0])) * math.cos(math.radians(b[0])) * math.sin(d_lon / 2) ** 2
    return 2 * R * math.asin(math.sqrt(h))


def bearing(a, b):
    la, lb = math.radians(a[0]), math.radians(b[0])
    dl = math.radians(b[1] - a[1])
    return (math.degrees(math.atan2(math.sin(dl) * math.cos(lb), math.cos(la) * math.sin(lb) - math.sin(la) * math.cos(lb) * math.cos(dl))) + 360) % 360


def offset(p, bearing_deg, meters):
    b = math.radians(bearing_deg)
    return (p[0] + math.degrees(meters * math.cos(b) / R), p[1] + math.degrees(meters * math.sin(b) / (R * math.cos(math.radians(p[0])))))


class Line:
    def __init__(self, points):
        self.points = points
        self.cum = [0.0]
        for i in range(1, len(points)):
            self.cum.append(self.cum[-1] + dist(points[i - 1], points[i]))

    def at(self, along):
        along = max(0.0, min(self.cum[-1], along))
        for i in range(1, len(self.cum)):
            if self.cum[i] >= along:
                seg = self.cum[i] - self.cum[i - 1]
                t = 0.0 if seg == 0 else (along - self.cum[i - 1]) / seg
                a, b = self.points[i - 1], self.points[i]
                return (a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t)
        return self.points[-1]

    def bearing_at(self, along):
        return bearing(self.at(along - 5), self.at(along + 5))

    def span(self, bridge_id, first_end):
        """Where the line is over a bridge: distance along at 30% and 95% of the way from one end to the other."""
        ends = BRIDGES[bridge_id]["ends"]
        a = ends[first_end]
        b = next(v for k, v in ends.items() if k != first_end)
        length = dist(a, b)

        def fraction(p):
            ax = math.radians(p[1] - a[1]) * math.cos(math.radians(a[0])) * R
            ay = math.radians(p[0] - a[0]) * R
            bx = math.radians(b[1] - a[1]) * math.cos(math.radians(a[0])) * R
            by = math.radians(b[0] - a[0]) * R
            t = (ax * bx + ay * by) / (length * length)
            off = math.hypot(ax - bx * max(0, min(1, t)), ay - by * max(0, min(1, t)))
            return t, off

        inside = [i for i, p in enumerate(self.points) if fraction(p)[1] <= 110 and 0.3 <= fraction(p)[0] <= 0.95]
        runs = [inside[0]]
        for i in inside[1:]:
            if i - runs[-1] > 3 and len(runs) > 3:
                break
            runs.append(i)
        return self.cum[runs[0]], self.cum[runs[-1]]


def fmt(p):
    return f"{p[0]:.6f},{p[1]:.6f}"


def travel(points, speed):
    return "- travel:\n    points:\n" + "".join(f"      - {fmt(p)}\n" for p in points) + f"    speed: {speed}\n"


def location(p):
    return f"- setLocation:\n    latitude: {p[0]:.6f}\n    longitude: {p[1]:.6f}\n"


PERMISSIONS = """# Permission dialogs differ by Android version; tap whichever appears.
- tapOn:
    text: "While using the app"
    optional: true
- tapOn:
    text: "Only this time"
    optional: true
- tapOn:
    text: "Allow"
    optional: true
"""


def main() -> None:
    pts = [tuple(map(float, l.split(","))) for l in RIDE.read_text(encoding="utf-8").splitlines() if l and not l.startswith("#")]
    line = Line(pts)

    # Hawthorne, westbound: from 250 m before the river to 100 m past the far bank.
    h0, h1 = line.span("hawthorne", "east")
    start, end = h0 - 250, h1 + 100
    over_hawthorne = [line.at(start + i * STEP_M) for i in range(int((end - start) / STEP_M) + 1)]
    after = over_hawthorne[-1]
    side = line.bearing_at(end) - 90  # left of travel: inland, away from the river
    wrong_turn = [offset(after, side, m) for m in (120, 135, 150)]

    light = f"""# Release 0.3 (tasks 1.4, 1.5, 1.6): pick a built-in ride, follow its line over the Hawthorne Bridge with
# simulated GPS, take a wrong turn, come back, lock and unlock, end, and see the crossing in the summary,
# the passport and the bridge sheet. Then ride the recorded ride again.
# The GPS points are made by tools/build_ride_flows.py from the ride file; do not edit them by hand.
appId: pdx.bridgeride.app
---
- launchApp:
    clearState: true
- extendedWaitUntil:
    visible: "Map, 28 bridge ends, light"
    timeout: 30000
{location(over_hawthorne[0])}- tapOn: "Plan a ride"
- assertVisible: "Built-in rides"
- takeScreenshot: shot-7-plan-list-light
- tapOn: "Hawthorne out, Steel back"
- assertVisible: "Loop · 2.6 mi · 2 bridges"
- assertVisible: "Go"
- waitForAnimationToEnd:
    timeout: 5000
- takeScreenshot: shot-8-plan-ride-light
- assertVisible: "BRIDGE · WESTBOUND · NORTH SIDE"
# On CI's small screen the second bridge is below the fold. Swipe inside the stop list
# (a swipe from the middle of the screen would pan the map instead).
- swipe:
    start: "50%, 66%"
    end: "50%, 53%"
- swipe:
    start: "50%, 66%"
    end: "50%, 53%"
- assertVisible: "BRIDGE · EASTBOUND · SOUTH SIDE"
- takeScreenshot: shot-8b-plan-stops-light
- tapOn: "Go"
{PERMISSIONS}# On the line, 250 m before the river: the banner names the next bridge, its direction and side.
- extendedWaitUntil:
    visible: "Hawthorne Bridge"
    timeout: 30000
- assertVisible: "Westbound, north side"
- assertVisible: "mi left"
- assertVisible: "Bike marker, position good.*"
- waitForAnimationToEnd:
    timeout: 5000
- takeScreenshot: shot-9-ride-next-bridge-light
# Ride over the bridge at 20 m/s, a fix every second.
{travel(over_hawthorne, 20)}# Past the Hawthorne: the next bridge is the Steel.
- extendedWaitUntil:
    visible: "Steel Bridge"
    timeout: 20000
- assertVisible: "Eastbound, south side"
# The marker has a heading now. (Stopped, the emulator's compass decides which; moving, it was west.)
- assertVisible: "Bike marker, position good, heading (north|northeast|east|southeast|south|southwest|west|northwest)"
- waitForAnimationToEnd:
    timeout: 3000
- takeScreenshot: shot-10-ride-after-bridge-light
# A wrong turn: 120 m and more from the line for over 8 s. One calm warning, with the way back.
{travel(wrong_turn, 3)}- extendedWaitUntil:
    visible: "off the line"
    timeout: 30000
- assertVisible: ".*No rerouting yet\\\\."
- takeScreenshot: shot-11-ride-off-line-light
# Back on the line: the warning clears itself.
{location(after)}- extendedWaitUntil:
    visible: "Steel Bridge"
    timeout: 30000
# Rain and gloves: Lock ignores touches until Unlock is held.
- tapOn: "Lock"
- assertVisible: "Unlock"
- assertVisible: "Screen locked.*"
- takeScreenshot: shot-12-ride-locked-light
- longPressOn: "Unlock"
- extendedWaitUntil:
    notVisible: "Unlock"
    timeout: 10000
- longPressOn: "End"
- extendedWaitUntil:
    visible: "RIDE SAVED"
    timeout: 15000
- assertVisible: "Hawthorne out, Steel back, .*"
- assertVisible: "Bridges crossed"
- assertVisible: "westbound"
- assertVisible: "New stamp"
- assertVisible: "Passport 1 of 12"
- takeScreenshot: shot-13-summary-light
- scrollUntilVisible:
    element: "Prettier"
    direction: DOWN
- tapOn: "Good"
- tapOn: "Prettier"
- takeScreenshot: shot-13b-summary-rated-light
- scrollUntilVisible:
    element: "Done"
    direction: DOWN
- tapOn: "Done"
- assertVisible: "Plan a ride"
# The ride log names the ride, and the passport has the stamp.
- tapOn: "Library"
- assertVisible: "Ride log"
- assertVisible: "Hawthorne out, Steel back, .*"
- assertVisible: ".*1 bridge.*rated Good"
- takeScreenshot: shot-14-library-light
- tapOn: "Passport"
- assertVisible: "Bridge passport"
- assertVisible: "1 of 12"
- assertVisible: "Hawthorne, stamped, crossed 1 time"
- takeScreenshot: shot-15-passport-light
- tapOn: "Hawthorne, stamped, crossed 1 time"
- assertVisible: "Hawthorne Bridge"
- assertVisible: "Westbound: ride the north side"
- assertVisible: "Passport: crossed once."
- takeScreenshot: shot-16-bridge-sheet-light
- tapOn: "Close bridge details"
# Ride it again: the recorded ride becomes a line to follow, with its bridge found.
- tapOn: "Rides"
- tapOn: "Ride again"
- extendedWaitUntil:
    visible: "Go"
    timeout: 15000
- assertVisible: "BRIDGE · WESTBOUND · NORTH SIDE"
- takeScreenshot: shot-17-ride-again-light
"""

    # Dark look: the Steel Bridge lower deck, eastbound.
    s0, s1 = line.span("steel", "west")
    start, end = s0 - 200, s1 + 60
    over_steel = [line.at(start + i * STEP_M) for i in range(int((end - start) / STEP_M) + 1)]
    dark = f"""# Release 0.3 in the dark look (C): the same screens as flow 04, over the Steel Bridge lower deck.
# Screenshots are uploaded by CI so both looks can be reviewed without a phone.
# The GPS points are made by tools/build_ride_flows.py from the ride file; do not edit them by hand.
appId: pdx.bridgeride.app
---
- launchApp:
    clearState: true
- extendedWaitUntil:
    visible: "Map, 28 bridge ends, light"
    timeout: 30000
- tapOn: "Settings"
- tapOn: "Dark"
- tapOn: "Map"
- extendedWaitUntil:
    visible: "Map, 28 bridge ends, dark"
    timeout: 30000
{location(over_steel[0])}- tapOn: "Plan a ride"
- takeScreenshot: shot-18-plan-list-dark
- tapOn: "Hawthorne out, Steel back"
- waitForAnimationToEnd:
    timeout: 5000
- takeScreenshot: shot-19-plan-ride-dark
- tapOn: "Go"
{PERMISSIONS}- extendedWaitUntil:
    visible: "Steel Bridge"
    timeout: 30000
- assertVisible: "Eastbound, south side"
- assertVisible: "Bike marker.*"
- waitForAnimationToEnd:
    timeout: 5000
- takeScreenshot: shot-20-ride-next-bridge-dark
{travel(over_steel, 20)}- extendedWaitUntil:
    visible: "to the finish"
    timeout: 20000
- waitForAnimationToEnd:
    timeout: 3000
- takeScreenshot: shot-21-ride-after-bridge-dark
- tapOn: "Lock"
- takeScreenshot: shot-22-ride-locked-dark
- longPressOn: "Unlock"
- extendedWaitUntil:
    notVisible: "Unlock"
    timeout: 10000
- longPressOn: "End"
- extendedWaitUntil:
    visible: "RIDE SAVED"
    timeout: 15000
- assertVisible: "eastbound"
- assertVisible: "Passport 1 of 12"
- takeScreenshot: shot-23-summary-dark
- scrollUntilVisible:
    element: "Done"
    direction: DOWN
- tapOn: "Done"
- tapOn: "Library"
- tapOn: "Passport"
- assertVisible: "Steel, stamped, crossed 1 time"
- takeScreenshot: shot-24-passport-dark
- tapOn: "Steel, stamped, crossed 1 time"
- assertVisible: "Steel Bridge"
- takeScreenshot: shot-25-bridge-sheet-dark
- tapOn: "Close bridge details"
"""
    (OUT / "04-follow-a-line.yaml").write_text(light, encoding="utf-8")
    (OUT / "05-ride-dark.yaml").write_text(dark, encoding="utf-8")
    print(f"after the Steel: {line.cum[-1] - end:.0f} m of line left; Hawthorne span {h0:.0f} to {h1:.0f} m, Steel {s0:.0f} to {s1:.0f} m")
    print(f"wrote 04-follow-a-line.yaml ({len(over_hawthorne)} points over the Hawthorne) and 05-ride-dark.yaml ({len(over_steel)} over the Steel)")


if __name__ == "__main__":
    main()
