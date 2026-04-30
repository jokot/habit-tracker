# Phase 5b — Multi-Identity Foundation (data model + onboarding + read-only surfaces)

**Goal:** Replace single-identity onboarding with a multi-identity model. Persist user→identity and habit→identity associations. Surface identities on Home (strip) and You hub (card). No management screens yet — those ship in 5c.

**Worktree:** `.worktrees/phase5b-multi-identity`
**Branch:** `feature/phase5b-multi-identity`
**Design source:** `/tmp/habitto-design/habitto/project/screens.jsx` + `home.jsx` (`IdentityList`, `IdentityDetail`, `AddIdentityFlow`, `HabitFormMulti`, identities strip), `docs/design/claude-design-followup-multi-identity.md`, `docs/design/claude-design-followup-cleanup.md` §2 (multi is canonical).

---

## Why

The 5a/5a-2 phases shipped UI on top of a single-identity assumption that the cleanup followup explicitly retires: "Multi-identity is the only identity model." Single-identity is dead. A user can hold multiple identities at once (e.g. "Healthy person" + "Reader" + "Disciplined sleeper"). Each identity is its own commitment. They are equal — no primary/secondary in 5b (pinning lands later).

Currently identity is decorative — picked at onboarding, then discarded. No persisted user→identity link exists. `Goal` data class exists but is unused. This phase wires identity into the persisted model so future surfaces (IdentityList, IdentityDetail, per-identity progress) have data to consume.

## Scope

**In:**
- Data model — local + Supabase, with sync.
- Onboarding rewrite — multi-select identity step, union habit step, persisted associations.
- Home identity strip — read-only, inert tap.
- You hub identity card — read-only, inert tap.
- DB migration — wipe local on schema bump (dev-only; no production users).

**Out (5c):**
- IdentityList screen (full management list)
- IdentityDetail screen (zoomed view of one identity)
- AddIdentityFlow (post-onboarding identity addition)
- Custom identity creation (user-defined name/icon)
- Pinning / reordering identities
- Per-identity progress signals (heat, streak, log activity)
- Per-identity notification copy
- Per-identity exchange-rate
- Habit form multi-select identity field (gated on Habit CRUD, separate phase)

## Open-question stances (matches followup-multi-identity §"Open design questions")

1. **Rankable identities?** No in 5b. Order = `addedAt`. Pinning + drag-reorder ship in 5c.
2. **Habit log counts toward each linked identity's progress?** Yes, intentional. Implementation lands in 5c when per-identity progress signals ship.
3. **Home strip with 5+ identities?** Show first 3 by `addedAt`, then "+N more" pill. Pill is inert in 5b; opens IdentityList in 5c.
4. **Identity icons?** Designer-curated set only in 5b. User-uploadable / custom icons defer to 5c (gated on custom identity creation).
5. **Onboarding with 4 identities → huge habit list?** Accept it. Render as a single scrollable union list with overlap badges. No truncation in 5b. If pain emerges in real use, revisit.

## Architecture

### Data model

Two new join tables. Both local (SQLDelight) and remote (Supabase Postgres). Composite primary keys. `LocalIdentity` (existing seed reference) is unchanged.

**`LocalUserIdentity`** — which identities a user holds.
| col | type | notes |
|---|---|---|
| `userId` | TEXT NOT NULL | part of PK |
| `identityId` | TEXT NOT NULL | part of PK; references seed `LocalIdentity.id` |
| `addedAt` | INTEGER NOT NULL | epoch ms; drives strip ordering |
| `syncedAt` | INTEGER NULL | matches existing pattern |

**`LocalHabitIdentity`** — which identity(ies) a habit serves.
| col | type | notes |
|---|---|---|
| `habitId` | TEXT NOT NULL | part of PK |
| `identityId` | TEXT NOT NULL | part of PK |
| `addedAt` | INTEGER NOT NULL | epoch ms |
| `syncedAt` | INTEGER NULL | |

Indexes: `idx_user_identity_user (userId)`, `idx_habit_identity_habit (habitId)`, `idx_habit_identity_identity (identityId)`. The last index is for 5c reverse lookup (habits linked to one identity); kept here as low-cost prep.

### Repository

```kotlin
interface IdentityRepository {
    fun observeAllIdentities(): Flow<List<Identity>>                                    // seed list
    fun observeUserIdentities(userId: String): Flow<List<Identity>>                      // resolved
    suspend fun setUserIdentities(userId: String, identityIds: Set<String>): Result<Unit>
    suspend fun linkHabitToIdentities(habitId: String, identityIds: Set<String>): Result<Unit>
    fun observeHabitsForIdentity(userId: String, identityId: String): Flow<List<Habit>>  // 5c-ready
}
```

Backed by a new `LocalIdentityDataSource` wrapping SQLDelight `LocalUserIdentity` / `LocalHabitIdentity` queries, composed with the existing `LocalHabitDataSource` for the habit-side resolution.

`setUserIdentities` semantics: replace — delete rows in `LocalUserIdentity WHERE userId = ?` not in the new set, insert missing ones, leave already-present rows untouched. Used by onboarding finish to flip from prior single-identity state if it existed.

### Use cases

**New:**
- `SetupUserIdentitiesUseCase(userId, identityIds)` → calls `IdentityRepository.setUserIdentities`.
- `GetUserIdentitiesUseCase(userId)` → returns `Flow<List<Identity>>` for Home strip + You hub card.
- `LinkOnboardingHabitsToIdentitiesUseCase(userId, templateIdToHabitId: Map<String, String>, selectedIdentityIds: Set<String>)`:
  - For each `(templateId, habitId)` pair, look up which selected identities recommend `templateId` via `SeedData.identityHabitMap`.
  - Insert one `LocalHabitIdentity` row per (habitId, identityId) pair.
  - `SetupUserHabitsUseCase` is extended to return `Map<templateId, habitId>` so the link step can resolve habit ids without a second DB read.

**Replaces:**
- `GetHabitTemplatesForIdentityUseCase` → `GetHabitTemplatesForIdentitiesUseCase(identityIds: Set<String>)`. Returns `List<TemplateWithIdentities>` where `TemplateWithIdentities = (template: HabitTemplate, recommendedBy: Set<Identity>)`. Union, deduped by template id.

**Touched:**
- `SetupUserHabitsUseCase.execute` returns `Result<Map<String, String>>` (templateId → habitId) so the link use case can use it. (Was `Result<Unit>`.)

### Sync

Extend `PostgrestSupabaseSyncClient`:
- `pushUserIdentities(userId)` — POST/UPSERT unsynced rows, mark `syncedAt`.
- `pullUserIdentities(userId)` — GET, merge into local via `mergePulledUserIdentity`.
- `pushHabitIdentities(userId)` — same pattern, scoped via habits owned by user.
- `pullHabitIdentities(userId)` — same.

Hook into existing sync orchestration: pull on app start (after auth), push on local write (post-onboarding), retry on next foreground / network event.

Last-write-wins on conflict (existing pattern). No CRDT.

### Migration

Bump SQLDelight schema version. On version mismatch, drop + recreate all tables. Re-seed `LocalIdentity` from `SeedData.identities` on first open (existing pattern).

User impact: existing dev installs lose all local data and re-onboard. No production users exist, so this is safe. Document in commit and PR description.

### Sign-out / Sign-in

- **Sign out:** extend existing local-wipe routine to also delete `LocalUserIdentity WHERE userId = ?` and `LocalHabitIdentity WHERE habitId IN (SELECT id FROM LocalHabit WHERE userId = ?)`.
- **Sign in to existing account:** pull `user_identities` + `habit_identities` from Supabase, merge.

## Schema (DDL)

### SQLDelight (`HabitTrackerDatabase.sq`)

```sql
CREATE TABLE IF NOT EXISTS LocalUserIdentity (
    userId TEXT NOT NULL,
    identityId TEXT NOT NULL,
    addedAt INTEGER NOT NULL,
    syncedAt INTEGER,
    PRIMARY KEY (userId, identityId)
);

CREATE TABLE IF NOT EXISTS LocalHabitIdentity (
    habitId TEXT NOT NULL,
    identityId TEXT NOT NULL,
    addedAt INTEGER NOT NULL,
    syncedAt INTEGER,
    PRIMARY KEY (habitId, identityId)
);

CREATE INDEX IF NOT EXISTS idx_user_identity_user ON LocalUserIdentity(userId);
CREATE INDEX IF NOT EXISTS idx_habit_identity_habit ON LocalHabitIdentity(habitId);
CREATE INDEX IF NOT EXISTS idx_habit_identity_identity ON LocalHabitIdentity(identityId);
```

Queries (SQLDelight named queries):
- `upsertUserIdentity`, `getUserIdentities(userId)`, `deleteUserIdentitiesForUser(userId)`, `deleteUserIdentity(userId, identityId)`, `getUnsyncedUserIdentities(userId)`, `markUserIdentitySynced(userId, identityId, syncedAt)`, `mergePulledUserIdentity`.
- Mirror set for `HabitIdentity`: `upsertHabitIdentity`, `getHabitIdentities(habitId)`, `getHabitsForIdentity(userId, identityId)` (joins `LocalHabit`), `deleteHabitIdentitiesForHabit(habitId)`, `getUnsyncedHabitIdentities(userId)` (joins `LocalHabit` for user scope), `markHabitIdentitySynced`, `mergePulledHabitIdentity`.

### Supabase Postgres

```sql
CREATE TABLE user_identities (
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    identity_id TEXT NOT NULL,
    added_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (user_id, identity_id)
);

CREATE TABLE habit_identities (
    habit_id UUID NOT NULL REFERENCES habits(id) ON DELETE CASCADE,
    identity_id TEXT NOT NULL,
    added_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (habit_id, identity_id)
);

CREATE INDEX idx_user_identities_user ON user_identities(user_id);
CREATE INDEX idx_habit_identities_habit ON habit_identities(habit_id);
```

RLS:
- `user_identities` SELECT/INSERT/DELETE — `auth.uid() = user_id`.
- `habit_identities` SELECT/INSERT/DELETE — `EXISTS (SELECT 1 FROM habits h WHERE h.id = habit_identities.habit_id AND h.user_id = auth.uid())`.

## UI surfaces

### Onboarding step 1 — Identity picker

Existing 2-col grid layout stays. Behavior changes:
- Toggle membership in `selectedIdentityIds: Set<String>` (was single replace).
- Multiple cards can show selected state simultaneously (hue-tinted bg + 2dp border + check badge — existing visual).
- Below subtitle, render advisory hint: `"Pick 1–4 to start. You can add more later."` (small text, onSurfaceVariant).
- `Next` button disabled until `selectedIdentityIds.isNotEmpty()`.

### Onboarding step 2 — Habit picker

Same row layout as 5a-2. Two changes:
- **Source:** `habitTemplates: List<TemplateWithIdentities>` — union across selected identities, deduped by template id, ordered by first-identity-recommended order.
- **Overlap badge:** when `template.recommendedBy.size > 1`, render below the existing subtitle:
  `"Recommended by: ${template.recommendedBy.joinToString(" · ") { it.name }}"`
  Style: `bodySmall`, color `onSurfaceVariant`, single-line, no wrap. When `size == 1`, no badge.

Identity set changes (going back, toggling) rebuild the templates list. `selectedTemplateIds` is filtered to keep only those still in the union.

### Onboarding finish

Sequence (replaces current `OnboardingViewModel.finish()`):

1. `SetupUserIdentitiesUseCase(userId, selectedIdentityIds)` — persist user→identity rows.
2. `SetupUserHabitsUseCase(userId, selectedTemplates)` — returns `Map<templateId, habitId>`.
3. `LinkOnboardingHabitsToIdentitiesUseCase(userId, templateIdToHabitId, selectedIdentityIds)` — for each habit, link to all selected identities that recommend its template.
4. `SetupUserWantActivitiesUseCase(userId, selectedActivities)` — unchanged.
5. Emit `_finished`.

Failure handling: existing on-failure pattern — surface `error` in UI state, abort. Acceptable for 5b. (Idempotency: re-running onboarding after partial failure is OK because all writes are upserts keyed on stable ids.)

### Home — Identity strip

**Position:** between top bar and DailyStatusCard. 16dp horizontal padding, 12dp bottom padding (matches canvas `home.jsx` L142–184).

**Layout:**
- Row, gap 8dp, wraps if needed (Compose `FlowRow`).
- Leading "I AM" label: 12sp 500, onSurfaceVariant, uppercase, 0.3sp letterSpacing, 4dp right padding.
- First 3 identities (by `addedAt`) → identity chips.
- If `userIdentities.size > 3` → "+N more" pill after the 3 chips.
- If `userIdentities.isEmpty()` → entire strip is hidden (defensive — shouldn't happen post-onboarding gate).

**Identity chip:**
- Pill (`RoundedCornerShape(999.dp)`), 1dp `outlineVariant` border, `surface` bg.
- `Row(verticalAlignment = CenterVertically, horizontalArrangement = spacedBy(6.dp))`, padding `start = 4.dp, end = 10.dp, vertical = 4.dp`.
- 22dp `HabitGlyph(icon = identity.icon, hue = IdentityHue.forIdentityId(...), size = 22.dp)`.
- Identity first-word name (`identity.name.split(" ").first()`), `12sp 600`, `onSurface`.
- No ripple, no clickable in 5b. Wire to navigation in 5c.

**+N more pill:** same shape as chip, no icon, label `"+${userIdentities.size - 3} more"`, `12sp 600`, `onSurfaceVariant`. Inert.

**Pinning:** not in 5b. Canvas pin badge is not rendered. No data field for pin.

### You hub — Identity card

**Position:** new first item in You list, before the "Account" `SectionHeader`.

**Layout:**
- Surface card: `RoundedCornerShape(16.dp)`, 1dp `outlineVariant` border, `surface` bg, `fillMaxWidth`, 16dp horizontal margin (matches existing You list horizontal rhythm), 16dp internal padding.
- Row, gap 12dp, `verticalAlignment = CenterVertically`:
  1. **Avatar stack:** Box with up to 3 `HabitGlyph(size = 32.dp)` overlapping by 50% (12dp horizontal offset per avatar). If `userIdentities.size > 3`, render 4th slot as "+N" circle: 32dp circle, surface bg, 1dp outlineVariant border, count text `bodySmall onSurfaceVariant`. Z-order: first identity on top.
  2. **Column(weight = 1f):**
     - `"Identities"` titleSmall onSurface
     - `"${userIdentities.size} active"` bodySmall onSurfaceVariant
  3. **Trailing:** `Icons.AutoMirrored.Outlined.KeyboardArrowRight` chevron, onSurfaceVariant, 20dp. Inert in 5b.
- Whole card: no ripple, no clickable in 5b.

### Component reuse

- `HabitGlyph` reused at sizes 22dp (Home strip) and 32dp (You stack).
- New private composables:
  - `IdentityStrip` (HomeScreen.kt-internal)
  - `IdentityChip` (HomeScreen.kt-internal)
  - `IdentityHubCard` (YouHubScreen.kt-internal)
  - `IdentityAvatarStack` (YouHubScreen.kt-internal)

## Testing

### Domain (commonTest, JVM)

- `IdentityRepository` (in-memory SQLDelight driver):
  - `setUserIdentities` is a replace (insert new, delete absent).
  - `linkHabitToIdentities` upserts; idempotent re-link.
  - `observeUserIdentities` emits ordered by `addedAt`.
  - `observeHabitsForIdentity` returns only linked habits.

- `LinkOnboardingHabitsToIdentitiesUseCase`:
  - Habit recommended by 2 selected identities → 2 join rows.
  - Habit recommended by 2 identities, only 1 selected → 1 row.
  - Habit recommended by 0 selected identities (defensive) → 0 rows, no error.

- `GetHabitTemplatesForIdentitiesUseCase`:
  - Two identities sharing template X → returned once with `recommendedBy = {A, B}`.
  - Empty input → empty list.

### Sync

`PostgrestSupabaseSyncClient` (existing test pattern with mocked Postgrest):
- `pushUserIdentities` posts unsynced rows, marks `syncedAt`.
- `pullUserIdentities` upserts pulled rows.
- Same for `habit_identities`.

### UI (Robolectric)

- `OnboardingViewModel`:
  - `toggleIdentity` adds + removes from set.
  - Step 2 templates union dedupes correctly.
  - Going back from step 2 → step 1 → changing identities rebuilds templates and prunes `selectedTemplateIds`.
  - `finish()` calls use cases in order: setUserIdentities → setupHabits → linkHabitsToIdentities → setupWants.
  - `finish()` failure surfaces error, aborts.

- `HomeViewModel`:
  - `userIdentities` flow propagates to UI state.
  - Empty list → strip hidden.

- `YouHubViewModel`:
  - Identity card visible when authenticated; count matches user identities.

### Skipped

- No UI snapshot/render tests (project doesn't use them).
- No live Supabase integration test (existing pattern uses mocks).

### Coverage target

Match existing — domain + sync exercised end-to-end, ViewModel state transitions covered, no UI render assertions.

## File structure

### Created

| File | Responsibility |
|---|---|
| `mobile/shared/src/commonMain/kotlin/com/habittracker/data/local/LocalIdentityDataSource.kt` | SQLDelight wrapper for user/habit identity tables |
| `mobile/shared/src/commonMain/kotlin/com/habittracker/data/repository/IdentityRepositoryImpl.kt` | `IdentityRepository` impl |
| `mobile/shared/src/commonMain/kotlin/com/habittracker/domain/repository/IdentityRepository.kt` | Repository interface |
| `mobile/shared/src/commonMain/kotlin/com/habittracker/domain/usecase/SetupUserIdentitiesUseCase.kt` | |
| `mobile/shared/src/commonMain/kotlin/com/habittracker/domain/usecase/GetUserIdentitiesUseCase.kt` | |
| `mobile/shared/src/commonMain/kotlin/com/habittracker/domain/usecase/LinkOnboardingHabitsToIdentitiesUseCase.kt` | |
| `mobile/shared/src/commonMain/kotlin/com/habittracker/domain/usecase/GetHabitTemplatesForIdentitiesUseCase.kt` | replaces single-identity variant |
| `mobile/shared/src/commonMain/kotlin/com/habittracker/domain/model/TemplateWithIdentities.kt` | Onboarding step 2 row model |
| `supabase/migrations/<timestamp>_user_identities_habit_identities.sql` | Postgres tables + RLS |
| Test files mirroring all of the above | |

### Modified

| File | What changes |
|---|---|
| `mobile/shared/src/commonMain/sqldelight/com/habittracker/data/local/HabitTrackerDatabase.sq` | New tables + queries; bump schema version |
| `mobile/shared/src/commonMain/kotlin/com/habittracker/data/sync/PostgrestSupabaseSyncClient.kt` | Push/pull for two new tables |
| `mobile/shared/src/commonMain/kotlin/com/habittracker/data/sync/SyncOrchestrator.kt` | Wire new push/pull into existing run sequence |
| `mobile/shared/src/commonMain/kotlin/com/habittracker/domain/usecase/SetupUserHabitsUseCase.kt` | Return `Map<templateId, habitId>` instead of `Unit` |
| `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/AppContainer.kt` | Wire new repo/use cases |
| `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/onboarding/OnboardingViewModel.kt` | `selectedIdentityId` → `selectedIdentityIds: Set`; `habitTemplates` → `List<TemplateWithIdentities>`; finish-sequence updates |
| `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/onboarding/OnboardingScreen.kt` | Step 1 multi-select toggle; step 2 overlap badge |
| `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/home/HomeScreen.kt` | Render `IdentityStrip` between top bar and DailyStatusCard |
| `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/home/HomeViewModel.kt` | Expose `userIdentities` Flow |
| `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/you/YouHubScreen.kt` | Render `IdentityHubCard` above Account section |
| `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/you/YouHubViewModel.kt` | Expose `userIdentities` Flow |
| `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/HabitTrackerApplication.kt` | If schema migration callback lives here, hook the wipe-on-version-bump |

---

## Acceptance

- New onboarding requires ≥1 identity; supports up to all seeded.
- After onboarding, user→identity and habit→identity rows exist locally and on Supabase (when authenticated).
- Sign-out wipes local rows for the signed-out user.
- Sign-in pulls Supabase rows into local.
- Home shows identity strip (or hides if empty); chips render but don't navigate.
- You hub shows identity card with avatar stack + count; card doesn't navigate.
- Build green; all phase 3/4/5a/5a-2 unit tests still pass; new tests pass.
- Manual smoke: light + dark, multiple identities at onboarding, multi-device sync verified manually.
