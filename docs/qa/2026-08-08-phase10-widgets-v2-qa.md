# Phase 10 (revised) — Widget manual QA

Device: ______  Android version: ______  Date: ______

## Pinning
- [ ] All four widgets appear in the launcher's widget picker with their descriptions
- [ ] Each pins at its default size and renders without a blank frame
- [ ] Each resizes between min and expanded, and the layout changes rather than scaling

## Widget 1 — Balance
- [ ] 2×2: numeral, `pts`, flame stacked; readable at 110dp
- [ ] 4×2: single-row header
- [ ] Zero balance renders `0 pts`, not an empty state
- [ ] Tap anywhere opens the app
- [ ] Dark mode

## Widget 2 — Quick log (list)
- [ ] Min: balance header + habit row(s)
- [ ] Expanded: header, two habit rows, divider, three want rows
- [ ] Tapping a habit row logs exactly one point (not the full daily target) — verify against Home
- [ ] Tapping an instant want (`unit != "min"`) spends one point
- [ ] Tapping a minute want opens the want-timer screen for that want
- [ ] At zero balance every want reads unavailable and no want tap spends
- [ ] No habits and no wants → "No habits yet — open app"
- [ ] The `LazyColumn` has no explicit size modifier. On a real device, check the list
      fills the widget and scrolls as expected at both the min (250×110dp) and expanded
      (250×320dp) sizes, and that the last want row is not clipped at expanded size.
- [ ] Dark mode

## Widget 3 — Quick log (grid)
- [ ] Min: one row of three tiles, no balance header
- [ ] Expanded: header + two rows of three
- [ ] Same tap behaviors as widget 2
- [ ] Dark mode

## Widget 4 — Streak
- [ ] Min: 36 cells at 12 columns
- [ ] Expanded: 60 cells at 15 columns
- [ ] Cell colors match the in-app streak history for the same days
- [ ] No history → 60 grey `HeatL0` cells, NOT "Start a streak". `ComputeStreakUseCase.computeNow`
      always returns a full 60-day list — missing history comes back as `EMPTY`-state days, not a
      shorter list — so the empty state is effectively unreachable in normal use. A fresh install
      showing a full grey grid is expected, not a bug to file.
- [ ] Dark mode

## Refresh model — the part most likely to be wrong
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
- [ ] Items shown are the first N in the app's own order, not the most-used ones. Confirm the
      widgets show the same leading habits and wants as the Home screen and the Want list, in the
      same order. The spec asked for "most-used, stable ordering", but the domain layer has no
      usage count and neither the habit nor the want-activity query carries an `ORDER BY`, so both
      return insertion order and `WidgetItemSelector` takes the first N. This is a known, accepted
      deviation from the spec — record it as such, not as a bug.

## Behavior change from v1 to confirm deliberately
- [ ] A widget habit tap logs one point. v1 logged the full daily target in one tap.
