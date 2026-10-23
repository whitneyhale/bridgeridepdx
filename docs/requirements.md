# Requirements

## Goals

- Free route creation and saving. Core route planning never goes behind a subscription.
- Greenway priority in routing.
- Custom routes and a custom route preference setting.
- Directions, tracking, route sharing.

## Bridge model

- A named bridge is a directional crossing (eastbound or westbound), not a single point.
- Users can refer to a bridge by colloquial name, misspelling, or color. All resolve to the same bridge.

## Safety (top priority)

- Cars are routed onto car-heavy roads, bikes onto bike-heavy roads.
- Car routing stays away from bike-heavy routes.

## GPS tracking

- Must handle momentary GPS drops and low-confidence position.
- Use heading (which way the rider is facing), not position alone. Tracking works much better with heading.

## Test targets

- Routing efficiency.
- Routing memory usage.
- Routing realism, checked against Portland's official green and bike routes.
- GPS tracking through signal drops and uncertain position.

## Later ideas

- Themed rides (for example, a pizza ride). Core route planning stays free.

## Open decisions

- Native app vs. installable web app (PWA). The starter is a web page so it can be tested on a phone right away.
- Routing engine (self-hosted vs. a hosted service) and bike-aware data source.
