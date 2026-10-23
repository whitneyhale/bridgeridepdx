# Content

Readable text for people, kept separate from the data the router uses.

- `bridges/<id>.md`: one description per bridge. The `id` must match `data/bridges.json`.
- `bridges/_template.md`: copy this to start a new one.
- Build: `python tools/build_descriptions.py` checks every file and writes `data/descriptions.json`.

Rules (docs/routing-rules.md, D-07):

- Facts like GPS points, category, and lane sides come from `data/bridges.json`. Do not repeat them here.
- `summary` is one sentence, 140 characters or fewer. The app shows it on the compact card.
- List a source for every fact. Write in your own words; do not paste Wikipedia text.
- `status: draft` until Whitney has read it, then `reviewed`.

Written so far: Hawthorne, Tilikum, Blumenauer.
