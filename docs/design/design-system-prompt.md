# Prompt: Bridge Ride PDX design system

Paste this into a new chat in the BridgeRidePDX project. It tells the AI what to build, what to read first, and when to stop and ask.

---

You are building the design system for Bridge Ride PDX: a free Android cycling route planner for Portland, built around the Willamette bridges. Themes: navigation, Portland bridges, cycling.

## Rules

1. Do not guess. If the repo does not say it, list it as a question and ask me before building on it.
2. Ask first, in one round: at most 8 multiple-choice questions, each with your recommendation and one line of reasoning.
3. Keep every file short. Lead with the fact. No intros, no marketing language.
4. Do not change anything already decided (listed under "Already decided"). If you think something should change, ask.
5. Every color pair gets a checked contrast ratio. Target: 4.5:1 for text, 3:1 for icons and route lines. Test in direct sunlight conditions (high-contrast variant).

## Read first (repo whitneyhale/bridgeridepdx)

- `README.md`, `docs/requirements.md`, `docs/app-plan.md`, `docs/prototype-spec.md`
- `docs/design/README.md` and the eight `*.dc.html` screen drafts (first drafts, not yet reviewed by me)
- `docs/routing-rules.md` (rule IDs are cited by the screens)
- `data/bridge_names.json` (bridge names, nicknames, colors) and `content/bridges/*.md` (voice sample)

## Research (web search, cite sources)

- Metro Bike There: the six route comfort categories and its map colors. I may map my route-type colors to them.
- Current bike and transit wayfinding sign conventions (the "Wayfinding" look is already chosen).
- Current navigation UI patterns for cycling apps (next-turn banner, off-route, GPS-lost states) and Android Material guidance for large touch targets, bottom sheets, and navigation bars.
- Outdoor legibility: glare, gloves, one-handed use, glance time while moving.

## Skills and tools to use

`design:design-system`, `design-skills:design-systems`, `design-skills:accessibility-audit`, `design-skills:ux-writing`, `mapmap:mapmap-map-design` (map styling), `frontend-design`. Use web search for the research above. Render screens to PNG and open them to check before reporting.

If a Figma tool or an image-generation tool is available in that chat, use it. If not, say so and produce SVG and HTML instead. Real photos of the bridges are for checking shape and color only: cite the page, do not copy or embed them.

## Already decided

- Look: "Wayfinding", after bike and transit route signs.
- Fonts: Barlow Semi Condensed (headings, signs), Inter (text).
- Colors: ink #15211D, ground #F3F5F2, greenway green #1B6B45, river blue #1F5F8B, signal orange #B4480F (warnings only).
- Touch targets 44 px or larger.
- Phone: Samsung Galaxy S24 Ultra (390 x 844 design frame). PC prototype page also exists.
- Screens: Map (idle), Plan, Ride, Bridge sheet, Library, Settings, First launch, PC Plan.
- A bridge is a directional crossing (eastbound or westbound), not a point. Lane side is shown.
- Five stop types: Stop, Pass by, Road, Bridge, Destination.
- Placeholders in [square brackets] are values the app does not compute yet. Keep them.
- Core planning stays free. No accounts, no social features, no themed rides in this version.
- Dark mode: yes, light and dark.
- Theme: roses (Portland, City of Roses), bikes, bridges, dark greens, dark reds, Willamette blue, and a larger, bluer Columbia. Proposed palette additions: Forest #0F3D2B, Rose #9B1B30, Columbia #1769B8 (dark mode set in `palette.json`).
- Icons: cartoon-simple, one per bridge (all 14 in `data/bridges.json`), plus rose, bicycle, and a stag head. The stag is an original drawing inspired by Portland's white stag; do not copy the sign or its lettering.
- Each bridge has a `geometry` entry in `data/geometry.json`: what it looks like, drawing rules, AI prompt, sources, and what still needs a photo check.
- Layers: layer 1 is the first pass (96 x 48 icons, silhouette rules). Layer 2 adds named parts, proportions, and details (192 x 96 icons, light and dark). Add layers instead of replacing earlier ones.
- River view: bridges are shown from the south looking north, centered on the Willamette, nearest bridge lowest, West left and East right. The Columbia is a wider, bluer band across the top. Spacing is even, not to scale.
- Style: cute and chunky. Rounded shapes, filled tints, friendly marks (rose, bike with rose basket, stag). Do not draw or imitate the OMSI or PSU logos; if the app ever shows them, use the official files and rules from those organizations.
- Defaults I applied where you gave no preference: use Metro's six Bike There categories for route types (pending the data license check), and plain, direct app voice.
- Rider position marker: a small bike in a disc with a heading wedge, not an arrow (added 2026-10-23).

## Files to generate (folder `docs/design/system/`)

1. `README.md`: index, one line per file.
2. `brand-guidelines.md`: name use, one-sentence description, voice with 5 do and 5 don't examples, how bridge nicknames and colors appear in the UI, logo direction (2 options, with reasons).
3. `colors.md` and `colors.json`: roles (ink, ground, greenway, river, warning, route types, GPS states), light and dark if I choose dark, contrast table, map colors for the six Metro categories if I choose to use them.
4. `typography.md`: scale (size, weight, line height) for sign headings, next-turn text, body, captions. Minimum sizes for riding glance reading.
5. `spacing-shape-motion.md`: spacing scale, radii, elevation, motion rules (reduced motion respected).
6. `icons/`: `icon-spec.md` plus SVG files on a 24 px grid, 2 px stroke. Needed: bridge, lift bridge, lane side left and right, greenway, car-free path, bike lane, shared road, hazard, locate, layers, the five stop types, GPS good, GPS estimated, GPS lost, streetcar tracks, dismount, battery.
7. `components.md`: search bar, chips, bottom sheet, nav bar, next-turn banner, route card, bridge card, stop row, style picker, GPS state pill, warning banner, empty, error, and offline states. Each with states and the touch target size.
8. `screens/`: one PNG and one short description per screen, including these states: GPS dropped 10 seconds, off route, lift warning, offline, location denied, approximate location only.
9. `assets/`: app icon (adaptive, light and dark), splash, map markers, route line styles, bridge illustration style sheet, one cartoon icon per bridge (light and dark versions), rose, bicycle, and stag marks.
10. `ai-image-prompts.md`: instructions for generating illustrations and photos with an AI image tool. Include style rules (flat, sign-like, palette-locked), per-asset prompts (welcome, empty states, store listing), a negative list (no real logos, no real people, no copyrighted characters, no text baked into images, no fake bridge details), aspect ratios, and a check step to compare each result to the palette.
11. `accessibility.md`: contrast results, glare variant, touch targets, voice prompt wording, screen reader labels for map elements.

## Questions to ask me first (do not skip)

Ask these, with your recommendation, before building:

1. Logo: pick A (wheel and rose), B (route shield with bridge), C (stag over bridge), or none. Drafts are in `theme-sheet.png`.
2. Bridge colors that are not verified (see `geometry.json`: Fremont, Steel, Burnside, Morrison, Marquam, Tilikum, Sellwood, Interstate, Glenn Jackson, Blumenauer). Which do you know from riding them?
3. Map base: restyle the OpenStreetMap tiles to match the palette, or keep the default look?
4. Where should AI-generated images appear: welcome and empty states only, or also the store listing?
5. Rose red (#9B1B30) is close in meaning to the orange warning color. Keep orange for warnings only, or move warnings to red?
6. Anything in the eight screen drafts you want changed before the system is built from them?

Then build, render, check, and report in one short message with a list of files.

Before building, run `python tools/check_palette.py` and read `docs/design/palette-report.md`: five color-blind pairs already fail (worst: route blue vs greenway green). Raise them as questions.
