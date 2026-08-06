# Habitto — Design Follow-up: Home-screen timer banner

Add a live timer banner to the Home screen. Right now a running Want timer is only visible on that want's own detail screen — if the user navigates away to Home, there's no indication a timer is still running/spending points. Keep tone and design system unchanged; this is a new element, not a rework of existing Home sections.

---

## Why

Want timers run in the background (foreground service) independent of what screen the user is on. A user can start a timer on Want detail, hit back, browse Home, and lose all visibility that points are actively draining. They shouldn't have to remember which want they started a timer on and navigate back to it just to check status or cancel.

## Existing reference

There's no canvas/Figma source available for this (prior canvas export is gone). Use the already-shipped **Want detail running-timer banner** as the visual/interaction reference — same component family, just relocated to Home:

- Full-bleed pill, `primaryContainer` background.
- Left zone (tappable, majority of width): clock icon in a circle, "Timer running" label, live `MM:SS` countdown, chevron.
- Right zone (separate tappable area, same background — no distinct color block): stop icon + "Cancel" label in `error` red, for contrast without a jarring color split.
- Whole left zone tap → opens the full-screen timer. Right zone tap → cancel + partial-log, independent of the left tap target.

Screenshot of the current Want-detail version (for exact color/spacing reference) — pull from `WantDetailScreen.kt`, `ActiveThisWantBanner` composable.

## What's different on Home vs Want detail

On Want detail the want is implied by the screen you're on — the banner doesn't need to name it. On Home it must, since the user could be looking at any part of their day:

- Add the want's name + icon to the banner (e.g. "TikTok · Timer running").
- Everything else (MM:SS, cancel zone, tap targets) stays the same pattern.

## Placement in the Home layout

Home is a single scrolling list (top→bottom, current order): sticky top bar → optional rate-ladder migration banner → identity strip → daily status card → today's habits → today's wants.

Decide and show:
- Where does this banner sit relative to the above? (Leading candidate: directly under the sticky top bar, above the rate-ladder banner, since "money is currently draining" is more urgent than a migration notice — but make the call and justify it.)
- Does it scroll with the list, or float/stick like the top bar?
- What happens when the rate-ladder banner AND the timer banner are both visible at once — stack order, spacing?

## States

- **No active timer** — banner doesn't render at all (not collapsed/hidden, just absent — zero layout cost).
- **Active timer, any want** — banner shows as described above. This is the only non-empty state; there's no "orphan"/error state here (that's handled on the full-screen timer screen).

## Deliverables

1. Home screen mockup with the banner in place, light + dark.
2. Exact placement/ordering relative to existing Home sections, with reasoning.
3. Spacing/elevation treatment if it visually competes with the sticky top bar.
4. Confirm cancel-zone tap target sizing/hit area at the Home width (banner will be full-bleed like other Home sections, wider than the Want-detail version which sits inside a padded column).

Reuse existing tokens/colors from the Want-detail banner — don't invent a new palette for this.
