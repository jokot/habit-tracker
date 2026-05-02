# Phase 5c-2 — Identity CRUD

> **Branch:** `feature/phase5c-2-identity-crud`. **Worktree:** `.worktrees/phase5c-2-identity-crud`.

## Goal

Ship the write-paths for identity management:

1. **AddIdentityFlow** — 2-step picker → recommended habits, template-only.
2. **Pin / unpin** — single featured identity, synced across devices.
3. **Remove identity** — soft-delete, no warning dialog, orphan habits stay active.
4. **"Why this identity" reflection** — free-text per-identity editor, synced.

## Non-Goals

- **Custom identity** (user-defined name/icon/hue). Deferred. Design's "Custom" tile rendered but tapping shows a "Coming soon" toast.
- **Custom habit creation inside AddIdentityFlow.** Step 2's "+ Define a custom habit" button rendered but tapping shows "Coming soon". Belongs in the Habit CRUD phase.
- **Habit edit / delete UI.** Habit CRUD phase.
- **"Past identities" collapsed section** on IdentityList (design line 1951+). Future feature.
- **Search backend** for AddIdentityFlow step 1. UI placeholder only; no filtering logic.
- **Remove confirm dialog.** Single-tap remove (per Q4 decision).
- **Streak engine changes.** Orphan habits remain in `getHabitsForUser` and remain required for the strict daily-complete rule (Q4 C1).

## Architecture

Extend the existing `IdentityRepository` with new mutators (pin, unpin, remove, set-why). Add one use case per write operation. Add `AddIdentityWithHabitsUseCase` to compose step-2 commit into a single transaction (insert user_identities + insert habits + link habit_identities). New `AddIdentityFlow` screen pair driven by one `AddIdentityViewModel` (step is VM state, not a separate route). Existing `IdentityDetailScreen` gains pin button, remove button, and inline reflection editor — all wired through `IdentityDetailViewModel`.

One schema migration adds three columns to `user_identities`: `isPinned`, `whyText`, `removedAt`. No new tables. No new sync handlers — existing `user_identities` upsert pipeline carries the new columns once the row mapper is updated.

## Schema migration

### Local — `mobile/shared/src/commonMain/sqldelight/com/habittracker/data/local/migrations/2.sqm`

```sql
ALTER TABLE LocalUserIdentity ADD COLUMN isPinned INTEGER NOT NULL DEFAULT 0;
ALTER TABLE LocalUserIdentity ADD COLUMN whyText TEXT;
ALTER TABLE LocalUserIdentity ADD COLUMN removedAt INTEGER;
```

`HabitTrackerDatabase.sq` `LocalUserIdentity` definition gains the three columns at end (for fresh installs). New / updated queries:

- `selectActiveUserIdentitiesForUser(userId)` — existing query gains `AND removedAt IS NULL`.
- `setPinForIdentity(userId, identityId, isPinned)` — UPDATE with `pendingSync = 1`.
- `clearPinForUser(userId)` — UPDATE all rows for `userId` where `isPinned = 1` to `isPinned = 0` with `pendingSync = 1`.
- `updateWhyText(userId, identityId, whyText)` — UPDATE with `pendingSync = 1`.
- `markUserIdentityRemoved(userId, identityId, removedAt)` — UPDATE `removedAt`, also `isPinned = 0` (atomic, in same UPDATE), with `pendingSync = 1`.

SQLDelight database version: bump from 1 → 2.

### Supabase

```sql
ALTER TABLE public.user_identities ADD COLUMN is_pinned BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE public.user_identities ADD COLUMN why_text TEXT;
ALTER TABLE public.user_identities ADD COLUMN removed_at TIMESTAMPTZ;
```

RLS policies unchanged (per-row `user_id` check still applies to all new columns).

### Sync mapper updates

`PostgrestSupabaseSyncClient` round-trips:
- `is_pinned` (Boolean ↔ Int 0/1)
- `why_text` (String? ↔ TEXT NULL)
- `removed_at` (Long? millis ↔ TIMESTAMPTZ NULL)

`LocalUserIdentity` data class gains three new fields. `IdentityRepository.observeUserIdentities` filter applies `removedAt IS NULL`.

Pull semantics: when a server row arrives with `removed_at != NULL`, local upserts the soft-delete state. Active queries filter it out — soft-delete propagates across devices via the standard sync.

## Components

### AddIdentityFlow

Two-screen wizard inside one route + one ViewModel.

**Screen.AddIdentity : Screen("add_identity")**

#### Step 1 — Picker (`AddIdentityStep1Screen`)

Per design (screens.jsx:2162-2258):
- App bar: close icon (left), title "Add identity"
- Heading: "Who else are you becoming?" (20sp, weight 700)
- Sub: "Pick from suggestions or define your own."
- Search bar — UI placeholder only, non-functional
- 2-column grid of candidate identities (templates not in user's active set, computed from `IDENTITIES - currentActiveUserIdentities`)
  - Selected tile: hue-tinted background `oklch(0.94 0.06 hue)` + 2dp accent border + check badge top-right
  - Unselected: `surface` background + 2dp `outline-var` border
  - Each tile shows `IdentityAvatar(36dp)` + name (title-s) + description
- "Custom" dashed tile: rendered with edit icon + "Custom" + "Define your own."
  - Tapping → toast "Coming soon" (no nav change)
- Sticky bottom CTA: filled button "Continue with {selectedIdentity.name.lowercase()}" + arrow_forward icon

#### Step 2 — Recommended habits (`AddIdentityStep2Screen`)

Per design (screens.jsx:2271-2356):
- App bar: back arrow, title "Add {identity.name}"
- Header row: identity avatar 44dp + "What does a {name} do?" + "Pick the habits you want to track."
- List of recommended habit cards from `GetHabitTemplatesForIdentitiesUseCase` for the picked identity
  - Each card:
    - Square checkbox (22dp) tinted with identity hue when checked
    - `HabitGlyph` (40dp) tinted hue
    - Name (title-s)
    - "{target} × {threshold} {unit}" (body-s, on-surface-var)
    - "Already tracking · will associate" pill if user already has a habit with this `templateId`
- "+ Define a custom habit" text button — disabled, tap → toast "Coming soon"
- Sticky bottom bar:
  - Left: "{N} habits selected" + sub "{M} already tracking"
  - Right: filled button "Commit to {identity.name.lowercase()}"

#### `AddIdentityViewModel`

State:
```kotlin
data class AddIdentityUiState(
    val step: Int = 1,                                 // 1 or 2
    val candidates: List<Identity> = emptyList(),
    val selectedIdentity: Identity? = null,
    val recommendedHabits: List<HabitChoice> = emptyList(),
    val isCommitting: Boolean = false,
    val error: String? = null,
)

data class HabitChoice(
    val templateId: String,
    val name: String,
    val icon: String,
    val threshold: Double,
    val target: Int,
    val unit: String,
    val checked: Boolean,
    val alreadyTracking: Boolean,
)
```

Actions: `selectIdentity(identity)`, `advanceToStep2()`, `goBackToStep1()`, `toggleHabit(templateId)`, `commit()`, `cancel()`.

**Default state on entering step 2:** all recommended habits start `checked = true`. User unchecks anything they don't want. Reduces friction — typical user accepts the recommendations.

`commit()` calls `AddIdentityWithHabitsUseCase` with selected templateIds, then on success emits a one-shot `commitSuccess` event consumed by the screen for navigation.

#### `AddIdentityWithHabitsUseCase`

Single transaction:
1. Insert into `LocalUserIdentity`: `(userId, identityId, isActive=1, isPinned=0, whyText=null, removedAt=null, pendingSync=1, syncedAt=null)`.
2. For each selected `templateId`:
   - If user already has a `LocalHabit` with `templateId = ?` → insert `LocalHabitIdentity(habitId=existing.id, identityId, pendingSync=1)`.
   - Else → insert new `LocalHabit` (id=Uuid.random, templateId, defaults from template) + insert `LocalHabitIdentity` link.
3. Trigger `SyncTriggers.enqueue(POST_LOG)` after the transaction commits (handled in VM, not the use case).

### Pin / unpin

Two thin use cases:

```kotlin
class PinIdentityUseCase(repo: IdentityRepository) {
    suspend fun execute(userId: String, identityId: String) {
        repo.transaction {
            repo.clearPinForUser(userId)
            repo.setPinForIdentity(userId, identityId, isPinned = true)
        }
    }
}
class UnpinIdentityUseCase(repo: IdentityRepository) {
    suspend fun execute(userId: String, identityId: String) {
        repo.setPinForIdentity(userId, identityId, isPinned = false)
    }
}
```

UI per design (line 2143-2145):
- IdentityDetail bottom action row: tonal button.
  - When unpinned: outlined `push_pin` icon + "Pin to Home"
  - When pinned: filled `push_pin` icon + "Unpin from Home"
- ViewModel decides which use case to invoke based on the row's current `isPinned`.
- Home strip pinned chip: `push_pin` filled icon (11dp) inline with identity name (per design line 1632).
- IdentityList pinned card: tinted accent border `oklch(0.78 0.10 hue)` + `push_pin` icon next to name (per design line 1857-1875).

### Remove identity

Use case:
```kotlin
class RemoveIdentityUseCase(repo: IdentityRepository, clock: Clock) {
    suspend fun execute(userId: String, identityId: String) {
        repo.markUserIdentityRemoved(userId, identityId, removedAt = clock.now().toEpochMilliseconds())
        // markUserIdentityRemoved sets removedAt + isPinned=0 + pendingSync=1 in one UPDATE
    }
}
```

UI per design (line 2146-2151):
- IdentityDetail bottom: text button with `remove_circle_outline` icon, error color, label "Remove identity".
- Static help text below button: "Removing keeps your habits — they stay associated with the identities they support."
- Single tap removes — no confirm dialog (Q4 decision).
- On success the screen navigates back to IdentityList. Removed row no longer appears (`removedAt IS NULL` filter).

`habit_identities` rows are untouched. Habits linked only to the removed identity become orphans (still active in DB, still required by streak engine per Q4 C1). When Habit CRUD ships, user can clean these up.

### "Why this identity" reflection

Use case:
```kotlin
class UpdateIdentityWhyUseCase(repo: IdentityRepository) {
    suspend fun execute(userId: String, identityId: String, whyText: String?) {
        val normalized = whyText?.trim()?.takeIf { it.isNotEmpty() }
        repo.updateWhyText(userId, identityId, normalized)  // sets pendingSync=1
    }
}
```

UI per design (line 2123-2139):
- IdentityDetail section header "Why this identity" (title-m bold)
- Display state: rounded card with `surface-1` background + `outline-var` border, italic body-s text quoting the user's reflection. Placeholder when null: "Tap edit to add a reflection."
- "Edit" button below card: text button with `edit` icon + "Edit" label.

Edit state:
- Card body becomes `OutlinedTextField` (Compose Material 3), multi-line, max 6 lines, single-line min height ~80dp.
- Trailing actions: text "Cancel" + filled "Save".
- Save → use case → state flips back to display.
- Cancel → discard, state flips back to display.

ViewModel state additions:
- `IdentityDetailUiState.whyText: String?`
- `IdentityDetailUiState.isEditingWhy: Boolean`
- Methods: `startEditingWhy()`, `saveWhyText(text: String)`, `cancelEditingWhy()`.

## Nav wiring

### New route
```kotlin
object AddIdentity : Screen("add_identity")
```

### Entry
- IdentityList "Add identity" CTA card (per design line 1924) → `navController.navigate(Screen.AddIdentity.route)`.

### Exit
- Step 1 close OR step 1 back → `navController.popBackStack()` (returns to IdentityList).
- Step 2 back → in-VM step transition to step 1 (no nav change).
- Step 2 commit success → `navController.navigate(Screen.IdentityList.route) { popUpTo(Screen.IdentityList.route) { inclusive = false } }`.

### Existing routes — no signature change
- `Screen.IdentityDetail` gains pin / unpin / remove / edit-why VM actions; remove navigates `popBackStack()` to IdentityList on success.

## Data flow

### AddIdentityFlow commit
```
User taps Commit
  → AddIdentityViewModel.commit()
  → AddIdentityWithHabitsUseCase.execute(userId, identityId, selectedTemplateIds)
    └─ Single SQLDelight transaction:
       1. Insert LocalUserIdentity (pendingSync=1)
       2. For each template:
          - If existing habit with templateId → insert LocalHabitIdentity link
          - Else → insert LocalHabit + LocalHabitIdentity
  → ViewModel emits commitSuccess
  → Screen navigates to IdentityList
  → SyncTriggers.enqueue(POST_LOG) pushes the new rows
```

### Pin
```
User taps Pin
  → IdentityDetailViewModel.togglePin()
  → PinIdentityUseCase.execute(userId, identityId)
    └─ Transaction: clearPinForUser → setPinForIdentity
  → IdentityRepository.observeUserIdentities re-emits with new isPinned
  → IdentityDetail UI updates button label
  → Home strip + IdentityList re-render with push_pin icon
  → Sync trigger pushes the row update
```

### Remove
```
User taps Remove
  → IdentityDetailViewModel.removeIdentity()
  → RemoveIdentityUseCase.execute(userId, identityId)
    └─ markUserIdentityRemoved sets removedAt + isPinned=0 + pendingSync=1
  → ViewModel emits removeSuccess
  → Screen navigates back to IdentityList
  → IdentityList re-renders without the removed row
  → Streak engine unaffected (orphan habits stay required)
  → Sync trigger pushes the soft-delete
```

### Reflection edit
```
User taps Edit on Why card
  → IdentityDetailViewModel.startEditingWhy()
  → UI shows OutlinedTextField pre-populated with current whyText
User edits + taps Save
  → IdentityDetailViewModel.saveWhyText(text)
  → UpdateIdentityWhyUseCase.execute(userId, identityId, text)
  → Repository UPDATE row, pendingSync=1
  → observeUserIdentities re-emits
  → UI flips back to display state
  → Sync trigger pushes
```

## Error handling

- AddIdentityFlow commit failure (DB write or network sync): VM sets `error: String?` in state. UI shows snackbar; user retries via the same Commit button. State stays on step 2 — selections preserved.
- Pin/unpin/remove/save-why failures: log to logcat, show toast "Couldn't save — try again." Local optimistic update reverts on failure (repository's pendingSync row is untouched if the write fails before the DB commit).
- Sync push failures: existing pipeline retries on next trigger. No new error UI needed.

## Testing

### Unit tests (commonTest)

- `AddIdentityWithHabitsUseCaseTest`
  - Inserts a single `LocalUserIdentity` row with `isPinned=false, whyText=null, removedAt=null, pendingSync=1`.
  - Selected templateId not yet in user's habits → new `LocalHabit` + new `LocalHabitIdentity` link.
  - Selected templateId already in user's habits → only new `LocalHabitIdentity` link, no duplicate habit.
  - Empty selectedTemplateIds → no habit/link rows inserted.
  - All inserted rows have `pendingSync = 1`.
- `PinIdentityUseCaseTest`
  - Pins target identity, sets `isPinned = 1`, `pendingSync = 1`.
  - When another row already pinned, clears the previous `isPinned` to 0 in the same transaction.
  - Single-pin invariant: after execute, exactly one row per user has `isPinned = 1`.
- `UnpinIdentityUseCaseTest`
  - Sets `isPinned = 0`, `pendingSync = 1`.
  - No-op when already unpinned (still writes pendingSync=1, idempotent OK).
- `RemoveIdentityUseCaseTest`
  - Sets `removedAt` to non-null, `pendingSync = 1`.
  - Clears `isPinned` if the removed identity was pinned.
  - `LocalHabitIdentity` rows untouched (orphan link preserved).
- `UpdateIdentityWhyUseCaseTest`
  - Sets `whyText` to provided non-empty string.
  - Empty / whitespace-only string → null.
  - `pendingSync = 1`.
- `ObserveUserIdentitiesWithStatsUseCaseTest` (existing, augmented)
  - Confirm filter excludes rows with `removedAt != NULL`.

### ViewModel tests (androidApp test)

- `AddIdentityViewModelTest`
  - Step transitions: 1 → 2 → commit → success event.
  - `selectIdentity` updates `selectedIdentity`; `advanceToStep2` requires non-null selection.
  - `toggleHabit(templateId)` flips `checked` for matching `HabitChoice`.
  - Commit failure → `error` set, step stays at 2, selections preserved.
- `IdentityDetailViewModelTest`
  - `togglePin` calls `PinIdentityUseCase` when unpinned, `UnpinIdentityUseCase` when pinned.
  - `removeIdentity` invokes `RemoveIdentityUseCase`, emits `removeSuccess` event.
  - `startEditingWhy` flips `isEditingWhy = true`, populates `pendingWhyText` from current `whyText`.
  - `saveWhyText` invokes `UpdateIdentityWhyUseCase`, flips `isEditingWhy = false`.
  - `cancelEditingWhy` flips `isEditingWhy = false` without invoking the use case.

### Manual smoke (pre-merge)

- [ ] AddIdentity flow: open from IdentityList → pick template → see recommended habits → toggle a couple → Commit → IdentityList shows new identity at top.
- [ ] AddIdentity with template that overlaps an existing user habit (templateId match) → commit → habit gets linked to new identity, no duplicate row in `LocalHabit`.
- [ ] AddIdentity then immediately open IdentityDetail of the new identity → recommended habits visible in the linked-habits section.
- [ ] Tap "Custom" tile in step 1 → toast "Coming soon", no nav.
- [ ] Tap "+ Define a custom habit" in step 2 → toast "Coming soon", no nav.
- [ ] Pin identity from IdentityDetail → push_pin icon appears on Home strip + IdentityList card.
- [ ] Pin a different identity → previous unpinned, only new is pinned (single-pin invariant).
- [ ] Unpin identity → icon disappears.
- [ ] Remove identity (single tap) → identity gone from IdentityList. Home strip updates. Linked habits still present in Home habit list.
- [ ] Remove pinned identity → no orphan pin (no other identity becomes pinned automatically; pin slot is empty).
- [ ] Edit reflection → save → reopen IdentityDetail → reflection persisted.
- [ ] Edit reflection to empty string → save → reopen → placeholder shown.
- [ ] Multi-device sync (two emulator instances signed in as same user): pin on device A → trigger sync on device B → pin reflects on device B.
- [ ] Multi-device sync remove: remove on device A → device B sees identity disappear after sync.
- [ ] Build + unit tests + ViewModel tests green.

## Migration / rollout

- Schema migration applied at first app launch after update via SQLDelight `2.sqm`.
- Supabase migration applied via dashboard SQL Editor before the first authenticated user pulls.
- Order: ship Supabase migration first → deploy app build. App builds without the migration would still work (existing columns unchanged) but pushes would fail to populate the new columns.
- No data backfill — defaults handle existing rows (`is_pinned = FALSE`, `why_text = NULL`, `removed_at = NULL`).

## Open questions

None. Design locked.
