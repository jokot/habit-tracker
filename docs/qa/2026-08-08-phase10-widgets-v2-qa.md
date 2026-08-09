# Phase 10 (revised) — Widget manual QA

Device: ______  Android version: ______  Date: ______

## Pinning
- [ ] All four widgets appear in the launcher's widget picker with their descriptions
- [ ] Each pins at its default size and renders without a blank frame
- [ ] Each resizes between min and expanded, and the layout changes rather than scaling

All four use `SizeMode.Exact`, so every layout number below is computed from the real
widget size. Resize each widget through several sizes and confirm the content re-flows
to fill the frame — content that stays in the top-left with dead space below is the
Responsive-mode bug this replaced.

## Widget 1 — Balance
- [ ] 1×1: numeral only, still legible (`pts` and the flame drop out below 60dp / 90dp)
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
- [ ] Column count grows with width (2–5), row count with height; the grid spans the frame
- [ ] Balance header appears once the widget is at least 170dp tall, not below
- [ ] Same tap behaviors as widget 2
- [ ] Dark mode

## Widget 4 — Streak
- [ ] 2×2 is the smallest pinnable size
- [ ] Cells are large enough to read and the grid spans the full widget width
- [ ] Row count grows with height; a tall widget shows more history, up to 120 days
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
      swallowing the other three, so a stuck update should surface in the log rather than
      failing silently.
- [ ] Pin all four widgets at once and repeat the tap test — the fan-out is sequential
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

## Known deviations from the mockup — verify they look acceptable, do not "fix"
- [ ] No icons anywhere. Glance cannot render the app's ImageVector icons; grid tiles
      show the item name's first letter instead.
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
