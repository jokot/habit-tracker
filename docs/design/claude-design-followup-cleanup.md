# Habitto — Design Cleanup & Gap Fills

Update the existing design canvas in place: remove duplicate / superseded artboards, apply the choices below across every screen consistently, and fill the missing screens. Produce a single canonical version of each screen — no duplicates, no contradictions.

Tone, design system, and component primitives stay as already defined in `tokens.css` and `shared.jsx`.

---

## Decisions to apply

### 1. Bottom navigation — 3 items only

Final nav: **Today · Streak · You** (3-item bottom nav).

- "Today" = current Home / daily logging
- "Streak" = streak history
- "You" = identity hub holding everything else (Identity, Manage Habits, Manage Wants, Exchange Rate, Freezes, Settings, Account)

Drop any 4-item variant (e.g. "Home · Streak · Wants · You"). Wants are managed inside the You hub like Habits — they are NOT a top-level destination.

Update every artboard with bottom nav chrome to reflect 3-item version.

**App bar treatment for top-level destinations (Streak, You):** title left-aligned, no back arrow. These are reachable only via bottom-nav tab tap (no push from another screen), so a back affordance is misleading. System back button still dismisses the app.

**Hue-tinted selection states (identity card, etc.):** when a selected card uses an identity-hue background (light tint), the foreground text and subtitle must use a DARK hue-tinted color (low lightness, e.g. `hsl(hue, 55%, 18%)` for title and `hsl(hue, 40%, 30%)` for subtitle), NOT `onSurface` / `onSurfaceVariant`. The bg is the same light tint regardless of light/dark mode, so theme-driven `onSurface` (light text in dark mode) becomes invisible against the light selected bg. Lock contrast to the bg, not to the theme.

**Habit cards support multiple identities.** A habit can serve more than one identity (many-to-many habit↔identity, per §2). Surfaces that render habits must reflect this:
- **Onboarding step 2 (habit picker):** when the user picked multiple identities at step 1, show one merged habit list (union, deduped). For habits recommended by 2+ picked identities, render a small inline label below the habit subtitle: `"Recommended by: Reader · Healthy"`. Single-identity habits show no badge.
- **Today (Home) habit cards:** each card may be associated with 1+ identities. Surface the association — likely as small hue-tinted dots/chips inline with the habit name (one dot per linked identity, using the identity hue). Tap behavior matches the identity strip chips above the cards.
- Layout constraint: identity info is secondary metadata. Don't crowd the habit card. Small, peripheral, never the focal point.

### 2. Multi-identity is the only identity model

The many-to-many habit↔identity model (a user holds multiple identities; each habit can serve one or more) is the canonical version. Single-identity variants are superseded — remove them entirely. Canvas must show one identity model.

Keep these multi-identity surfaces:
- `IdentityList`
- `IdentityDetail`
- `AddIdentityFlow`
- `HabitFormMulti` (multi-select identity field)
- Per-identity notifications copy
- Per-identity exchange-rate breakdown (geometric mean to global)
- Multi-identity onboarding step
- Home with multi-identity chip strip
- You hub identity card (stacked avatars)

Remove from the canvas:
- Any single-identity `IdentityScreen` / `IdentityChangeDialog`
- Any single-select onboarding identity step
- Any single-identity Home chip
- Any older You hub identity card variant
- Any single-identity notifications + exchange-rate variants

If duplicate component names exist (e.g. `Notifications` and `NotificationsV2`), collapse to one canonical name and delete the older.

### 3. Type system stays — Inter + Instrument Serif

Keep as defined in `tokens.css`:
- `Inter` for UI text
- `Instrument Serif` for display numerals (streak count, balance hero, identity stats)
- `JetBrains Mono` for technical readouts where appropriate

### 4. Background palette stays warm

Keep `#FAF8F4` (light bg) / `#121210` (dark bg) and the warm surface ramp.

### 5. Hide unshipped-feature surfaces from Home

Remove from the Home design:
- Freeze inventory chip next to streak number (e.g. "🔥 23 · 🧊 2") — drop the freeze count chip
- Exchange-rate banner above Wants section — drop entirely
- Tap-balance-to-view-exchange-rate affordance — drop
- Per-habit streak micro-chip on each habit card — drop

These features WILL ship later, but Home should not advertise or surface them in the design version we hand to engineering for the first redesign pass. Their dedicated screens (Freezes screen, Exchange rate screen, Habit detail per-habit-streak section) stay in the canvas as separate artboards — but Home does not link to them or show their data.

This is design hygiene only — engineering will leave hooks in code that surface these later. The DESIGN should not show them on Home until the features ship.

### 6. Group artboards by readiness

In the canvas, label every section as one of two groups:
- **`[redesign of existing surface]`** — the screen exists in the live app today; the design replaces what's there
- **`[future surface]`** — the screen describes a feature that hasn't shipped yet

Existing surfaces being redesigned: Onboarding, Auth, Today (Home), Streak history, Settings.

Future surfaces: Identity list / Identity detail / Add identity flow, Habit detail, Habit form / Habit CRUD, Want list / Want detail / Want form / Want CRUD, Freezes screen, Want timer, Exchange rate, Widgets, You hub.

Group sections so all redesigns sit together and all future surfaces sit together — see §9 for the full ordering.

---

## Gaps to fill

### 7. Want list + Want detail + Want CRUD screens

Designer skipped Want management screens. Habits got `HabitCRUD`, `HabitDetail`, `HabitFormMulti` — Wants got nothing equivalent. Wants are NOT the same as habits:

- **Habits** earn points. Fields: name, identityIds, `dailyTarget`, `thresholdPerPoint`, optional icon.
- **Wants** spend points. Fields: name, unit (e.g. "min" / "match" / "serving"), `costPerUnit`, custom-vs-seeded flag (seeded wants can be hidden but not deleted).

Design (mirror the habit pattern):
- **Want list** — list of all user's want activities with edit/hide affordance + "Add want" CTA. Show each row's `costPerUnit · unit` clearly. Distinguish custom vs seeded (different action: delete vs hide).
- **Want detail** — single want zoomed in: name, cost-per-unit, unit, total times logged, total points spent on it, recent log timeline. Edit + delete/hide actions.
- **Want form / Add want sheet** — same modal-bottom-sheet treatment as habit form, but with want fields (name, unit selector, costPerUnit numeric input). NO identity field (wants are not identity-associated).
- **Want timer** — already designed (keep), but the entry point should now be reachable from Want detail.

Place Want list / detail / form artboards in their own canvas section parallel to the Habit section.

### 8. Verify Wants discoverability now that nav is 3-item

With Wants no longer a top-level destination, the user reaches Want management via:
1. Today screen → tap a want row → Want detail (one tap)
2. You hub → "Wants" row → Want list → detail or Add (two taps)

Annotate both paths in the IA diagram. Drop the "Wants" tab variant from the IA sketch.

### 9. Swipe-to-refresh on Today

Today screen supports pull-to-refresh which triggers a manual sync. Currently no design exists for the refresh interaction. Design these states explicitly:

- **Idle (default)** — no indicator visible
- **Pulling (gesture in progress, threshold not yet reached)** — subtle indicator appearing from top, growing/rotating with pull distance, no fixed position yet
- **Released (threshold reached, sync started)** — indicator locks in place at top, spinner active
- **Syncing** — sustained spinner, paired with the existing sync status chip in the top bar transitioning to its `running` state (amber bg, spinner inline)
- **Success** — quick confirmation (chip flips to `synced` blue with checkmark), refresh indicator fades out
- **Error** — chip flips to `error` red, refresh indicator fades out, snackbar / toast surfaces the categorized error label ("No network", "Session expired", etc.)

**Important:**
- The refresh indicator only appears for **manual** pulls. Automatic background syncs (after habit log commit, on app foreground) do NOT show the pull indicator — they only update the top-bar sync chip.
- Indicator should not visually conflict with the multi-identity chip strip below the top bar — design the indicator to overlay or push down.
- Mark this in the Home artboard set as a separate state ("Today · refreshing") in light + dark.

---

## Deliverables

1. **Cleaned canvas.html** with all superseded artboards removed. Single canonical version of every screen.
2. **3-item bottom nav** applied consistently to every screen that shows nav chrome.
3. **Want list / Want detail / Want form** mockups (light + dark, all states from §13 of the original brief).
4. **Updated IA diagram** reflecting 3-item nav + Wants reachable via Today taps and You hub.
5. **Readiness tags** on every canvas section: `[redesign of existing surface]` vs `[future surface]`.
6. **Home (revised)** — without freeze chip, without exchange-rate banner, without per-habit streak chip. Just the streak number + 7-day heatmap + KPI row + identity strip + habits + wants + bottom nav.
7. **Today refresh states** — pulling / released / syncing / success / error variants of Today, light + dark.
8. **Microcopy delta** for any labels that changed (drop "Wants" tab label, add "Manage wants" inside You hub, etc.).
9. **Sort the canvas sections** in this order:
   - **Design system** first (tokens, primitives, component library)
   - **Existing-surface redesigns** next, in user-flow order: Onboarding → Auth → Today → Streak history → You hub → Settings
   - **Future surfaces** after, in conceptually related groupings: Identity (list, detail, add) → Habit (detail, CRUD) → Want (list, detail, CRUD) → Freezes → Want timer → Exchange rate → Widgets
   - **Notifications + IA diagram** last as reference appendices
   - Within each section: light variants before dark; primary state before edge states (empty / loading / error / pending)

---

## Don't change

- Token system (colors, type, spacing, radius, motion).
- Switch / Card / ListItem / HeatCell / SyncChip primitives — keep as-is.
- Notification visual examples — single canonical version, per-identity copy.
- Onboarding flow shape (4 steps, multi-identity step).
- Streak rules (frozen-day / broken-day / heat ramp / today-pending / future-day).
