# Phase 5e-3 — Habit Form + Delete + Custom Habit Design

**Status:** Approved
**Date:** 2026-05-04
**Phase:** 5e-3 (final sub-phase of Habit CRUD work; 5e-1 foundation + 5e-2 read-only browse already merged)

## Goal

Make habits writable. Users can create, edit, and delete habits — including free-form ("custom") habits not derived from a curated template. Provide entry points from `HabitListScreen`, `HabitDetailScreen`, and `IdentityDetailScreen`.

## Architecture

One form screen (`HabitFormScreen`) handles three modes:

- **Create custom** — free-form habit, `templateId = null`
- **Create from template** — same form pre-filled from template defaults (used by `IdentityDetail` entry; `AddIdentityFlow` integration deferred)
- **Edit existing** — full field edit, `templateId` left untouched

Mode is determined by nav arguments (`habitId` for edit, `identityId` for pre-fill). Same UI; the ViewModel branches on init.

Soft semantics throughout (5e-1 design intent):
- Habit delete → `markHabitDeleted` sets `Habit.effectiveTo = now`
- Identity unlink → `markHabitIdentityRemoved` sets `LocalHabitIdentity.effectiveTo = now`

Past streak/heat history stays accurate. Future days exclude. Sync already handles `effectiveTo` on both rows.

## Schema Changes

### Local (SQLDelight migration `4.sqm`)

`LocalHabit.templateId` becomes nullable. SQLite requires a rebuild:

```sql
CREATE TABLE LocalHabit_new (
    id TEXT PRIMARY KEY NOT NULL,
    userId TEXT NOT NULL,
    templateId TEXT,                 -- nullable now (was NOT NULL)
    name TEXT NOT NULL,
    unit TEXT NOT NULL,
    thresholdPerPoint REAL NOT NULL,
    dailyTarget INTEGER NOT NULL,
    createdAt INTEGER NOT NULL,
    updatedAt INTEGER NOT NULL,
    syncedAt INTEGER,
    effectiveFrom INTEGER,
    effectiveTo INTEGER
);
INSERT INTO LocalHabit_new SELECT * FROM LocalHabit;
DROP TABLE LocalHabit;
ALTER TABLE LocalHabit_new RENAME TO LocalHabit;
```

Existing rows preserve their `templateId` (no data loss).

### Cloud (Supabase migration)

```sql
ALTER TABLE habits ALTER COLUMN template_id DROP NOT NULL;
```

### Domain model

```kotlin
data class Habit(
    val id: String,
    val userId: String,
    val templateId: String?,  // was String — now nullable
    val name: String,
    val unit: String,
    val thresholdPerPoint: Double,
    val dailyTarget: Int,
    val createdAt: Instant,
    val updatedAt: Instant,
    val syncedAt: Instant? = null,
    val effectiveFrom: Instant? = null,
    val effectiveTo: Instant? = null,
)
```

`PostgrestSupabaseSyncClient` DTOs (push + pull) update to `templateId: String?`.

## Components

### New (shared/commonMain)

**`SaveHabitUseCase`**
- `create(userId, name, unit, threshold, target, identityIds, templateId = null)` — inserts Habit + links each identity with `effectiveFrom = now`
- `update(userId, habitId, name, unit, threshold, target, newIdentityIds)` — loads existing habit + active links, applies field updates, diffs identity sets:
  - `newIdentityIds - currentlyActive` → `linkHabitToIdentities` (insert/upsert with `effectiveFrom = now`, `effectiveTo = null`)
  - `currentlyActive - newIdentityIds` → `markHabitIdentityRemoved(habitId, identityId, now)`
  - Re-link of previously removed identity (link row exists with `effectiveTo` set): UPDATE clears `effectiveTo` only; `effectiveFrom` left as-is. Past gap glossed over but past activity preserved. Re-link is rare; this is YAGNI for v1.

**`DeleteHabitUseCase`**
- `execute(userId, habitId)` → `habitRepo.markHabitDeleted(habitId, userId, effectiveTo = now)` + sync trigger.

**`IdentityRepository.markHabitIdentityRemoved(habitId, identityId, effectiveTo)`**
- New interface method + `LocalIdentityRepository` impl.
- SQLDelight: `UPDATE LocalHabitIdentity SET effectiveTo = ?, syncedAt = NULL WHERE habitId = ? AND identityId = ?`
- Sync push must include rows where `effectiveTo IS NOT NULL AND syncedAt IS NULL` (verify existing query already covers; add if missing).

### New (androidApp)

**`HabitFormScreen`** — single composable per canvas `HabitFormMulti` (screens.jsx:2633):
- TopAppBar: close icon (left), title "New habit" / "Edit habit", "Save" text button (right, disabled when invalid)
- Glyph + Name field (auto-glyph derived from first selected identity hue + `habitIcon(name)`)
- Identities section: "at least 1 required" hint, selected chips (tinted pill with X), "Add another identity…" pill, "Suggested" chip row (other user identities not yet selected)
- Goal section: two cards side-by-side — "1 point per [N] [unit]" + "Daily target [N] [unit]"
- Delete button (edit mode only): text button error color → confirm dialog → soft delete → pop

**`HabitFormViewModel`** — holds form state (name, identityIds, threshold, target, unit), exposes validation flags, dispatches `SaveHabitUseCase` / `DeleteHabitUseCase`. Init branches:
- `habitId == null && identityId == null` → empty Create
- `habitId == null && identityId != null` → Create with `identityIds = {identityId}`
- `habitId != null` → Edit; load habit + active links, populate state

### Modified

- **`Habit` model** — `templateId: String?`
- **`LocalHabitRepository`** — handle nullable templateId in upsert + row mapping
- **`PostgrestSupabaseSyncClient`** — DTO `templateId: String?`
- **`IdentityRepository` interface + `LocalIdentityRepository`** — add `markHabitIdentityRemoved`
- **`HabitListScreen`** — FAB "New habit" → nav to `HabitForm` (create custom)
- **`HabitDetailScreen`** — edit icon in app bar → nav to `HabitForm` with `habitId`. Delete result (when initiated from detail's edit-then-delete flow) returns to list, not detail
- **`IdentityDetailScreen`** — "+ Add habit" dashed row at bottom of habits list (canvas line 2383-2392) → nav to `HabitForm` with `identityId` pre-fill
- **`IdentityDetailScreen` + `IdentityDetailViewModel`** — refactor "Remove identity" to use confirm dialog (mirror habit delete dialog UX). Currently removes immediately. New: `showRemoveDialog: StateFlow<Boolean>`, `beginRemove()`, `confirmRemove()`, `dismissRemoveDialog()`. Dialog body uses existing copy "Removing keeps your habits — they stay associated with the identities they support."
- **`AppNavigation`** — `HabitForm` route with optional args (`habitId`, `identityId`)

## Form UX

| Field | Behavior |
|---|---|
| Glyph (56dp) | Auto-derived: `habitIcon(name)` + first selected identity's hue. No manual picker. |
| Name | TextField, required, non-empty after trim. Underlined per design. |
| Identities | Multi-select chips. Selected → tinted pill (identity hue) with X. "Add another identity…" pill opens bottom sheet of user's identities not yet selected. ≥1 required. |
| Threshold ("1 point per") | Numeric TextField + unit text. > 0 required. |
| Daily target | Integer TextField. ≥ 1 required. |
| Unit | Free-text TextField. Non-empty after trim. Plain `String` storage (no dedupe, no FK — see "Decisions" below). |
| Save (top right) | Disabled when any field invalid. |
| Delete (bottom, edit mode) | Text button error color → `AlertDialog` "Delete habit?" / "Past activity stays in your history. Future days will exclude it." / Cancel · Delete (error). |

Validation: inline red helper text under invalid fields after blur. No async validation.

## Data Flow

### Create custom (HabitList FAB)

```
User taps FAB
→ navigate HabitForm(habitId=null, identityId=null)
→ VM init: empty form, mode=Create
→ User fills fields + taps Save
→ SaveHabitUseCase.create(...)
  → habitRepo.saveHabit(Habit(templateId=null, effectiveFrom=now, ...))
  → identityIds.forEach { linkHabitToIdentities(habitId, {it}) }
→ pop back, enqueue sync (`SyncReason.POST_LOG` — existing catch-all for writes)
```

### Create from identity (IdentityDetail "+ Add habit")

```
User taps "+ Add habit" row
→ navigate HabitForm(habitId=null, identityId=current)
→ VM init: identityIds={current}, name/threshold/target/unit empty
→ same Save path as create custom
```

### Edit (HabitDetail edit icon)

```
User taps edit icon
→ navigate HabitForm(habitId=existing, identityId=null)
→ VM init: load habit + active identity links → populate fields
→ User edits + taps Save
→ SaveHabitUseCase.update(...)
  → habitRepo.saveHabit(updatedHabit) // new updatedAt
  → diff: newIdentityIds - currentlyActive → linkHabitToIdentities (insert/resume)
  → diff: currentlyActive - newIdentityIds → markHabitIdentityRemoved(now)
→ pop back, enqueue sync
```

### Delete (form bottom button)

```
User taps Delete → AlertDialog
User taps Confirm
→ DeleteHabitUseCase.execute(userId, habitId)
  → habitRepo.markHabitDeleted(habitId, userId, now)
→ pop back to HabitList (or IdentityDetail if entered from there), enqueue sync
```

### Remove identity (IdentityDetail confirm dialog refactor)

```
User taps "Remove identity"
→ VM.beginRemove() → showRemoveDialog = true
→ Screen renders AlertDialog
User taps Remove
→ VM.confirmRemove() → existing removeUseCase.execute(...) + dismiss dialog + emit removeSuccess
→ Screen pops back
```

## Engine Compatibility

**`Habit.effectiveTo` (delete)**: already supported by all three engines (5e-1).
- `ComputeStreakUseCase`: hybrid window check (today=instant grace, past=date overlap) excludes habit when `effectiveTo <= dayStart`
- `ComputeIdentityStatsUseCase`: date-overlap excludes when `effectiveTo <= dayStart`
- `ComputePerHabitStreakUseCase`: date-overlap; once `effectiveTo` set, all days from then forward show EMPTY (habit no longer active)

**`LocalHabitIdentity.effectiveTo` (unlink)**: 5e-1 deferred per-day filtering on `HabitIdentityRow.effectiveFrom/effectiveTo`. Today's engine reads:
- `IdentityRepository.observeHabitsForIdentity(userId, identityId)` returns habits CURRENTLY linked (no date filter on link)
- `IdentityRepository.getHabitIdentityLinksForUser(userId)` returns all link rows including ones with `effectiveTo` set

For 5e-3, `observeHabitsForIdentity` should filter `effectiveTo IS NULL` so unlinked habits drop out of the identity's habit list. This is the single change required to make soft unlink visible. Past identity-grid retroactive accuracy (showing the habit's contribution during the linked window) remains deferred per 5e-1 (`HabitIdentityRow.effectiveFrom/effectiveTo per-day filter is deferred`).

## Error Handling

| Scenario | Handling |
|---|---|
| Save with invalid input | Save button disabled |
| Save throws (DB write fail) | VM error state → screen snackbar "Couldn't save habit. Try again." |
| Delete throws | Snackbar; dialog dismissed; habit unchanged |
| Edit a habit deleted on another device (rare race) | `markHabitDeleted` idempotent. Save still writes; tombstone wins on next sync. |
| Identity removed while form open | VM caches identity list at load. Save proceeds with cached selection. Sync reconciles. |
| Offline | Local write succeeds (offline-first); sync retries on reconnect. |

## Testing

### `commonTest` (use cases)

- `SaveHabitUseCaseTest`
  - create custom: `templateId == null`, links inserted with `effectiveFrom = now`
  - create from template (templateId argument propagated)
  - update field-only: name/threshold/target/unit changed, links untouched
  - update with link diff: add new link, remove old link, re-link previously-removed
  - validation: rejects empty name, 0 identities, threshold ≤ 0, target < 1, empty unit
- `DeleteHabitUseCaseTest`
  - sets `effectiveTo` and clears `syncedAt`
- `IdentityRepositoryTest` (or `LocalIdentityRepositoryTest`)
  - `markHabitIdentityRemoved` updates `effectiveTo`, leaves `effectiveFrom` untouched, clears `syncedAt`

### `androidUnitTest` (ViewModel)

- `HabitFormViewModelTest`
  - load existing habit populates state correctly
  - validation flags fire on blur
  - Save in create mode dispatches `create(...)`; edit mode dispatches `update(...)`
  - Delete dispatches `DeleteHabitUseCase`
- `IdentityDetailViewModelTest`
  - confirm dialog flow: `beginRemove` shows, `dismissRemoveDialog` hides without removing, `confirmRemove` calls use case + emits success

### Engine regression (`commonTest`)

- `ComputeIdentityStatsUseCaseTest`: habit with link `effectiveTo` set drops from identity's active habits today (heat 0 if all linked-and-active habits also unlogged) — sanity check that `observeHabitsForIdentity` filter works downstream

## Phase Boundary — Deferred

- **AddIdentityFlow step 2 "+ Define a custom habit"** entry point (canvas line 2608). Touches existing flow; separate pass.
- **Icon picker** UI. For v1, glyph auto-derives from identity hue + `habitIcon(name)` keyword match.
- **Unit dedupe / autocomplete**. Plain text suffices (no aggregation across units).
- **Reorder habits** (drag handle visible in `HabitCRUD` design — not yet implemented anywhere).
- **Past-identities collapsed section** on IdentityList (canvas line 2220-2228).
- **Per-day identity-link filter in engines** (deferred from 5e-1; 5e-3 only filters at `observeHabitsForIdentity` level for current-state correctness).

## Decisions Log

| Decision | Choice | Rationale |
|---|---|---|
| Custom habit storage | `templateId: String?` nullable | Cleanest schema; null semantically matches "no template". Sentinel risks accidental joins. |
| Delete | Soft via `effectiveTo` (existing) | 5e-1 designed for this. Past streak/heat preserved. |
| Identity unlink | Soft via link's `effectiveTo` | Mirror delete semantics. New repo method `markHabitIdentityRemoved`. |
| Re-link previously removed identity | UPDATE clears `effectiveTo` only; keep original `effectiveFrom` | Composite PK forces single row. Past gap glossed over; past activity preserved. YAGNI for v1. |
| Edit scope | Full edit (name + identities + threshold + target + unit) | Streak unaffected (presence rule). Past pts recompute (acceptable). |
| Confirm dialog | M3 `AlertDialog`, mirror existing LogoutDialog pattern | No specific canvas mockup. Standard Material convention. |
| Identity removal in form | Soft, batched on Save | Atomic with field changes. |
| Unit field | Free-text, no dedupe | Display-only; no aggregation. YAGNI. |
| Identity association on edit | ≥ 1 required | Per canvas "at least 1 required" hint. |

## Entry Points (in scope)

| # | Entry | Effort | Pre-fill |
|---|---|---|---|
| 1 | `HabitListScreen` FAB "New habit" | low | none (custom) |
| 2 | `IdentityDetailScreen` "+ Add habit" row | low | identityId |
| 3 | `HabitDetailScreen` edit icon | low | habitId (full habit) |

## Files Touched

### Created

- `mobile/shared/src/commonMain/kotlin/com/habittracker/domain/usecase/SaveHabitUseCase.kt`
- `mobile/shared/src/commonMain/kotlin/com/habittracker/domain/usecase/DeleteHabitUseCase.kt`
- `mobile/shared/src/commonMain/sqldelight/com/habittracker/data/local/migrations/4.sqm`
- `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/habit/HabitFormScreen.kt`
- `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/habit/HabitFormViewModel.kt`
- Tests: `SaveHabitUseCaseTest.kt`, `DeleteHabitUseCaseTest.kt`, `HabitFormViewModelTest.kt`
- Supabase migration file under `supabase/migrations/`

### Modified

- `mobile/shared/src/commonMain/kotlin/com/habittracker/domain/model/Habit.kt`
- `mobile/shared/src/commonMain/kotlin/com/habittracker/data/repository/HabitRepository.kt` (no signature change; impl handles nullable templateId)
- `mobile/shared/src/commonMain/kotlin/com/habittracker/data/repository/LocalHabitRepository.kt`
- `mobile/shared/src/commonMain/kotlin/com/habittracker/data/repository/IdentityRepository.kt` (add `markHabitIdentityRemoved`)
- `mobile/shared/src/commonMain/kotlin/com/habittracker/data/repository/LocalIdentityRepository.kt`
- `mobile/shared/src/commonMain/kotlin/com/habittracker/data/sync/PostgrestSupabaseSyncClient.kt` (DTOs)
- `mobile/shared/src/commonMain/sqldelight/com/habittracker/data/local/HabitTrackerDatabase.sq` (templateId nullable; new UPDATE for unlink)
- `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/AppContainer.kt` (wire new use cases)
- `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/navigation/AppNavigation.kt` (HabitForm route)
- `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/habit/HabitListScreen.kt` (FAB)
- `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/habit/HabitDetailScreen.kt` (edit icon)
- `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/identity/IdentityDetailScreen.kt` ("+ Add habit" row + dialog)
- `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/identity/IdentityDetailViewModel.kt` (confirm dialog state)
- `IdentityDetailViewModelTest.kt` (dialog flow tests)
