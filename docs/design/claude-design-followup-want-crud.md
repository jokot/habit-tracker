# Claude Design follow-up — Want CRUD + ExchangeRate sync

Source canvas under review: `screens-v2.jsx` (post-cleanup pass).

## What's already designed (good — no rework needed)

- `WantList` — seeded vs custom split, "Add want" CTA, empty state, trailing icon hint (`visibility_off` for seeded, `edit` for custom).
- `WantDetail` — hero card (icon, name, Seeded badge, cost, total spent, times logged), Start timer + Edit row, Recent activity timeline grouped by day, Hide (seeded) / Delete (custom) action with explanatory caption.
- `WantForm` — name + icon picker, unit chip row (min/serving/match/episode/session/item), cost stepper with live preview (`e.g. 30 min = X pt`), no-identity info banner, Delete in edit mode.

Decisions locked in by canvas: seeded→hide / custom→delete; no identity link; cost stepper not free-form input; past logs preserved on hide/delete.

## Gaps / questions for next design pass

### 1. Hidden-want restoration flow

`WantList` only renders visible wants. After a user taps `visibility_off` on a seeded want, where do they go to find + restore it?

Options to mock:
- **A.** Trailing app-bar `more_vert` menu on `WantList` → "Show hidden (N)" → expands a third "Hidden" section under Seeded + Custom.
- **B.** Bottom of list: collapsed "Hidden · N" row that expands inline.
- **C.** Separate `WantHidden` screen reachable via app-bar action.

Prefer A — keeps everything in one place; matches Settings-style trailing menus elsewhere in the app.

### 2. Cost stepper increment + fractional support

Stepper +/- buttons drawn in `WantForm` but the step size isn't specified. Need explicit:

- Step size for +/- taps: **0.1** (so user can express 0.5 pt/min Twitter, 0.1 pt/min YouTube).
- Long-press on +/-: jump in **1.0** increments.
- Min: 0.0 (FREE — already supported).
- Max: 999 (sanity cap).
- Tap the big `−1.0` numeral to enter free-form decimal via keyboard (escape hatch).

Mock the long-press affordance + keyboard-input variant. Both should preserve the live preview line (`e.g. 30 min = X pt`).

### 3. "Start timer" button on `WantDetail`

Want timer is a separate, deferred feature surface — not part of the Want CRUD flow.

Options to mock:
- **A.** Hide the "Start timer" button entirely; CRUD ships clean.
- **B.** Show but disabled, with caption "Coming soon".
- **C.** Combine timer + CRUD into a single deliverable.

Prefer A — clearest separation; B clutters; C bloats the surface. Show the "without timer" variant in `WantDetail`.

### 4. Today → Want Detail navigation gesture

IA diagram says "Today screen → tap want row → Want detail (one tap)" but `Today`'s established want-row interaction is `tap = spend (3-sec commit)`. Conflict.

Options to mock:
- **A.** Long-press the want row to open `WantDetail` (mirrors `Habit row long-press → Habit detail` already drawn at line 1730 of canvas).
- **B.** Tiny chevron-right icon on each row → tap to detail; existing tap-zone stays as spend.
- **C.** Drop the Today→Detail one-tap path entirely; user reaches `WantDetail` only via You hub → WantList → row.

Prefer A — consistent with habit row interaction, no extra UI clutter.

Update IA diagram + add explicit annotation on `Today` artboard: "Long-press want row → Want detail".

### 5. Cost-edit retroactive behavior — UI affordance

Editing a want's `costPerUnit` could either rewrite past spend points or leave them frozen. Two semantics to mock:

- **Recompute:** past-day balance + Recent activity timeline costs reflect new rate. User edits cost from 1.0 → 2.0 → yesterday's "−12 pt" entry becomes "−24 pt".
- **Snapshot:** WantLog stores cost-at-log-time. Edits affect future logs only. Past entries unchanged.

UX implication for `WantForm`:
- If Recompute: show warning banner when editing cost for a non-fresh want — "Editing this cost rewrites your spend history."
- If Snapshot: no warning needed; past entries naturally frozen.

Mock both variants. Final pick is an engineering decision but the UI cost differs between them.

### 6. Seeded WANTS list alignment

Canvas `WANTS` constant in `shared.jsx` defines the seeded set used by all designs. The app's onboarding seeder may carry a different list.

Action: dump the canvas `WANTS` array (id/name/unit/cost) into this doc so engineering can reconcile. Decide which list is canonical.

```
WANTS = [
  // paste current canvas array here when responding
]
```

### 7. WantList row affordance — quick-action vs row-tap

Trailing icon on each row is `visibility_off` (seeded) or `edit` (custom). Unclear whether:

- Tap row = open `WantDetail`; trailing icon = quick-action (hide / quick-edit).
- Or tap row = trailing-icon-action; long-press = open detail.

Prefer first interpretation. Annotate on `WantList` artboard:
- Row body tap → `WantDetail`.
- Trailing `visibility_off` tap → toggle hide (snackbar with Undo).
- Trailing `edit` tap → open `WantForm` in edit mode.

### 8. ExchangeRate screen — comparison-row semantics

`ExchangeRateScreen` comparison rows currently display raw cost-per-unit at 1-decimal precision (`1 → 1.4 pt`). For wants priced below ~0.71 pt/unit this hides the actual per-tap charge from the user — every tap floors to 1 pt due to the anti-cheat min-1 rule, but the row says "0.1 → 0.1 pt" or similar, making the rate effect invisible and confusing.

Update each row to display **per-tap actual deduction**:
- Strikethrough `pointsSpent(1, baseCost)` (base per-tap, integer, min 1 if cost > 0) → bold `pointsSpentWithRate(1, baseCost, rate)` (current per-tap, integer).
- Suffix: ` pt / {unit}` (e.g. `pt / minute`).
- Skip the arrow + strikethrough when base and current per-tap are equal.
- Show **FREE** label when `costPerUnit == 0.0` exactly (skip the number row entirely).

This matches `WantList` row semantics and the live deduction the user actually sees on `Today` taps.

### 9. ExchangeRate screen — hide-aware filtering + custom support

Once seeded wants can be hidden (per section 1), `ExchangeRateScreen` comparison list should:

- Hide hidden wants from the list (consistent with `Today` screen).
- Show custom wants inline with seeded ones (no separate section).
- Sort: alphabetical by name, or by current per-tap cost descending — pick one.
- Optional caption under section title: "Visible wants only."

### 10. ExchangeRate hero subtitle polish

Current copy: `"You're at Tier N of 5. M days to Y.Y×."` and `"Top tier reached."` for top.

Optional refinement: `"M days to Tier {N+1}"` so users see the next tier's number explicitly rather than its multiplier. Mock both variants — pick after viewing.

Confirm hero math reads correctly when streak survives a freeze day mid-week (i.e., subtitle should reflect the streak the engine actually counts, not the count of consecutive logged days).

## Out of scope for this design pass

- WantTimer artboards (separate surface).
- Per-tap rate-multiplied cost display on `WantList` rows (already covered by `ExchangeRate` comparison rows per sections 8–9).
- WantLog schema decisions (cost-snapshot vs recompute is documented in section 5; final pick lives in engineering spec, not canvas).
