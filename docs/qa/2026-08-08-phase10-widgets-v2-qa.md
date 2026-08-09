# Phase 10 (revised) — Widget manual QA

Device: ______  Android version: ______  Date: ______

## Pinning
- [ ] All seven widgets appear in the launcher's widget picker with their descriptions:
      `Point balance`, `Quick log — list`, `Quick log — grid`, `Streak history`,
      `Quick log — list only`, `Quick log — grid only`, `Streak history — grid only`
- [ ] Each pins at its default size and renders without a blank frame
- [ ] Each resizes between min and expanded, and the layout changes rather than scaling

All seven use `SizeMode.Exact`, so every layout number below is computed from the real
widget size. Resize each widget through several sizes and confirm the content re-flows
to fill the frame — content that stays in the top-left with dead space below is the
Responsive-mode bug this replaced.

## Widget 1 — Balance
- [ ] 1×1: numeral **and** flame, no `pts` — `pts` drops below 90dp, the flame never drops
- [ ] 2×2: numeral + `pts` + flame, centred, numeral noticeably larger than at 1×1
- [ ] 4×4: numeral at its largest, still centred
- [ ] Zero balance renders `0 pts`, not an empty state
- [ ] Tap anywhere opens the app
- [ ] Dark mode

## Widget 2 — Quick log (list)
- [ ] Every active habit and every non-hidden want appears — scroll to the end and
      compare against Home and the Want list. Nothing is truncated to a slot count.
- [ ] The list fills the widget height at every size; no dead space under the last row
- [ ] Divider sits between the last habit and the first want
- [ ] Tapping a habit row logs exactly one point (not the full daily target) — verify against Home
- [ ] Tapping an instant want (`unit != "min"`) spends one point
- [ ] Tapping a minute want opens the want-timer screen for that want
- [ ] At zero balance every want reads unavailable and no want tap spends
- [ ] No habits and no wants → "No habits yet — open app"
- [ ] Dark mode

## Widget 3 — Quick log (grid)
- [ ] Tiles are square and separated by a visible gutter at every size
- [ ] Each tile shows the item's icon — the same glyph Home shows for that habit or want —
      on a green circle for habits and a red circle for wants
- [ ] Column count grows with width (2–5), row count with height; the grid spans the frame
- [ ] 2×3 shows **6 tiles** (2 columns × 3 rows), not 4
- [ ] Labels drop out below a 62dp tile, captions below 80dp — a small tile is icon-only
- [ ] Balance header appears once the widget is at least 220dp tall, not below
- [ ] Same tap behaviors as widget 2
- [ ] Dark mode

## Widget 4 — Streak
- [ ] 2×2 is the smallest pinnable size, and it shows a **5×5** grid
- [ ] Cells are large enough to read and the grid spans the full widget width
- [ ] Column count grows with width from a floor of 5; row count grows with height, up to
      120 days of history
- [ ] Header ("🔥 N day streak") appears at 140dp tall and above
- [ ] Cell colors match the in-app streak history for the same days
- [ ] No history → 60 grey `HeatL0` cells, NOT "Start a streak". `ComputeStreakUseCase.computeNow`
      always returns a full 60-day list — missing history comes back as `EMPTY`-state days, not a
      shorter list — so the empty state is effectively unreachable in normal use. A fresh install
      showing a full grey grid is expected, not a bug to file.
- [ ] Dark mode

## Refresh model — the part most likely to be wrong
- [ ] Tap the same habit row on the widget three times in a row. The balance and the
      progress text must move on **every** tap, not only the first. If a later tap does
      nothing, capture `adb logcat -s GlanceAppWidget:* Glance:*` across the taps — the
      per-widget `runCatching` in `WidgetUpdates` now keeps one widget's failure from
      swallowing the other six, so a stuck update should surface in the log rather than
      failing silently.
- [ ] Pin all seven widgets at once and repeat the tap test — the fan-out is sequential
- [ ] Log a habit in-app; every pinned widget updates without waiting
- [ ] Log a want in-app; every pinned widget updates without waiting
- [ ] Start a want timer; the balance on the widgets drains once per minute while it runs
- [ ] Cancel a timer; widgets reflect the partial spend
- [ ] Force-stop the app, change nothing, wait: widgets reconcile within 30 minutes
- [ ] Cross midnight with a widget pinned: habit progress resets
- [ ] `WidgetRefresher` retries its Flow chain after a 5-second delay instead of letting
      an exception escape into `GlobalScope` and kill the process. There is no easy way
      to force a refresher failure on a device — treat this as code-verified, not
      device-verified, and do not spend time hunting for a way to trigger it here.

## Widgets 5–7 — the "plain" trio
Same three layouts with the headers removed: `Quick log — list only`, `Quick log — grid only`,
`Streak history — grid only`. They are separate entries in the picker, not a setting.
- [ ] Each shows the same content as its headed twin with no balance line and no streak line
- [ ] The content grows into the space the header vacated — at the same widget size, the
      plain grid fits at least as many tiles as the headed one, never fewer
- [ ] Taps behave identically to the headed versions
- [ ] Pinning a plain widget and a headed widget at once: both update on the same tap
- [ ] Dark mode

## Known deviations from the mockup — verify they look acceptable, do not "fix"
- [ ] Icons in the grid are rasterised from the app's ImageVector icons at 84×84 and tinted,
      so they are flat single-colour glyphs — no gradients, no strokes. Compare against Home:
      the same habit or want should be recognisably the same glyph.
- [ ] The list widget still has no icons; only the grid draws them.
- [ ] Corners are square below API 31 (`cornerRadius` is API 31+). Check on the oldest
      device available.
- [ ] Disabled wants are dimmed by color, not opacity — Glance has no opacity modifier.
- [ ] Every want reads `−1 pt`; there is no per-want cost in the domain model. The
      differentiator shown is the rate (`5 min`, `1 cup`).
- [ ] Items appear in the app's own order, not most-used first. Confirm the widgets show the
      same habits and wants as the Home screen and the Want list, in the same order. The spec
      asked for "most-used, stable ordering", but the domain layer has no usage count and
      neither the habit nor the want-activity query carries an `ORDER BY`, so both return
      insertion order. This is a known, accepted deviation from the spec — record it as such,
      not as a bug.
- [ ] Widget 3 still truncates: a grid only holds `columns × rows` tiles, so a long want list
      is cut at the frame. Widget 2 scrolls and shows everything. That difference is deliberate.

## Behavior change from v1 to confirm deliberately
- [ ] A widget habit tap logs one point. v1 logged the full daily target in one tap.
