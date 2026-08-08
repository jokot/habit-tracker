# Habitto — Design Follow-up: Home-screen widgets

Rework the canvas's **Widgets** section into four separate, independently-pinnable Android home-screen widgets, and add the Wants capability the current design is missing entirely. Keep tone and design system unchanged; this extends the existing widget work rather than reworking the app's screens.

---

## Why

The canvas currently has one `Widgets({ dark, size })` component in `screens.jsx` drawing two cards: a balance + quick-log grid, and a streak grid. Two gaps:

1. **Wants are absent.** The widget shows only habits and balance. But the core loop is two-sided — habits earn points, wants spend them. A widget that only surfaces the earning half hides the reason points matter.
2. **Only one quick-log layout exists.** The current card is icon-grid only. A row/list layout reads better for users with few habits and longer names; the grid wins for many items. Both should exist as separate pinnable widgets so the user picks.

Splitting into four also lets users pin only what they want — someone who only cares about the number shouldn't have to pin a card with a heat grid attached.

## Existing reference

Everything is on the canvas already — reuse it, don't invent parallel data or components:

- `HABITS` — `{id, name, icon, identity, identityIds, threshold, target, unit}`
- `WANTS` — `{id, name, icon, cost, unit, seeded}`
- Helpers from `shared.jsx`: `habitHue(habit)`, `HabitGlyph`, `HabitRing`, `HeatCell`, `buildHistory(preset)`, `classForDay(d)`, `Icon`
- Tokens from `tokens.css`: the `--m-*` colors and `t-*` type classes

The existing `Widgets` component is the visual starting point for Widgets 3 and 4 — surface cards at radius 24, 1px `--m-outline-var` border, `t-numeral` for the big number, flame icon in `--m-flame` for streak.

## The four widgets

**Widget 1 — Balance.** Point balance only, at a glance. Big numeral, `pts` unit, streak flame + count as a secondary accent. Whole surface taps through to the app. The "I just want the number" widget.

**Widget 2 — Balance + quick log (LIST).** Balance header, then a scrollable vertical list of rows: icon glyph, name, progress or cost, tap-to-log affordance. Must show **both habits and wants** — decide and show how they're visually separated (section headers? divider? inline badge?), and how a want row reads differently from a habit row. Show ~4–6 rows.

**Widget 3 — Balance + quick log (GRID).** Same data as Widget 2, icon tiles instead of rows — evolves the existing card. Must include **wants alongside habits**. Denser, icon-led, name optional. Show how a want tile differs from a habit tile at tile size, where there's no room for much text.

**Widget 4 — Streak grid.** Keep the current streak card largely as-is (streak count, flame, heat grid via `classForDay`/`buildHistory`), but as a standalone widget with its own sizing.

## The Wants problem

This is the actual new design work. Wants are not just "more habits":

1. **Direction is opposite.** Habits earn (`+`), wants cost (`−`). A habit tap is always safe; a want tap *spends*. Make that unmistakable at a glance — a user must never fat-finger a spend thinking it was an earn.
2. **A want tap starts a timer**, it doesn't log instantly. Most wants are `unit: 'minutes'` with a per-minute `cost`, so tapping "TikTok" starts a running want-timer that drains points in real time.
3. Therefore the widget needs an **active-timer state**: which want is running, elapsed/remaining, points draining, and a stop affordance. Design this for both the list and grid variants.
4. **Affordability.** If the balance can't cover a want, its tile/row should read as unavailable.
5. **Volume.** There are 14 seeded wants. Widgets can't show all of them. Decide what's shown — most-used? a fixed few? — and show the reasoning in the mockup.

## States

Draw each, per widget where applicable:

- Populated (default)
- Empty — no habits yet, no wants yet
- Want timer running
- Insufficient balance for a want
- Dark mode (all four)

## Sizing

Android widgets are sized in host grid cells, not free pixels. Draw each at its **minimum** and one **expanded** size:

| Widget | Min | Expanded |
|---|---|---|
| 1 Balance | 4×1 (~250×40dp) | 4×2 |
| 2 List | 4×2 (~250×110dp) | 4×4 (~250×250dp) |
| 3 Grid | 4×2 | 4×4 |
| 4 Streak | 4×2 | 4×3 |

Show what content drops or reflows between min and expanded — don't just scale it.

## Implementation constraints

These are real platform limits, not preferences. This ships in **Jetpack Glance**, not Compose or CSS, and the current mockup uses several things Glance can't render:

- **No CSS gradients** as widget backgrounds — flat color or a drawable only.
- **No `aspectRatio`** — cells need explicit dp width and height.
- **No `oklch()`** — colors must resolve to fixed hex or theme color providers. The `habitHue`-driven `oklch(0.45 0.12 …)` tints need a discrete palette instead.
- **No lazy grid.** Glance has `LazyColumn` but no `LazyVerticalGrid`, so a grid must be a fixed number of `Row`s of `Column`s. Widget 3's grid is a **fixed** N×M, not flowing or scrolling.
- **Tap targets ≥ 48dp**, and each tappable region must be a rectangle.
- **No animations, no transitions** — every state is a static render.
- Rounded corners and elevation are limited; keep the visual language achievable with flat surfaces, borders, and radius.

If a design idea genuinely needs something above, call it out explicitly rather than drawing it silently.

## Deliverables

1. Four components — `WidgetBalance`, `WidgetQuickLogList`, `WidgetQuickLogGrid`, `WidgetStreak` — each taking `{ dark, size, state }`, in `screens.jsx` or a new `screens-v5.jsx` (matching how v2/v3/v4 extend the project).
2. Registered on `window` alongside the existing exports.
3. A canvas section titled **"Home-screen widgets"** so they render on the board.
4. Habit-vs-want visual distinction, stated and justified, at both row size and tile size.
5. The active-timer state for list and grid, with its stop affordance.
6. Shown on a home-screen-like backdrop — the existing gradient backdrop is fine for *presentation*; the no-gradient constraint applies to the widget surface itself, not the wallpaper behind it.
