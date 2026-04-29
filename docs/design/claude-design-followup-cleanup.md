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

### 6. Document the deferred features

In the canvas, clearly label which artboards represent **shipped + redesign** vs **future-feature designs not yet wired**:
- Identity list / detail / add → future feature (Phase 5b)
- Habit form CRUD / Habit detail → future feature (Phase 5c)
- Want list / detail / CRUD → future feature (Phase 5c) — see §7 below
- Freezes screen → future feature (Phase 5c)
- Want timer → future feature (Phase 5c)
- Exchange rate → future feature (Phase 7)
- Widgets → future feature (Phase 6)

The Home / Onboarding / Auth / Streak history / Settings redesigns are the IMMEDIATE-IMPLEMENTATION set. Mark them.

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
5. **Phase tags** on every canvas section: `[redesign · ship now]` vs `[future · phase 5b]` vs `[future · phase 5c]` vs `[future · phase 6]` vs `[future · phase 7]`.
6. **Home (revised)** — without freeze chip, without exchange-rate banner, without per-habit streak chip. Just the streak number + 7-day heatmap + KPI row + identity strip + habits + wants + bottom nav.
7. **Today refresh states** — pulling / released / syncing / success / error variants of Today, light + dark.
8. **Microcopy delta** for any labels that changed (drop "Wants" tab label, add "Manage wants" inside You hub, etc.).
9. **Sort the canvas sections** in this order:
   - **Design system** first (tokens, primitives, component library)
   - **Ship-now redesigns** next, in user-flow order: Onboarding → Auth → Today → Streak history → You hub → Settings
   - **Phase-tagged future features** after, grouped by phase: 5b (Identity list/detail/add, Habit form CRUD, Habit detail) → 5c (Want list/detail/form, Habit/Want CRUD lists, Freezes, Want timer, per-habit streak surfaces) → 6 (Widgets) → 7 (Exchange rate)
   - **Notifications + IA diagram** last as reference appendices
   - Within each section, light variants before dark; primary state before edge states (empty / loading / error / pending)

---

## Don't change

- Token system (colors, type, spacing, radius, motion).
- Switch / Card / ListItem / HeatCell / SyncChip primitives — keep as-is.
- Notification visual examples — single canonical version, per-identity copy.
- Onboarding flow shape (4 steps, multi-identity step).
- Streak rules (frozen-day / broken-day / heat ramp / today-pending / future-day).
