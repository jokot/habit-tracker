# Phase 7 — Want CRUD Design Spec

**Date:** 2026-05-10
**Branch:** `feature/phase7-want-crud` (off main, post phase6 + dev-tools merge)
**Worktree:** `.worktrees/phase7-want-crud`
**Design references:**
- `docs/design/claude-design-decisions-want-crud.md` (locked decisions)
- `docs/design/claude-design-followup-wants-seed.md` (14 seeded wants)
- `docs/design/claude-design-followup-habits-seed.md` (13 identities + 91 habit templates — out of scope this phase, captured for future)
- `docs/design/claude-design-followup-exchange-rate-v2.md` (per-identity unmount)
- Canvas: `screens-v2.jsx` + `screens-v3.jsx` mounted artboards (`WantList`, `WantDetail`, `WantForm`, `WantListMenuOpen`, `WantListWithHidden`, `SeededWantsReference`)

## Goal

Build CRUD for `WantActivity`: list, detail, add/edit form, hide-for-seeded, delete-for-custom. Bring the seeded want set in line with the new 14-item canvas list. Adopt the new exchange rate ladder (1.0/1.2/1.4/1.6/2.0×). Add cost-edit retro warning. No want-timer integration in this phase.

**Success metric:** A user can add a custom want, edit any want's cost, hide an unwanted seeded item, and see the change reflected on Today + Exchange rate within the same session — all without sync or test failures.

## Decisions log

| # | Decision | Choice | Source |
|---|---|---|---|
| 1 | Scope | Single phase, ~10 files | brainstorm Q1 |
| 2 | Hide / delete schema | One column: `hiddenAt: Instant?` (visibility filter `WHERE hiddenAt IS NULL`); custom-with-hiddenAt = effectively deleted (UI hides them) | Q2 |
| 3 | Sort | Alphabetical by name within each section | Q3 |
| 4 | Icon picker | Curated 13-glyph set + `more_horiz` fallback; explicit per-want icon stored on row | Q4 |
| 5 | Recent activity timeline | Last 7 days, day-grouped | Q5 |
| 6 | Rate ladder migration UX | Silent dismissible banner on Today: "Spend rates updated — see Exchange rate." | Q6 |
| 7 | Existing-user seed reconciliation | Additive — insert any new seeded items missing locally; preserve customizations | Q7 |
| – | Timer button | Stub: visible but on tap shows toast "Timer coming soon." | locked |
| – | Rate ladder | 1.0× / 1.2× / 1.4× / 1.6× / 2.0× (5 tiers) | locked |
| – | Cost-edit semantics | Recompute (read `costPerUnit` × stored quantity); show warning when editing on a want with past logs | locked |
| – | Today → Detail gesture | Long-press the want row | locked |

## Out of scope

- Want timer screen + alarm-style notification (separate phase).
- Per-identity rate model (`ExchangeRateV2` deferred, JSX retained).
- WantLog `costPerUnitAtLog` schema (recompute is canonical).
- 91-habit template + 13-identity seed migration (separate phase if/when adopted).
- Localization of any banner/copy.

## Architecture

### Data model changes

**`WantActivity` model gains two fields:**

```kotlin
data class WantActivity(
    val id: String,
    val name: String,
    val unit: String,
    val costPerUnit: Double,
    val isCustom: Boolean = false,
    val createdByUserId: String? = null,
    val updatedAt: Instant = Instant.fromEpochMilliseconds(0L),
    val syncedAt: Instant? = null,
    val iconKey: String? = null,        // NEW — Material Icons name, null = legacy
    val hiddenAt: Instant? = null,      // NEW — visibility flag (hide for seeded; delete for custom)
)
```

**Migration:** SQLDelight schema bump. New columns `iconKey TEXT NULL`, `hiddenAt INTEGER NULL`. Existing rows: `iconKey = NULL` (UI falls back to name-derived icon for those rows until reconciliation runs); `hiddenAt = NULL` (everything visible).

**Sync:** both new columns flow through existing `mergePulled` upsert path. `softDelete` semantics (`hiddenAt`) clear `syncedAt = NULL` so changes propagate (matches Phase 6 fix for log soft-deletes).

### Visibility filter

Single SQL filter applied everywhere visible-list semantics are needed:

```sql
WHERE userId = ? AND hiddenAt IS NULL
```

Touched queries: `getWantActivities`, `observeWantActivities`, `getByIdsForUser` for visible-only consumers.

`getWantActivities(includeHidden = false)` overload exists for `WantList` to fetch seeded-hidden rows for the "Hidden" section.

### Seed list update

`SeedData.wantActivities` replaces the existing 15-item list with the locked 14 items from `claude-design-followup-wants-seed.md`:

| id | name | unit | costPerUnit | iconKey |
|---|---|---|---|---|
| tiktok | TikTok | minutes | 1.0 | play_circle |
| yt-shorts | YouTube Shorts | minutes | 1.0 | play_circle |
| youtube | YouTube | minutes | 0.1 | smart_display |
| netflix | Netflix | minutes | 0.1 | local_movies |
| twitter | Twitter/X | minutes | 0.5 | chat_bubble |
| instagram | Instagram | minutes | 0.5 | photo_camera |
| reddit | Reddit | minutes | 0.5 | forum |
| gaming | Gaming | minutes | 0.5 | sports_esports |
| shopping | Online shopping | minutes | 0.5 | shopping_bag |
| junkfood | Junk food | meals | 5 | restaurant |
| snacks | Snacks | servings | 2 | restaurant |
| sweets | Sweets | pieces | 2 | cake |
| sugary | Sugary drinks | drinks | 2 | local_drink |
| coffee | Coffee | cups | 1 | local_cafe |

Stable IDs (well-known UUIDs) so reconciliation can compare per-id rather than per-name.

**Reconciliation (existing users on upgrade):** `SeedData.reconcileWantActivities(userId)` runs once per app start (idempotent). For each seed id, if the user has no row with that id, insert it. Existing rows untouched (preserves cost edits, hide state, custom wants).

### Rate ladder update

`ExchangeRateCalculator.tiers` updates to:

```kotlin
RateTier(level = 1, rate = 1.0, minStreak = 0,  maxStreak = 6)
RateTier(level = 2, rate = 1.2, minStreak = 7,  maxStreak = 13)
RateTier(level = 3, rate = 1.4, minStreak = 14, maxStreak = 20)
RateTier(level = 4, rate = 1.6, minStreak = 21, maxStreak = 29)
RateTier(level = 5, rate = 2.0, minStreak = 30, maxStreak = null)
```

Tier breakpoints unchanged (0/7/14/21/30); only multipliers move. All Phase 6 unit tests rebaseline.

**Migration banner:** new boolean pref `seenRateLadderUpgradeBanner`. Default false. On Today open, if false + user has any want logs (= experienced user), show snackbar:
> "Spend rates updated — see Exchange rate."

Action: tap → navigate to ExchangeRate screen + set pref true. Dismiss (X) → set pref true. Auto-dismiss after 8 seconds → don't update pref (banner returns next launch). One screen-tap to ExchangeRate also marks seen.

## Components

### File layout

| File | Type | Responsibility |
|---|---|---|
| `mobile/shared/src/commonMain/sqldelight/com/habittracker/data/local/HabitTrackerDatabase.sq` | modify | Add `iconKey TEXT`, `hiddenAt INTEGER` to `WantActivity`; new query `hideWantActivity` (sets hiddenAt + clears syncedAt). |
| `mobile/shared/src/commonMain/kotlin/com/habittracker/domain/model/WantActivity.kt` | modify | Add `iconKey`, `hiddenAt` fields. |
| `mobile/shared/src/commonMain/kotlin/com/habittracker/data/repository/WantActivityRepository.kt` | modify | Add `hideWantActivity(id, userId)`, `unhideWantActivity(id, userId)`, `getAllWantActivitiesForUser(userId)` (includes hidden). Existing `getWantActivities` filters hiddenAt. |
| `mobile/shared/src/commonMain/kotlin/com/habittracker/data/repository/LocalWantActivityRepository.kt` | modify | Implement above. |
| `mobile/shared/src/commonMain/kotlin/com/habittracker/data/local/SeedData.kt` | modify | Replace `wantActivities` with 14-item list including `iconKey`. |
| `mobile/shared/src/commonMain/kotlin/com/habittracker/domain/usecase/SetupUserWantActivitiesUseCase.kt` | modify | Add `reconcile(userId)` method (additive). |
| `mobile/shared/src/commonMain/kotlin/com/habittracker/domain/usecase/ExchangeRateCalculator.kt` | modify | Update `tiers` to new ladder. |
| `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/want/WantListScreen.kt` | new | List with seeded vs custom split + Hidden section + FAB. |
| `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/want/WantListViewModel.kt` | new | Observes wants flow + hide/unhide actions. |
| `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/want/WantDetailScreen.kt` | new | Hero + 7d activity timeline + Edit + stubbed Start timer. |
| `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/want/WantDetailViewModel.kt` | new | Loads single want + last 7d logs + total spent. |
| `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/want/WantFormScreen.kt` | new | Bottom-sheet form: name, unit chips, cost stepper, icon picker, cost-edit warning. |
| `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/want/WantFormViewModel.kt` | new | Add / edit / save / delete. Computes cost-edit warning state. |
| `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/components/WantIcon.kt` | new | `wantIcon(iconKey: String?, name: String): ImageVector` — explicit-key first, name-fallback for legacy rows. |
| `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/components/WantIconPicker.kt` | new | Bottom-sheet with 13 curated glyphs. |
| `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/home/HomeScreen.kt` | modify | Long-press want row → `WantDetail`. Render rate-ladder migration snackbar (one-shot). Use new `wantIcon(iconKey, name)`. |
| `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/exchange/ExchangeRateScreen.kt` | modify | Filter hidden wants from comparison rows. Display per-tap deduction (already shipped). |
| `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/you/YouHubScreen.kt` | modify | Add "Wants" row → `WantList`. |
| `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/navigation/AppNavigation.kt` | modify | New routes: `Screen.WantList`, `Screen.WantDetail/{id}`, `Screen.WantForm?id={id}`. |
| `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/notifications/NotificationPreferences.kt` (or settings store) | modify | Add `seenRateLadderUpgradeBanner: Boolean` pref. |

Total: ~10 new + ~7 modified files.

### Component summaries

**WantListScreen** — Two visible sections (Seeded, Custom), each alphabetical. Seeded row trailing icon = `visibility_off` (tap → confirm + hide). Custom row trailing icon = `edit` (tap → form). Row body tap → `WantDetail`. App-bar overflow menu shows "Show hidden (N)" → expands a third "Hidden" section with restore action. Big FAB → `WantForm` in new mode. Empty state when zero visible: per canvas mock.

**WantDetailScreen** — Hero card (icon, name, "Seeded" badge if seeded, cost line, total spent last 7d, times logged last 7d). Quick actions row: "Start timer" (stub, toast on tap) + "Edit" (→ form). Recent activity: last 7 days, day-grouped, time + qty + computed pt. Footer action: "Hide" (seeded) or "Delete" (custom) — both confirm dialog.

**WantFormScreen** — Bottom sheet. Fields:
- Icon picker chip (tap → `WantIconPicker` bottom sheet, 13 glyphs)
- Name (text input)
- Unit chips: minutes, servings, match, episode, session, item, drinks, cups, pieces, meals
- Cost stepper: -/+ buttons, big numeral (tap → keyboard input), live preview "e.g. 30 {unit} = X pt"
- Cost-edit warning banner (visible only in edit mode + cost field changed + want has past logs): "Editing this cost rewrites your spend history."
- Save button (primary) / Cancel
- In edit mode: footer "Delete want" (custom) or "Hide want" (seeded)

Stepper increment = 0.1 per tap; long-press = 1.0 jump; min = 0.0; max = 999.

**Hide vs delete behavior at repo layer:**
- Seeded want hidden: `hiddenAt = now`, `syncedAt = null`. UI restorable from "Hidden" section.
- Custom want deleted: same — `hiddenAt = now`, `syncedAt = null`. UI doesn't surface in Hidden section because `isCustom == true`. Effectively deleted.

Single `softHide(id, userId)` repo method serves both. UI gates the user-facing wording + restore affordance.

### Cost-edit warning logic

Show warning when ALL of:
- Form is in edit mode (`existingId != null`)
- Cost field's current value `!= original.costPerUnit`
- The want has at least one un-hidden `WantLog` historically (`countActiveLogsForActivity > 0`)

VM pre-computes `hasPastLogs: Boolean` once on form open. Warning banner re-renders reactively when cost field changes.

## State model

`WantListState`:
```kotlin
data class WantListState(
    val seeded: List<WantActivityWithStats>,    // visible seeded
    val custom: List<WantActivityWithStats>,    // visible custom
    val hidden: List<WantActivityWithStats>,    // expanded only on user toggle
    val showHidden: Boolean = false,
    val pendingConfirm: PendingHideConfirm? = null,
)
```

`WantDetailState`:
```kotlin
data class WantDetailState(
    val isLoading: Boolean,
    val want: WantActivity?,
    val totalSpent7d: Int,
    val timesLogged7d: Int,
    val timeline: List<DayLogs>,         // grouped by local date, last 7 days
    val isCustom: Boolean,                // computed from want
    val pendingDelete: Boolean = false,
    val toast: String? = null,            // for "Timer coming soon" + delete success
)

data class DayLogs(val date: LocalDate, val logs: List<TimedLog>)
data class TimedLog(val time: LocalTime, val qty: Double, val pointsAtLog: Int)
```

`WantFormState`:
```kotlin
data class WantFormState(
    val mode: FormMode,                  // New / Edit(id)
    val name: String,
    val unit: String,
    val costInput: String,                // stepper local string
    val costParsed: Double,
    val iconKey: String,
    val activities: List<WantActivity>,   // for unit chip dedup
    val hasPastLogs: Boolean,
    val isSaving: Boolean,
    val validationError: String? = null,
    val showCostEditWarning: Boolean,
)
```

## UI flow

### Today → Want detail

Long-press want row on Today → navigates to `WantDetail/{id}`. Tap (single press) preserves existing 3-sec spend-commit countdown.

### Hide flow (seeded want)

WantList → trailing `visibility_off` tap → confirm dialog "Hide {name}? You can restore it from the menu later." → `Hide` → repo `softHide` → snackbar "{name} hidden" with "Undo" action (5 second window) → tap Undo → repo `unhide`.

### Restore flow

WantList app-bar overflow `more_vert` → menu opens with "Show hidden (N)" → tap → state.showHidden = true → third section appears below Custom listing seeded-with-hiddenAt rows. Each has trailing `visibility` icon → tap → repo `unhide` → row moves to Seeded section.

### Custom delete

WantDetail (custom) → "Delete" footer button → confirm dialog "Delete {name}? Past logs stay in your history." → repo `softHide` → navigate back to WantList → snackbar "{name} deleted" (no undo, since hidden custom won't reappear in any UI surface — user-perceived as gone).

(Engineering note: behind the scenes `hiddenAt` is reversible at DB level. If user re-creates a want with the same id later via sync from another device, the new instance overwrites. Edge case; not surfaced.)

## Edge cases

- **Form open with empty seed icon row:** legacy WantActivity rows pre-migration have `iconKey = null`. UI falls back to old `wantIcon(name)` resolver until next save (which writes the correct iconKey).
- **Cost set to 0:** stored as `0.0`; `pointsSpentWithRate` short-circuits → 0pt deduction. UI shows "FREE" label (already shipped Phase 6).
- **Negative cost in keyboard input:** validation rejects; banner "Cost must be ≥ 0".
- **Renaming custom to match seeded id:** allowed; new row, new id; seeded reconciliation doesn't conflict because IDs differ.
- **Hiding all wants:** `WantList` shows empty-state mock; `Today` shows "No wants" empty subtitle on the Wants section.
- **Hidden want still appears in old `WantLog` rows:** activity timeline on `WantDetail` shows correctly; logs aren't tied to want's hidden state.
- **Rate ladder migration banner:** `seenRateLadderUpgradeBanner` defaults to `false` for new users too. Suppress banner if user has zero want logs (= fresh user, never experienced old ladder).
- **Sync conflict on hidden state:** standard `updatedAt` last-write-wins via `mergePulled`. Hide on phone, edit cost on tablet — whichever has newer `updatedAt` wins. Acceptable.
- **Reconciliation idempotency:** `SetupUserWantActivitiesUseCase.reconcile` checked against `WantActivity` table by stable ID. Re-run safe — only inserts missing.

## Testing

### Unit (commonTest)

`SeedDataReconcileTest`:
- New user: zero existing wants → reconcile inserts all 14.
- Mid-state user: 5 of 14 seed ids exist → reconcile inserts 9 missing.
- Customized user: existing seed row has cost 5.0 (overridden from 1.0) → reconcile preserves 5.0.
- Hidden user: existing row has hiddenAt set → reconcile leaves it alone.
- Idempotency: run reconcile twice → second run inserts nothing.

`WantActivityRepoVisibilityTest`:
- `getWantActivities(userId)` filters out rows with hiddenAt != null.
- `getAllWantActivitiesForUser(userId)` returns hidden + visible.
- `softHide` sets hiddenAt + clears syncedAt. `unhide` clears hiddenAt + clears syncedAt.

`ExchangeRateCalculatorTest` rebaseline:
- Tier 1: 0–6 → 1.0×.
- Tier 2: 7–13 → 1.2×.
- Tier 3: 14–20 → 1.4×.
- Tier 4: 21–29 → 1.6×.
- Tier 5: 30+ → 2.0×.

### VM (testDebug)

`WantFormViewModelTest`:
- New mode: empty fields, save inserts.
- Edit mode opens with existing want's fields.
- Cost change in edit mode + has past logs → showCostEditWarning = true.
- Cost change in edit mode + no past logs → showCostEditWarning = false.
- Save in edit mode persists changes.
- Delete in edit mode (custom) calls softHide.
- Validation: empty name rejects; negative cost rejects.

`WantListViewModelTest`:
- Seeded vs custom partition.
- Hidden section toggleable.
- Hide → list update + snackbar emit.

`WantDetailViewModelTest`:
- Last 7d filter respects local timezone.
- Total spent computed using `pointsSpentWithRate(qty, cost, rateAtLogDay)`.

### UI smoke (manual)

- Add custom want → appears in Custom section.
- Edit seeded want's cost from 1.0 → 2.0 → warning banner appears → save → next tap on Today reflects new cost.
- Hide seeded want → disappears from Today + ExchangeRate comparison rows. Show hidden → restore → reappears.
- Delete custom want → gone from list. Past WantLog rows on streak history still show entries (cost computed against the deleted activity's last-known costPerUnit).
- Long-press a want row on Today → opens detail.
- Tap "Start timer" on detail → toast "Timer coming soon."
- New install: 14 seeded wants present, all visible.
- Upgrade install (existing user): banner "Spend rates updated…" appears once. Tap → navigates to ExchangeRate. Banner doesn't reappear next launch.
- Open ExchangeRate with one hidden seeded want → row absent from comparison list.

## Migration

- Schema bump: SQLDelight migration file adds `iconKey`, `hiddenAt` columns nullable.
- Reconciliation: runs from `AppContainer.seedLocalDataIfEmpty` extension or new `reconcileSeededDataIfNeeded()` on app start. Idempotent.
- Rate ladder migration banner: shows once via DataStore pref.
- No `WantLog` schema change.
- No retroactive `iconKey` backfill for custom wants — UI fallback to name-derived resolver until user opens form + saves.

## Acceptance

- [ ] All Phase 6 + dev-tools tests still pass after rebaseline.
- [ ] 14 seeded wants present for new install.
- [ ] Existing-user upgrade path: missing seed rows added, customizations preserved.
- [ ] Add / edit / hide / delete flows work without sync errors.
- [ ] Cost-edit warning shows for cost change in edit mode on want with past logs.
- [ ] Today long-press → detail.
- [ ] Hidden wants absent from Today + ExchangeRate comparison rows; present in Hidden section.
- [ ] Migration banner one-shot.
- [ ] Rate ladder unit tests rebaselined to 1.0/1.2/1.4/1.6/2.0×.
- [ ] Release variant (`assembleRelease`) builds; debug APK installable.
