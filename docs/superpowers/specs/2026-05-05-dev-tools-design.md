# Dev Tools — Design Spec

**Date:** 2026-05-05
**Branch:** `feature/dev-tools` (off `feature/phase6-exchange-rate`)
**Worktree:** `.worktrees/dev-tools`

## Goal

Provide a debug-only screen that seeds backdated habit + want logs so Phase 6 exchange-rate behavior can be smoke-tested without waiting 7+ real days. Compile-time excluded from release builds.

## Out of Scope (deferred)

- Force sync, dump state, clock offset, trigger session expiry, reset/wipe data — added later as needed.
- Compose UI tests for dev-tools screen.
- Promoting seeder to shared module for `commonTest`.

## Decisions Log

| # | Question | Choice |
|---|----------|--------|
| 1 | MVP scope | Seed habit-streak + auto-include past want spends (option A from brainstorm) |
| 2 | Streak granularity | Constant (pick heat level) **or** Random (random 1–4); both with optional freeze + broken gap injection |
| 3 | Want spend coverage | Auto-include via toggle on streak seeder ("also seed 1 want spend per complete day") |
| 4 | Entry point | Settings row, debug-gated via `BuildConfig.DEBUG` |
| 5 | Idempotency | Confirmation dialog before wiping existing logs in window |
| 6 | Architecture | Approach 1 — pure debug source set (`src/debug/kotlin/`) |

## Architecture

### File layout

```
mobile/androidApp/src/debug/kotlin/com/jktdeveloper/habitto/devtools/
  DevToolsScreen.kt          — Compose screen
  DevToolsViewModel.kt       — single state-flow + seed action
  DevSeeder.kt               — pure planning logic (slot placement + qty mapping)
  DevToolsRoute.kt            — debug-only @Composable wrapper

mobile/androidApp/src/androidMain/kotlin/.../ui/settings/SettingsScreen.kt
  + adds row, gated `if (BuildConfig.DEBUG)`

mobile/androidApp/src/androidMain/kotlin/.../ui/navigation/AppNavigation.kt
  + Screen.DevTools route, mounted via debug-only composable

mobile/androidApp/src/testDebug/kotlin/com/jktdeveloper/habitto/devtools/
  DevSeederTest.kt
```

### Source-set mechanics

- `src/debug/kotlin/` auto-merges with `androidMain` on debug variant.
- Release build: `src/debug/` is not compiled. Only the `if (BuildConfig.DEBUG)` line in Settings ships.
- `BuildConfig.DEBUG = false` in release prunes the row at runtime; reference to `Screen.DevTools` is in `androidMain` (compiles in both variants), but route-mount Composable is in `src/debug/` so release link references would fail. Mount call site lives in a debug-only file too.

### Why split DevSeeder out of VM

Pure planning logic (slot placement, heat→quantity mapping) is the only piece worth testing. UI/VM is glue.

## Domain

### Heat-level → log quantity mapping

Reverse-engineer `ComputeStreakUseCase.bucketFor`:

| Level | Per-habit quantity (target = T) |
|-------|---------------------------------|
| 0 | n/a — not selectable in Constant; not produced in Random |
| 1 | `max(1, ceil(T * (bareMin gap)))` — bare minimum to count "all logged"; resolves to qty=1 for `dailyTarget=1` habits |
| 2 | `bareMin + (T - bareMin) / 3` |
| 3 | `bareMin + 2 * (T - bareMin) / 3` |
| 4 | `T` |

Tested via round-trip: `bucketFor(quantityForLevel(L), allLogged=true) == L` for L ∈ {1,2,3,4}.

### Slot placement (DevSeeder.plan)

Inputs: `days N, mode, level?, freeze, broken`.

1. Validate `freeze + 2*broken < days` (must leave ≥1 complete day).
2. Reserve `broken` non-overlapping 2-day pairs at random positions.
3. Reserve `freeze` single-day positions at random remaining positions; prefer slots flanked by complete days (otherwise engine marks BROKEN, not FROZEN).
4. Remaining slots = COMPLETE; assign heat level per mode.
5. RNG seeded with system time → reseeds vary.

Returns: `List<DaySlot>` where `DaySlot = { date: LocalDate, kind: Complete(level) | Frozen | Broken }`.

### Seed action

Per `DaySlot` in plan:

- **Frozen / Broken:** delete existing logs in `[dayStart, dayEnd)`; insert nothing.
- **Complete(level):** delete existing logs in window; insert one habit log per active habit with `quantity = quantityForLevel(level, habit.dailyTarget)`. If want toggle on, also insert one want log on selected activity at chosen quantity.

Window: `[today - days, today)` — today excluded so user can manually test "today" UX.

## State Model

```kotlin
enum class SeedMode { Constant, Random }

data class DevToolsState(
    val isLoading: Boolean = false,
    val mode: SeedMode = SeedMode.Constant,
    val days: Int = 14,
    val constantLevel: Int = 4,                  // 1..4
    val freezeCount: Int = 0,
    val brokenCount: Int = 0,
    val seedWantSpends: Boolean = false,
    val activities: List<WantActivity> = emptyList(),
    val selectedActivityId: String? = null,
    val wantQuantity: Double = 1.0,
    val validationError: String? = null,
    val pendingConfirm: ConfirmPlan? = null,
    val toast: String? = null,
)

data class ConfirmPlan(
    val days: Int,
    val completeSlots: Int,
    val freezeSlots: Int,
    val brokenSlots: Int,
    val habitLogsToDelete: Int,
    val wantLogsToDelete: Int,
    val expectedRate: Double,
)
```

## UI Layout

1. TopAppBar: "Dev tools" + back arrow.
2. Section "Seed streak"
   - Mode segmented control: `[Constant | Random]`
   - Days slider 1–35 with label
   - Constant only: heat-level chips `[1] [2] [3] [4]`
   - Freeze count: stepper +/-
   - Broken count: stepper +/-
   - Helper text: "Each broken = 2-day gap. Budget: N − freeze − 2×broken complete days."
3. Section "Want spends"
   - Toggle "Also seed 1 want spend per complete day"
   - If on: activity dropdown + quantity field
4. Validation banner (red) if invalid.
5. Big "Seed" button (primary, disabled while loading or invalid).
6. Confirm dialog over screen when `pendingConfirm != null`.

## Validation

| Condition | Message |
|-----------|---------|
| `days < 1 \|\| days > 35` | "1–35 days" |
| `freeze + 2*broken >= days` | "Gaps fill window. Add ≥1 complete day." |
| Want on + `activities.isEmpty()` | "No want activities exist. Create one first." |
| Want on + `selectedActivityId == null` | "Pick an activity." |
| Want on + `wantQuantity <= 0` | "Quantity > 0." |
| `habits.isEmpty()` (screen load) | Render error placeholder: "No habits exist for current user." |

## Edge Cases

- **Sync:** seeded logs sync to cloud like normal logs. Confirm dialog notes this.
- **Background during seed:** `viewModelScope` continues; toast may not fire. Acceptable.
- **DB write failure:** catch + show toast; leave partial state; user re-seeds.
- **Repeated seeds:** confirm-dialog wipe semantics ensure no compounding.
- **Streak recomputation:** automatic via `ComputeStreakUseCase.observe...` flow that already powers Home/Exchange/YouHub VMs.

## Testing

### Unit (DevSeeder, in `src/testDebug/kotlin/`)

1. `plan(days=14, Constant, level=4, freeze=0, broken=0)` → 14 Complete(4) slots, all `today - n` for n ∈ 1..14.
2. `plan(days=14, level=4, freeze=2, broken=1)` → 10 Complete + 2 Frozen + 1 Broken-pair (2 slots).
3. `plan(days=14, freeze=20)` → returns `Result.failure` with message "Gaps fill window…".
4. `plan(days=14, broken=7)` → returns `Result.failure` (zero complete days).
5. `quantityForLevel(L=1, target=10)` → ≥1.
6. `quantityForLevel(L=4, target=10)` → 10.
7. `quantityForLevel(L=2/3, target=10)` → round-trip via `ComputeStreakUseCase.bucketFor` returns same level.
8. Random mode (100 iterations): every Complete slot has level ∈ {1,2,3,4}.
9. Slot placement: freeze prefers flanked positions; broken pairs don't overlap.

### ViewModel + UI

Skip. Glue covered by smoke.

### Manual smoke (driven by this feature)

- Settings → Dev tools row visible (debug build only).
- Seed 14 days Constant level 4 → ExchangeRate hero shows "1.2×".
- Re-seed with freeze=1 → streak counter unchanged at 14.
- Re-seed with broken=1 → streak counter resets at break.
- Toggle want spends + pick activity → seed → Home Balance reflects spent; ExchangeRate comparison rows show base→current.
- Switch to release build (or `assembleRelease`) → Settings row absent.

## Implementation Order (preview, plan handles details)

1. `DevSeeder.kt` + `DevSeederTest.kt` (TDD; pure logic).
2. `DevToolsViewModel.kt`.
3. `DevToolsScreen.kt` + confirm dialog.
4. `DevToolsRoute.kt` + `Screen.DevTools` nav.
5. Settings row gated by `BuildConfig.DEBUG`.
6. Manual smoke → confirm release build excludes screen.
