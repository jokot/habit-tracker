# Claude Design ask: WantTimer UX

Refresh the WantTimer surfaces + add the live-countdown notification visual. **Replace the existing design** for these areas — don't keep old variants around as alternates.

## Existing canvas to UPDATE / REPLACE

- `want-timer` DCSection (`canvas.html:424`) — full-bleed dedicated timer screen. **Replace existing content** with the new running + orphan states below.
- `WantTimer` component (`screens.jsx:1111`) — existing layout. **Rewrite** to match the new full-screen spec below.
- Want detail screen — currently has HeroCard + Start-timer button. **Replace the timer-related footer** with the 4 states below.
- `notifications` DCSection — keep all existing notification types. **Add** the new `want_timer_running` mock alongside `want_timer_end`. **Verify / refresh** `want_timer_end` copy if outdated.

Old variants of the want-timer screen, old inline-banner mockups, and any prior `want_timer_running` sketch should be **removed**. Single canonical design only.

---

## Want detail — 4 timer states

Render all four states. HeroCard stays unchanged on top; the section below it changes per state.

1. **Idle, `unit == "min"`** — "Start timer" tonal button. Tap → duration bottom sheet (5 / 10 / 15 / 20 / 30 / 60 min chips). Keep existing duration sheet visual if present; otherwise add it.
2. **Idle, `unit != "min"`** — **Start-timer button hidden.** Layout flows HeroCard → directly into Recent activity. Show this empty footer explicitly so the hide is unambiguous.
3. **Active timer for THIS want** — Replace Start-timer with a banner: `Timer running · MM:SS` + Cancel pill. **Whole banner body is tappable** → opens full-screen WantTimer. Cancel pill is its own tap target → cancel + partial-log. Make the two tap zones visually distinct (row body vs trailing pill).
4. **Active timer for ANOTHER want** — Start-timer button shown normally. Tap → confirm-replace dialog (below).

### Confirm-replace dialog

- Title: `Replace running timer?`
- Body line 1: `You have a {minutesLeft} min timer for {otherWantName}.`
- Body line 2 (variant a, `elapsedMin >= 1`): `Starting a new one will log {elapsedMin} min and end it.`
- Body line 2 (variant b, `elapsedMin == 0`): `Starting a new one will discard it.`
- Buttons: primary `Replace`, neutral `Keep`.
- Render both body variants.

---

## Full-screen WantTimer — "LIVE COST" hero

Full-bleed, no app-chrome scaffold competing. Two states:

1. **Running** — Hero contains:
   - Large MM:SS countdown, center.
   - Points-spent-so-far counter (updates each minute).
   - Optional progress ring/arc around the countdown.
   - Want name + small icon above hero.
   - Prominent `Cancel` CTA (full-width pill or large bottom button) — triggers cancel + partial-log.
   - Top-left back arrow (returns to Want detail, timer keeps running).
2. **Orphan / empty** — User landed here but no timer is running (race or stale notification tap). Empty state copy: `No timer running` + Back button. No crash, no auto-redirect.

Annotate entry points on the canvas (not in the screen itself):
- Auto-open after duration pick on Want detail.
- Tap the running banner on Want detail.
- Tap the live notification in the shade.

---

## Live-countdown notification (NEW)

Service-internal foreground notification — required by Android while the timer service runs. **Not user-toggleable in the app's notification settings.** User mutes only via Android's per-channel system settings.

Channel: `want_timer_running` (LOW importance, no sound, no badge).

### Compact view

- Small icon: app notification icon.
- Title: `{Want name} timer` (e.g. "Scroll feed timer").
- Body: `X min left · −Y pt spent` — `X` = minutes left rounded up, `Y` = running points counter, minute-granular.

### Expanded view

- Same title + body.
- Determinate progress bar showing `elapsedMin / totalMin`.
- Action: `Cancel` only (single button — no Pause, no Snooze).
- Tap target (whole notification body): opens full-screen WantTimer.

### Catalog placement

Place adjacent to `want_timer_end` in the `notifications` catalog mock, but visually tag it as **"system-internal, not user-toggleable"** (e.g. badge/strip/border treatment) so the distinction from the user-facing types is immediate.

---

## End-of-timer notification — verify

Already exists as `want_timer_end`. Confirm body copy reads:
- Min-unit wants: `{Want name} timer finished · {N} min logged · −{pt} pt`
- Non-min wants: `{Want name} timer finished`

Tap target: Want detail (NOT full-screen — timer is done).

---

## Constraints

- Material 3 + existing Compose tokens. Match the design vocabulary used in the rest of the canvas (cards, pills, banner shapes, typography scale).
- Android only.
- Notification channel topology stays unchanged: 4 grouped channels (`reminder` / `alert` / `status` / `system`) + 2 timer channels (`want_timer_running` LOW + `want_timer_end` HIGH).
- Notification catalog stays at the existing user-facing count. `want_timer_running` is service-internal, not a catalog entry.

---

## Deliverables

1. Updated `WantDetail` canvas section showing 4 timer states + confirm-replace dialog.
2. Updated `want-timer` canvas section with both Running + Orphan states + entry-point annotations.
3. New `want_timer_running` notification mock (compact + expanded) tagged service-internal, placed in the `notifications` catalog adjacent to `want_timer_end`.
4. Confirmed / refreshed `want_timer_end` copy.

Replace any prior design for the above. No legacy variants kept.

---

## Out of scope

- Pause / resume controls.
- Snooze action on notifications.
- Concurrent timers (one per want simultaneously).
- iOS layouts.
- Widgets.
- Sound / vibrate customization UI.
- Cross-device timer sync.
- Auto-target adjustment features (separate future work).
