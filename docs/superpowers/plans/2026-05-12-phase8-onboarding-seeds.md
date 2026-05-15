# Phase 8 — Onboarding Redesign + Identity / Habit Seed Update Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Match canvas v4 onboarding flow + identity/habit catalog. 13 identities with Material icons + per-identity hue; ~80-template habit catalog with `alsoFor` shared-recommendation semantics; modular per-step onboarding composables matching canvas layout and copy.

**Architecture:** Schema migration adds `hue: Int` to `LocalIdentity` and `iconName: String?` to `LocalHabitTemplate`. Domain models gain those fields. `Identity.icon` flips semantic from emoji to Material icon name (server backfill by stable id). `SeedData` rebuilt from canvas (`/tmp/habitto-design/habitto/project/screens-v3.jsx:1117` for HABIT_TEMPLATES, `shared.jsx:9` for IDENTITIES). `IdentityHue` helper rewrites to read from `Identity.hue` instead of hard-coded map. `OnboardingScreen` shrinks to scaffold; per-step composables `IdentityStep`, `HabitsStep`, `WantsStep`, `SignInStep` live under `ui/onboarding/steps/`. Sync DTOs extended. Server migration adds columns + backfills 8 existing identities.

**Tech Stack:** Kotlin Multiplatform, SQLDelight, Compose Material 3, kotlinx.datetime, Robolectric + JUnit4, Supabase Postgres.

**Branch:** `feature/phase8-onboarding-seeds` (worktree `.worktrees/phase8-onboarding-seeds`).

**Spec:** [`docs/superpowers/specs/2026-05-12-phase8-onboarding-seeds-design.md`](../specs/2026-05-12-phase8-onboarding-seeds-design.md).

**Canvas source of truth (extracted to `/tmp/habitto-design/habitto/project/`):**
- `shared.jsx:9` — IDENTITIES (13 entries, full canonical list).
- `screens-v3.jsx:1117` — HABIT_TEMPLATES (full ~80-template catalog grouped by identity, with `alsoFor`).
- `screens.jsx:23` — OnboardStep (step 3 + 4 layouts + step copy).
- `screens.jsx:2753` — OnboardIdentityMulti (step 1).
- `screens-v2.jsx:1011` — OnboardHabitsMulti (step 2 with "Recommended by" pills).

---

## File Map

**Schema:**
- Create: `mobile/shared/src/commonMain/sqldelight/com/habittracker/data/local/migrations/8.sqm`
- Modify: `mobile/shared/src/commonMain/sqldelight/com/habittracker/data/local/HabitTrackerDatabase.sq`
- Create: `supabase/migrations/20260512000000_phase8_identity_hue_habit_icon.sql`

**Domain models:**
- Modify: `mobile/shared/src/commonMain/kotlin/com/habittracker/domain/model/Identity.kt`
- Modify: `mobile/shared/src/commonMain/kotlin/com/habittracker/domain/model/HabitTemplate.kt`

**Repos + DTOs:**
- Modify: `mobile/shared/src/commonMain/kotlin/com/habittracker/data/repository/LocalIdentityRepository.kt`
- Modify: `mobile/shared/src/commonMain/kotlin/com/habittracker/data/repository/LocalHabitTemplateRepository.kt` (whatever the current name is — confirm during Task 4)
- Modify: `mobile/shared/src/commonMain/kotlin/com/habittracker/data/sync/PostgrestSupabaseSyncClient.kt`

**Seed:**
- Modify: `mobile/shared/src/commonMain/kotlin/com/habittracker/data/local/SeedData.kt`

**Icon + hue helpers:**
- Modify: `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/components/HabitGlyph.kt` (rewrites `IdentityHue` to read from `Identity.hue` directly + adds `materialIconFor(name: String)` resolver).
- Create: `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/components/MaterialIconResolver.kt` (maps Material icon string names → `ImageVector` for the ~60 distinct icons used by seeds; falls back to `Icons.Default.LabelImportant`).

**Onboarding:**
- Modify: `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/onboarding/OnboardingScreen.kt` (scaffold only).
- Modify: `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/onboarding/OnboardingViewModel.kt` (LinkedHashSet for pick order).
- Create: `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/onboarding/steps/IdentityStep.kt`
- Create: `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/onboarding/steps/HabitsStep.kt`
- Create: `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/onboarding/steps/WantsStep.kt`
- Create: `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/onboarding/steps/SignInStep.kt`

**Tests:**
- Modify: `mobile/shared/src/commonTest/kotlin/com/habittracker/domain/usecase/SetupUserIdentitiesUseCaseTest.kt`
- Modify: `mobile/shared/src/commonTest/kotlin/com/habittracker/domain/usecase/GetHabitTemplatesForIdentitiesUseCaseTest.kt` (if exists; otherwise create)
- Modify: `mobile/androidApp/src/test/kotlin/com/jktdeveloper/habitto/ui/onboarding/OnboardingViewModelTest.kt`

---

## Task 1: Schema migration 8 + table column adds

**Files:**
- Create: `mobile/shared/src/commonMain/sqldelight/com/habittracker/data/local/migrations/8.sqm`
- Modify: `mobile/shared/src/commonMain/sqldelight/com/habittracker/data/local/HabitTrackerDatabase.sq`

- [ ] **Step 1: Write migration 8**

`mobile/shared/src/commonMain/sqldelight/com/habittracker/data/local/migrations/8.sqm`:
```sql
-- Phase 8: Identity gains `hue` (OKLCH 0..360); HabitTemplate gains `iconName`.
-- Existing rows keep their data; reconcile path overwrites icon + hue from
-- canvas catalog on next startup. Per-row backfill of canonical hue is done
-- server-side; local clients pull updated rows via sync.

ALTER TABLE LocalIdentity ADD COLUMN hue INTEGER NOT NULL DEFAULT 142;
ALTER TABLE LocalHabitTemplate ADD COLUMN iconName TEXT;
```

- [ ] **Step 2: Update `.sq` table definitions**

In `mobile/shared/src/commonMain/sqldelight/com/habittracker/data/local/HabitTrackerDatabase.sq`, add the new columns to the `CREATE TABLE IF NOT EXISTS LocalIdentity` block (after `icon`):
```sql
CREATE TABLE IF NOT EXISTS LocalIdentity (
    id TEXT NOT NULL PRIMARY KEY,
    name TEXT NOT NULL,
    description TEXT NOT NULL,
    icon TEXT NOT NULL,
    hue INTEGER NOT NULL DEFAULT 142
);
```

And `LocalHabitTemplate`:
```sql
CREATE TABLE IF NOT EXISTS LocalHabitTemplate (
    id TEXT NOT NULL PRIMARY KEY,
    name TEXT NOT NULL,
    unit TEXT NOT NULL,
    defaultThreshold REAL NOT NULL,
    defaultDailyTarget INTEGER NOT NULL,
    iconName TEXT,
    isCustom INTEGER NOT NULL DEFAULT 0,
    createdByUserId TEXT
);
```

Update `upsertIdentity` and `upsertHabitTemplate` queries to include the new columns. Run `rtk grep -n "upsertIdentity\|upsertHabitTemplate\|mergePulledIdentity\|mergePulledHabitTemplate" mobile/shared/src/commonMain/sqldelight/com/habittracker/data/local/HabitTrackerDatabase.sq` to find every binding site; each gets the new placeholder.

- [ ] **Step 3: Build to regenerate SQLDelight**

```bash
cd /Users/jokot/dev/habit-tracker/.worktrees/phase8-onboarding-seeds
rtk ./gradlew :mobile:shared:generateCommonMainHabitTrackerDatabaseInterface :mobile:shared:verifyCommonMainHabitTrackerDatabaseMigration 2>&1 | tail -10
```

Expected: BUILD SUCCESSFUL on both. Migration verify ensures the migrated schema matches the fresh `.sq` definition.

- [ ] **Step 4: Commit**

```bash
rtk git add mobile/shared/src/commonMain/sqldelight/com/habittracker/data/local/migrations/8.sqm \
    mobile/shared/src/commonMain/sqldelight/com/habittracker/data/local/HabitTrackerDatabase.sq
rtk git commit -m "feat(identity): schema 8 — add Identity.hue + HabitTemplate.iconName"
```

---

## Task 2: Domain model field additions

**Files:**
- Modify: `mobile/shared/src/commonMain/kotlin/com/habittracker/domain/model/Identity.kt`
- Modify: `mobile/shared/src/commonMain/kotlin/com/habittracker/domain/model/HabitTemplate.kt`

- [ ] **Step 1: Update Identity**

Replace `Identity.kt`:
```kotlin
package com.habittracker.domain.model

data class Identity(
    val id: String,
    val name: String,
    val description: String,
    val icon: String,   // Material icon name (e.g. "menu_book"); semantic flipped from emoji.
    val hue: Int = 142, // OKLCH hue 0..360 for color tint.
)
```

- [ ] **Step 2: Update HabitTemplate**

Replace `HabitTemplate.kt`:
```kotlin
package com.habittracker.domain.model

data class HabitTemplate(
    val id: String,
    val name: String,
    val unit: String,
    val defaultThreshold: Double,
    val defaultDailyTarget: Int,
    val iconName: String? = null,
    val isCustom: Boolean = false,
    val createdByUserId: String? = null,
)
```

- [ ] **Step 3: Compile (broken intermediate commit OK)**

```bash
rtk ./gradlew :mobile:shared:compileCommonMainKotlinMetadata 2>&1 | tail -20
```

Expected: build BREAKS at every site that constructs `Identity(...)` or `HabitTemplate(...)`. Capture the file list in the report — Tasks 3, 4, 5 fix those sites.

- [ ] **Step 4: Commit**

```bash
rtk git add mobile/shared/src/commonMain/kotlin/com/habittracker/domain/model/Identity.kt \
    mobile/shared/src/commonMain/kotlin/com/habittracker/domain/model/HabitTemplate.kt
rtk git commit -m "refactor(identity): domain Identity.hue + HabitTemplate.iconName"
```

---

## Task 3: SeedData — 13 identities + canvas habit catalog

**Files:**
- Modify: `mobile/shared/src/commonMain/kotlin/com/habittracker/data/local/SeedData.kt`

- [ ] **Step 1: Rebuild identity seed**

In `SeedData.kt`, replace the `identities` list with the canonical 13 from spec §Catalog. Stable UUIDs `00000000-0000-0000-0000-000000000001..0013`. Each Identity uses Material icon name + hue:
```kotlin
val identities: List<Identity> = listOf(
    Identity("00000000-0000-0000-0000-000000000001", "Reader",           "Build a reading habit to expand knowledge and vocabulary.",  icon = "menu_book",        hue = 30),
    Identity("00000000-0000-0000-0000-000000000002", "Builder",          "Develop your craft as a software developer.",                icon = "code",             hue = 225),
    Identity("00000000-0000-0000-0000-000000000003", "Athlete",          "Build physical strength and endurance.",                     icon = "directions_run",   hue = 5),
    Identity("00000000-0000-0000-0000-000000000004", "Writer",           "Express yourself through consistent writing practice.",      icon = "edit_note",        hue = 285),
    Identity("00000000-0000-0000-0000-000000000005", "Learner",          "Stay curious and keep learning every day.",                  icon = "school",           hue = 190),
    Identity("00000000-0000-0000-0000-000000000006", "Minimalist",       "Simplify your space and digital life.",                      icon = "eco",              hue = 130),
    Identity("00000000-0000-0000-0000-000000000007", "Devotee",          "Deepen your spiritual practice.",                            icon = "self_improvement", hue = 255),
    Identity("00000000-0000-0000-0000-000000000008", "Health-Conscious", "Build healthy daily habits for long-term wellness.",         icon = "favorite",         hue = 155),
    Identity("00000000-0000-0000-0000-000000000009", "Creator",          "Make things — visual, audio, or written art.",               icon = "palette",          hue = 315),
    Identity("00000000-0000-0000-0000-000000000010", "Saver",            "Build wealth and break expensive habits.",                   icon = "savings",          hue = 75),
    Identity("00000000-0000-0000-0000-000000000011", "Connector",        "Invest in people who matter.",                               icon = "handshake",        hue = 350),
    Identity("00000000-0000-0000-0000-000000000012", "Adventurer",       "Try new things on purpose.",                                 icon = "explore",          hue = 170),
    Identity("00000000-0000-0000-0000-000000000013", "Chef",             "Cook your way to better food and lower cost.",               icon = "restaurant",       hue = 45),
)
```

- [ ] **Step 2: Rebuild habit template catalog**

Open `/tmp/habitto-design/habitto/project/screens-v3.jsx:1117` and read the full `HABIT_TEMPLATES` object (lines 1117–1233). Each canvas entry like:
```jsx
{ id: 't-read-book',   name: 'Read book',  icon: 'menu_book', threshold: 3, target: 3, unit: 'pages' },
{ id: 't-read-article', name: 'Read article', icon: 'article', threshold: 5, target: 2, unit: 'min', alsoFor: ['learner'] },
```

becomes a Kotlin `HabitTemplate` entry. UUID strategy: assign sequential stable UUIDs `10000000-0000-0000-0000-000000000001..` to each canvas template in order of appearance per the canvas. Existing 31 template UUIDs that map cleanly by NAME (e.g. canvas "Read book" ↔ existing "Read book / Kindle" → new clean "Read book") use the same UUID as today where possible to preserve referential continuity in `Habit.templateId`.

Concrete mapping for the first identity group as illustration:
```kotlin
// reader bucket
HabitTemplate("10000000-0000-0000-0000-000000000001", "Read book",            "pages", 3.0,  3, iconName = "menu_book"),
HabitTemplate("10000000-0000-0000-0000-000000000002", "Read on Kindle",       "min",   10.0, 2, iconName = "menu_book"),
HabitTemplate("10000000-0000-0000-0000-000000000003", "Read article",         "min",   5.0,  2, iconName = "article"),    // alsoFor: learner
HabitTemplate("10000000-0000-0000-0000-000000000004", "Read research paper",  "min",   10.0, 1, iconName = "description"),
HabitTemplate("10000000-0000-0000-0000-000000000005", "Audiobook listen",     "min",   15.0, 2, iconName = "headphones"),
HabitTemplate("10000000-0000-0000-0000-000000000006", "Re-read passage",      "min",   5.0,  1, iconName = "menu_book"),
```

Continue for all 13 identity buckets per canvas line 1117-1233. For full enumeration the implementer reads the canvas file directly and ports each row. UUID counter increments globally (not per-bucket) so each template has a unique stable id.

The total is ~80 templates. The implementer is responsible for porting all of them; this plan is the orchestration document, not the catalog itself. **Always source from canvas; do not improvise template fields.**

- [ ] **Step 3: Rebuild identityHabitMap**

For each canvas template, the natural identity bucket gets the template id. If the canvas entry has `alsoFor: ['health']`, that identity's bucket also gets the same template id:
```kotlin
val identityHabitMap: Map<String, List<String>> = mapOf(
    "00000000-0000-0000-0000-000000000001" to listOf(  // Reader
        "10000000-0000-0000-0000-000000000001",  // Read book
        "10000000-0000-0000-0000-000000000002",  // Read on Kindle
        "10000000-0000-0000-0000-000000000003",  // Read article (alsoFor learner)
        "10000000-0000-0000-0000-000000000004",  // Read research paper
        "10000000-0000-0000-0000-000000000005",  // Audiobook listen
        "10000000-0000-0000-0000-000000000006",  // Re-read passage
    ),
    "00000000-0000-0000-0000-000000000005" to listOf(  // Learner
        // ... learner bucket templates
        "10000000-0000-0000-0000-000000000003",  // Read article (alsoFor reader — also here)
        // ... other learner templates
    ),
    // ... all 13 identity buckets
)
```

The `alsoFor` semantics: a template appears in EACH identity bucket the canvas lists for it. The "primary" bucket is the one the template is natively defined under in canvas; "alsoFor" adds extra bucket memberships.

- [ ] **Step 4: Build shared**

```bash
rtk ./gradlew :mobile:shared:compileCommonMainKotlinMetadata 2>&1 | tail -15
```

Expected: SeedData compiles cleanly. Remaining errors are in callers — Tasks 4, 5, 6, 7 fix them.

- [ ] **Step 5: Commit**

```bash
rtk git add mobile/shared/src/commonMain/kotlin/com/habittracker/data/local/SeedData.kt
rtk git commit -m "feat(identity): 13-identity + ~80-template canvas seed catalog"
```

---

## Task 4: Repo mappers — Identity + HabitTemplate

**Files:**
- Modify: `mobile/shared/src/commonMain/kotlin/com/habittracker/data/repository/LocalIdentityRepository.kt`
- Modify: `mobile/shared/src/commonMain/kotlin/com/habittracker/data/repository/LocalHabitTemplateRepository.kt` (confirm filename during step 1; may be different — search if needed)

- [ ] **Step 1: Update LocalIdentityRepository**

Find `toDomain()` mapper and `upsertIdentity(...)` call sites. Update `toDomain` to:
```kotlin
private fun LocalIdentityRow.toDomain() = Identity(
    id = id,
    name = name,
    description = description,
    icon = icon,
    hue = hue.toInt(),
)
```

And `upsertIdentities(...)` call now passes `identity.hue.toLong()` as the 5th placeholder:
```kotlin
queries.upsertIdentity(
    id = identity.id,
    name = identity.name,
    description = identity.description,
    icon = identity.icon,
    hue = identity.hue.toLong(),
)
```

Confirm the SQL query order matches (id, name, description, icon, hue).

- [ ] **Step 2: Update LocalHabitTemplateRepository**

If it exists, mirror the change: `toDomain` reads `iconName`; `upsert` passes `template.iconName`. If `HabitTemplate` is wired through a different repo path, search:
```bash
rtk grep -n "upsertHabitTemplate\|HabitTemplate(" mobile/shared/src/commonMain/kotlin/com/habittracker/data/ | head
```
and apply the same pattern.

- [ ] **Step 3: Build shared**

```bash
rtk ./gradlew :mobile:shared:compileCommonMainKotlinMetadata 2>&1 | tail -10
```

Remaining errors should be in Sync DTOs (Task 5) + UI (Tasks 7-11).

- [ ] **Step 4: Commit**

```bash
rtk git add mobile/shared/src/commonMain/kotlin/com/habittracker/data/repository/LocalIdentityRepository.kt \
    mobile/shared/src/commonMain/kotlin/com/habittracker/data/repository/LocalHabitTemplateRepository.kt
rtk git commit -m "refactor(identity): repo mappers carry hue + iconName"
```

(Drop the second path if it doesn't exist.)

---

## Task 5: Sync DTO additions

**Files:**
- Modify: `mobile/shared/src/commonMain/kotlin/com/habittracker/data/sync/PostgrestSupabaseSyncClient.kt`

- [ ] **Step 1: Update IdentityDto**

Find `IdentityDto` (search by class name). Add:
```kotlin
@Serializable
private data class IdentityDto(
    val id: String,
    val name: String,
    val description: String,
    val icon: String,
    val hue: Int = 142,
)
```

Update `Identity.toDto()` and `IdentityDto.toDomain()` to round-trip `hue`. The default 142 covers DTOs decoded from rows pre-migration.

- [ ] **Step 2: Update HabitTemplateDto**

```kotlin
@Serializable
private data class HabitTemplateDto(
    val id: String,
    val name: String,
    val unit: String,
    @SerialName("default_threshold") val defaultThreshold: Double,
    @SerialName("default_daily_target") val defaultDailyTarget: Int,
    @SerialName("icon_name") val iconName: String? = null,
)
```

Round-trip `iconName` in toDto/toDomain mappers.

- [ ] **Step 3: Build shared**

```bash
rtk ./gradlew :mobile:shared:assembleDebug 2>&1 | tail -10
```

Expected: BUILD SUCCESSFUL. Shared layer clean.

- [ ] **Step 4: Commit**

```bash
rtk git add mobile/shared/src/commonMain/kotlin/com/habittracker/data/sync/PostgrestSupabaseSyncClient.kt
rtk git commit -m "refactor(sync): IdentityDto.hue + HabitTemplateDto.icon_name round-trip"
```

---

## Task 6: Server migration

**Files:**
- Create: `supabase/migrations/20260512000000_phase8_identity_hue_habit_icon.sql`

- [ ] **Step 1: Write SQL**

```sql
-- Phase 8: Identity gains `hue`; HabitTemplate gains `icon_name`.
-- Backfill canonical hue + Material icon name for 8 existing identities by
-- stable id. New identities (id 09..13) inserted via seed.sql update + reconcile.

alter table identities add column hue integer not null default 142;
alter table habit_templates add column icon_name text;

update identities set hue = 30,  icon = 'menu_book'        where id = '00000000-0000-0000-0000-000000000001';
update identities set hue = 225, icon = 'code'             where id = '00000000-0000-0000-0000-000000000002';
update identities set hue = 5,   icon = 'directions_run'   where id = '00000000-0000-0000-0000-000000000003';
update identities set hue = 285, icon = 'edit_note'        where id = '00000000-0000-0000-0000-000000000004';
update identities set hue = 190, icon = 'school'           where id = '00000000-0000-0000-0000-000000000005';
update identities set hue = 130, icon = 'eco'              where id = '00000000-0000-0000-0000-000000000006';
update identities set hue = 255, icon = 'self_improvement' where id = '00000000-0000-0000-0000-000000000007';
update identities set hue = 155, icon = 'favorite'         where id = '00000000-0000-0000-0000-000000000008';
```

- [ ] **Step 2: Push**

```bash
rtk supabase db push
```

Expected: applies and confirms. New rows (09..13) land at first client push after reconcile picks them up from the new SeedData.

- [ ] **Step 3: Commit**

```bash
rtk git add supabase/migrations/20260512000000_phase8_identity_hue_habit_icon.sql
rtk git commit -m "fix(sync): server migration for Identity.hue + HabitTemplate.icon_name"
```

---

## Task 7: Material icon resolver + IdentityHue rewrite

**Files:**
- Modify: `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/components/HabitGlyph.kt`
- Create: `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/components/MaterialIconResolver.kt`

- [ ] **Step 1: Create resolver**

Create `MaterialIconResolver.kt`. Build a `when` mapping every Material icon name used in the new seed to its `ImageVector`. From canvas, the distinct icons used by identities + habit templates: `menu_book, code, directions_run, edit_note, school, eco, self_improvement, favorite, palette, savings, handshake, explore, restaurant` (identities) plus `article, description, headphones, bug_report, rate_review, groups, extension, fitness_center, directions_walk, directions_bike, pool, accessibility_new, timer, whatshot, rss_feed, create, edit, spellcheck, email, smart_display, cast, language, style, podcasts, calculate, movie, cleaning_services, inventory, delete_sweep, volunteer_activism, inbox, unsubscribe, swap_horiz, spa, psychology, water_drop, bedtime, kitchen, no_food, medication, brush, music_note, photo_camera, share, block, account_balance, price_check, money_off, phone, chat, event_note, mail_outline, hearing, place, lightbulb, park`.

```kotlin
package com.jktdeveloper.habitto.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.LabelImportant
import androidx.compose.ui.graphics.vector.ImageVector

/** Maps Material icon names from the seed catalog to Compose ImageVectors. */
fun materialIconFor(name: String?): ImageVector = when (name) {
    "menu_book" -> Icons.Default.MenuBook
    "code" -> Icons.Default.Code
    "directions_run" -> Icons.Default.DirectionsRun
    "edit_note" -> Icons.Default.EditNote
    "school" -> Icons.Default.School
    "eco" -> Icons.Default.Eco
    "self_improvement" -> Icons.Default.SelfImprovement
    "favorite" -> Icons.Default.Favorite
    "palette" -> Icons.Default.Palette
    "savings" -> Icons.Default.Savings
    "handshake" -> Icons.Default.Handshake
    "explore" -> Icons.Default.Explore
    "restaurant" -> Icons.Default.Restaurant
    "article" -> Icons.Default.Article
    "description" -> Icons.Default.Description
    "headphones" -> Icons.Default.Headphones
    "bug_report" -> Icons.Default.BugReport
    "rate_review" -> Icons.Default.RateReview
    "groups" -> Icons.Default.Groups
    "extension" -> Icons.Default.Extension
    "fitness_center" -> Icons.Default.FitnessCenter
    "directions_walk" -> Icons.Default.DirectionsWalk
    "directions_bike" -> Icons.Default.DirectionsBike
    "pool" -> Icons.Default.Pool
    "accessibility_new" -> Icons.Default.AccessibilityNew
    "timer" -> Icons.Default.Timer
    "whatshot" -> Icons.Default.Whatshot
    "rss_feed" -> Icons.Default.RssFeed
    "create" -> Icons.Default.Create
    "edit" -> Icons.Default.Edit
    "spellcheck" -> Icons.Default.Spellcheck
    "email" -> Icons.Default.Email
    "smart_display" -> Icons.Default.SmartDisplay
    "cast" -> Icons.Default.Cast
    "language" -> Icons.Default.Language
    "style" -> Icons.Default.Style
    "podcasts" -> Icons.Default.Podcasts
    "calculate" -> Icons.Default.Calculate
    "movie" -> Icons.Default.Movie
    "cleaning_services" -> Icons.Default.CleaningServices
    "inventory" -> Icons.Default.Inventory
    "delete_sweep" -> Icons.Default.DeleteSweep
    "volunteer_activism" -> Icons.Default.VolunteerActivism
    "inbox" -> Icons.Default.Inbox
    "unsubscribe" -> Icons.Default.Unsubscribe
    "swap_horiz" -> Icons.Default.SwapHoriz
    "spa" -> Icons.Default.Spa
    "psychology" -> Icons.Default.Psychology
    "water_drop" -> Icons.Default.WaterDrop
    "bedtime" -> Icons.Default.Bedtime
    "kitchen" -> Icons.Default.Kitchen
    "no_food" -> Icons.Default.NoFood
    "medication" -> Icons.Default.Medication
    "brush" -> Icons.Default.Brush
    "music_note" -> Icons.Default.MusicNote
    "photo_camera" -> Icons.Default.PhotoCamera
    "share" -> Icons.Default.Share
    "block" -> Icons.Default.Block
    "account_balance" -> Icons.Default.AccountBalance
    "price_check" -> Icons.Default.PriceCheck
    "money_off" -> Icons.Default.MoneyOff
    "phone" -> Icons.Default.Phone
    "chat" -> Icons.Default.Chat
    "event_note" -> Icons.Default.EventNote
    "mail_outline" -> Icons.Default.MailOutline
    "hearing" -> Icons.Default.Hearing
    "place" -> Icons.Default.Place
    "lightbulb" -> Icons.Default.Lightbulb
    "park" -> Icons.Default.Park
    else -> Icons.Outlined.LabelImportant  // safe fallback for unmapped names
}
```

If any Material icon name is unavailable on the project's Compose Material Icons version, swap to the closest alternative (e.g. `headphones` may be `Headset`). Verify each via `rtk grep -r "Icons.Default.Code\|Icons.Default.MenuBook" mobile/androidApp/build/intermediates/` after first compile attempt.

- [ ] **Step 2: Rewrite IdentityHue helper**

In `HabitGlyph.kt`, replace the existing `object IdentityHue` (lines ~78–101) with:
```kotlin
/** Identity hue source. With Phase 8, prefer reading Identity.hue directly. */
object IdentityHue {
    const val DEFAULT = 142f
    fun forIdentity(identity: Identity?): Float = identity?.hue?.toFloat() ?: DEFAULT
}
```

Drop the hard-coded id→hue map (legacy aliases from `shared.jsx`). Add `import com.habittracker.domain.model.Identity` to the file.

- [ ] **Step 3: Update HabitGlyph icon source**

If `HabitGlyph.kt` currently has its own ad-hoc icon mapping (lines ~70–76 per probe), make it call `materialIconFor(name)` and accept an icon-name String parameter. Existing callers passing a Material icon name keep working; emoji callers (none expected after Phase 8) fall through to the fallback.

- [ ] **Step 4: Update all `IdentityHue.forIdentityId(...)` call sites**

Search `rtk grep -rn "IdentityHue.forIdentityId" mobile/androidApp/` and update each to either:
- Pass the `Identity` object: `IdentityHue.forIdentity(identity)` if scope has Identity.
- Use the `Identity.hue.toFloat()` directly if the call site has a hue Int.

Don't keep `forIdentityId(String?)` — clean break.

- [ ] **Step 5: Build**

```bash
rtk ./gradlew :mobile:androidApp:assembleDebug 2>&1 | tail -10
```

Expected: BUILD SUCCESSFUL once every call site is migrated. If a site is missed, the build flags it; fix inline.

- [ ] **Step 6: Commit**

```bash
rtk git add mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/components/MaterialIconResolver.kt \
    mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/components/HabitGlyph.kt \
    $(git ls-files -m mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ | tr '\n' ' ')
rtk git commit -m "refactor(ui): IdentityHue reads from Identity.hue + MaterialIconResolver"
```

---

## Task 8: OnboardingViewModel — LinkedHashSet pick order + multi-select polish

**Files:**
- Modify: `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/onboarding/OnboardingViewModel.kt`

- [ ] **Step 1: Make selectedIdentityIds preserve insertion order**

The VM already stores `selectedIdentityIds: Set<String>`. Switch the underlying instances to `LinkedHashSet`:
```kotlin
fun toggleIdentity(identityId: String) {
    val current = LinkedHashSet(_uiState.value.selectedIdentityIds)  // preserves order
    if (current.contains(identityId)) current.remove(identityId) else current.add(identityId)
    val newTemplates = container.getHabitTemplatesForIdentitiesUseCase.execute(current)
    val newTemplateIds = newTemplates.map { it.template.id }.toSet()
    val keptSelections = _uiState.value.selectedTemplateIds.intersect(newTemplateIds)
    _uiState.value = _uiState.value.copy(
        selectedIdentityIds = current,
        habitTemplates = newTemplates,
        selectedTemplateIds = keptSelections,
    )
}
```

Same for `toggleHabit` and `toggleWantActivity` (LinkedHashSet for consistency; pick order matters in Step 2 grouping).

- [ ] **Step 2: Build**

```bash
rtk ./gradlew :mobile:androidApp:assembleDebug 2>&1 | tail -5
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
rtk git add mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/onboarding/OnboardingViewModel.kt
rtk git commit -m "refactor(onboarding): VM Set picks preserve insertion order"
```

---

## Task 9: IdentityStep composable

**Files:**
- Create: `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/onboarding/steps/IdentityStep.kt`

- [ ] **Step 1: Implement step**

```kotlin
package com.jktdeveloper.habitto.ui.onboarding.steps

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.habittracker.domain.model.Identity
import com.jktdeveloper.habitto.ui.components.HabitGlyph

@Composable
fun IdentityStep(
    identities: List<Identity>,
    selectedIds: Set<String>,
    onToggle: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.padding(horizontal = 16.dp),
    ) {
        items(identities, key = { it.id }) { identity ->
            IdentityCard(
                identity = identity,
                selected = identity.id in selectedIds,
                onClick = { onToggle(identity.id) },
            )
        }
    }
}

@Composable
private fun IdentityCard(identity: Identity, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
    val border = BorderStroke(
        width = if (selected) 2.dp else 1.dp,
        color = if (selected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.outlineVariant,
    )
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = bg,
        border = border,
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Box(modifier = Modifier.padding(14.dp)) {
            if (selected) {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .align(Alignment.TopEnd)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
            Column {
                HabitGlyph(iconName = identity.icon, hue = identity.hue.toFloat(), size = 36.dp)
                Spacer(Modifier.height(10.dp))
                Text(
                    identity.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    identity.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
```

Note: `HabitGlyph` accepts `iconName: String` after Task 7's rewrite. If the existing signature uses a different param name, adapt.

- [ ] **Step 2: Build**

```bash
rtk ./gradlew :mobile:androidApp:assembleDebug 2>&1 | tail -5
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
rtk git add mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/onboarding/steps/IdentityStep.kt
rtk git commit -m "feat(onboarding): IdentityStep — 13-card multi-select grid"
```

---

## Task 10: HabitsStep composable

**Files:**
- Create: `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/onboarding/steps/HabitsStep.kt`

- [ ] **Step 1: Implement step**

The VM exposes `habitTemplates: List<TemplateWithIdentities>`. Each `TemplateWithIdentities` carries the template and the identities that recommended it. Step composable groups by primary identity (the first recommender per VM ordering), then within each identity, renders rows. Rows with multiple recommenders display an "Also for {Other}" pill.

```kotlin
package com.jktdeveloper.habitto.ui.onboarding.steps

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.habittracker.domain.model.Identity
import com.habittracker.domain.model.TemplateWithIdentities
import com.jktdeveloper.habitto.ui.components.HabitGlyph

@Composable
fun HabitsStep(
    templates: List<TemplateWithIdentities>,
    selectedIdentityIds: Set<String>,
    selectedTemplateIds: Set<String>,
    identityById: Map<String, Identity>,
    onToggle: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Group by primary identity (first in selectedIdentityIds order that the template lists)
    val orderedIds = selectedIdentityIds.toList()
    val grouped = templates.groupBy { item ->
        orderedIds.firstOrNull { iid -> iid in item.identityIds } ?: orderedIds.first()
    }
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.padding(horizontal = 16.dp),
    ) {
        orderedIds.forEach { iid ->
            val items = grouped[iid] ?: return@forEach
            val identity = identityById[iid] ?: return@forEach
            item {
                Text(
                    identity.name.uppercase(),
                    fontSize = 11.sp,
                    letterSpacing = 0.6.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                )
            }
            items(items, key = { it.template.id }) { it ->
                HabitRow(
                    template = it,
                    identityById = identityById,
                    primaryIdentityId = iid,
                    selected = it.template.id in selectedTemplateIds,
                    onClick = { onToggle(it.template.id) },
                )
            }
        }
    }
}

@Composable
private fun HabitRow(
    template: TemplateWithIdentities,
    identityById: Map<String, Identity>,
    primaryIdentityId: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val primaryIdentity = identityById[primaryIdentityId]
    val bg = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
    val border = BorderStroke(
        width = 1.dp,
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
    )
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = bg,
        border = border,
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HabitGlyph(
                iconName = template.template.iconName ?: "label_important",
                hue = primaryIdentity?.hue?.toFloat() ?: 142f,
                size = 40.dp,
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    template.template.name,
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    "Target ${template.template.defaultDailyTarget} ${template.template.unit} · ${template.template.defaultThreshold.toInt()} per pt",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val otherIdentities = template.identityIds
                    .filter { it != primaryIdentityId }
                    .mapNotNull { identityById[it]?.name }
                if (otherIdentities.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    AlsoForPill(otherNames = otherIdentities)
                }
            }
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary
                        else androidx.compose.ui.graphics.Color.Transparent
                    )
                    .padding(2.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun AlsoForPill(otherNames: List<String>) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Text(
            "Also for ${otherNames.joinToString(", ")}",
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.4.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
        )
    }
}
```

If `TemplateWithIdentities` exposes a different field name (`identityIds` here is illustrative — check the actual model), adapt.

- [ ] **Step 2: Build**

```bash
rtk ./gradlew :mobile:androidApp:assembleDebug 2>&1 | tail -5
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
rtk git add mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/onboarding/steps/HabitsStep.kt
rtk git commit -m "feat(onboarding): HabitsStep — merged list + Also-for pill"
```

---

## Task 11: WantsStep + SignInStep composables

**Files:**
- Create: `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/onboarding/steps/WantsStep.kt`
- Create: `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/onboarding/steps/SignInStep.kt`

- [ ] **Step 1: WantsStep**

```kotlin
package com.jktdeveloper.habitto.ui.onboarding.steps

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.habittracker.domain.model.WantActivity
import com.jktdeveloper.habitto.ui.components.resolveWantIcon

@Composable
fun WantsStep(
    wants: List<WantActivity>,
    selectedIds: Set<String>,
    onToggle: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.padding(horizontal = 16.dp),
    ) {
        items(wants, key = { it.id }) { want ->
            WantRow(
                want = want,
                selected = want.id in selectedIds,
                onClick = { onToggle(want.id) },
            )
        }
    }
}

@Composable
private fun WantRow(want: WantActivity, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surface
    val border = BorderStroke(
        width = 1.dp,
        color = if (selected) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outlineVariant,
    )
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = bg,
        border = border,
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    resolveWantIcon(want.iconKey, want.name),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(
                want.name,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (selected) MaterialTheme.colorScheme.error else Color.Transparent),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}
```

- [ ] **Step 2: SignInStep**

```kotlin
package com.jktdeveloper.habitto.ui.onboarding.steps

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun SignInStep(
    onContinueEmail: () -> Unit,
    onContinueGoogle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.padding(horizontal = 16.dp)) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(20.dp),
            ) {
                Button(
                    onClick = onContinueEmail,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                ) {
                    Text("Continue with email")
                }
                OutlinedButton(
                    onClick = onContinueGoogle,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                ) {
                    Icon(Icons.Default.AccountCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Continue with Google")
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            "You can sign in later from Settings. Your data stays on this device until you do.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        )
    }
}
```

- [ ] **Step 3: Build**

```bash
rtk ./gradlew :mobile:androidApp:assembleDebug 2>&1 | tail -5
```

- [ ] **Step 4: Commit**

```bash
rtk git add mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/onboarding/steps/WantsStep.kt \
    mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/onboarding/steps/SignInStep.kt
rtk git commit -m "feat(onboarding): WantsStep + SignInStep composables"
```

---

## Task 12: OnboardingScreen scaffold rewrite

**Files:**
- Modify: `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/onboarding/OnboardingScreen.kt`

- [ ] **Step 1: Shrink scaffold to dispatch + chrome**

Replace the current step-body branches with composable dispatch. Top of scaffold: progress dots, Step N of 4 label, title (28sp bold, -0.4sp letter-spacing), subtitle (body-md muted). Body slot calls into the appropriate step composable. Bottom bar: Skip/Back left, Next/I'll do it later right.

Step copy:
```kotlin
private data class StepCopy(val title: String, val subtitle: String, val ctaPrimary: String)

private fun copyFor(step: OnboardingStep): StepCopy = when (step) {
    OnboardingStep.IDENTITY -> StepCopy(
        "Who do you want to become?",
        "Choose an identity. Habitto suggests habits that support it.",
        "Next",
    )
    OnboardingStep.HABITS -> StepCopy(
        "Pick habits that prove it.",
        "Each habit earns points. Stay above your daily target to bank them.",
        "Next",
    )
    OnboardingStep.WANTS -> StepCopy(
        "What pulls you away?",
        "Wants cost points. Pick the ones you do without thinking.",
        "Next",
    )
    OnboardingStep.SYNC -> StepCopy(
        "Sync across devices?",
        "Sign in to sync. Skip if you'd rather stay local.",
        "I'll do it later",
    )
}
```

In the body branch:
```kotlin
when (uiState.step) {
    OnboardingStep.IDENTITY -> IdentityStep(
        identities = uiState.identities,
        selectedIds = uiState.selectedIdentityIds,
        onToggle = viewModel::toggleIdentity,
        modifier = Modifier.fillMaxSize(),
    )
    OnboardingStep.HABITS -> {
        val identityById = remember(uiState.identities) { uiState.identities.associateBy { it.id } }
        HabitsStep(
            templates = uiState.habitTemplates,
            selectedIdentityIds = uiState.selectedIdentityIds,
            selectedTemplateIds = uiState.selectedTemplateIds,
            identityById = identityById,
            onToggle = viewModel::toggleHabit,
            modifier = Modifier.fillMaxSize(),
        )
    }
    OnboardingStep.WANTS -> WantsStep(
        wants = uiState.wantActivities,
        selectedIds = uiState.selectedActivityIds,
        onToggle = viewModel::toggleWantActivity,
        modifier = Modifier.fillMaxSize(),
    )
    OnboardingStep.SYNC -> SignInStep(
        onContinueEmail = viewModel::finishAndSignIn,
        onContinueGoogle = viewModel::finishAndSignIn,  // OAuth routed through AuthScreen
        modifier = Modifier.fillMaxSize(),
    )
}
```

Imports updated for the new step composables.

- [ ] **Step 2: Tighten progress bar + title**

The existing `OnboardingScreen` has a progress bar at top. Update to show 4 dots/segments, current step highlighted in primary. Keep the existing bottom bar widget; just feed it the new CTA labels from `copyFor(step).ctaPrimary`.

- [ ] **Step 3: Build**

```bash
rtk ./gradlew :mobile:androidApp:assembleDebug 2>&1 | tail -5
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
rtk git add mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/onboarding/OnboardingScreen.kt
rtk git commit -m "refactor(onboarding): OnboardingScreen scaffold + canvas step copy"
```

---

## Task 13: Test rebaseline + reconcile verification

**Files:**
- Modify: `mobile/shared/src/commonTest/kotlin/com/habittracker/domain/usecase/SetupUserIdentitiesUseCaseTest.kt`
- Modify: `mobile/androidApp/src/test/kotlin/com/jktdeveloper/habitto/ui/onboarding/OnboardingViewModelTest.kt`
- (Add as needed: `GetHabitTemplatesForIdentitiesUseCaseTest.kt` — create if missing.)

- [ ] **Step 1: SetupUserIdentitiesUseCaseTest**

Extend coverage with a `reconciles 13 seeded identities` test that asserts every new identity (09..13) is inserted on a fresh user and that existing identities (01..08) retain their original `id` but get `hue` + `icon` updated to the new canvas values:
```kotlin
@Test fun `reconcile inserts 13 seeded identities with canonical hue + Material icon`() = runTest {
    val sut = SetupUserIdentitiesUseCase(repo, SeedData.identities)
    sut.reconcile(userId = "u1")
    val all = repo.getAllIdentities()
    assertEquals(13, all.size)
    val athlete = all.single { it.id == "00000000-0000-0000-0000-000000000003" }
    assertEquals("directions_run", athlete.icon)
    assertEquals(5, athlete.hue)
    val creator = all.single { it.id == "00000000-0000-0000-0000-000000000009" }
    assertEquals("palette", creator.icon)
    assertEquals(315, creator.hue)
}
```

If `SetupUserIdentitiesUseCase` doesn't have a `reconcile` method today, adapt to whatever entry point seeds identities at startup.

- [ ] **Step 2: GetHabitTemplatesForIdentitiesUseCaseTest**

Verify dedupe of shared templates (`alsoFor`):
```kotlin
@Test fun `templates with alsoFor appear in both buckets and dedupe in cross-pick`() {
    val sut = GetHabitTemplatesForIdentitiesUseCase(SeedData.identityHabitMap, SeedData.habitTemplates)
    val result = sut.execute(setOf(
        "00000000-0000-0000-0000-000000000001",  // Reader
        "00000000-0000-0000-0000-000000000005",  // Learner
    ))
    // "Read article" is alsoFor learner — appears once in result.
    val readArticle = result.count { it.template.name == "Read article" }
    assertEquals(1, readArticle)
}
```

- [ ] **Step 3: OnboardingViewModelTest**

Add a `toggleIdentity preserves pick order` test:
```kotlin
@Test fun `toggleIdentity preserves insertion order`() {
    vm.toggleIdentity("a")
    vm.toggleIdentity("b")
    vm.toggleIdentity("c")
    assertEquals(listOf("a", "b", "c"), vm.uiState.value.selectedIdentityIds.toList())
}
```

Existing tests continue to pass.

- [ ] **Step 4: Run tests**

```bash
rtk ./gradlew :mobile:shared:testDebugUnitTest :mobile:androidApp:testDebugUnitTest 2>&1 | tail -10
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
rtk git add mobile/shared/src/commonTest/kotlin/com/habittracker/domain/usecase/SetupUserIdentitiesUseCaseTest.kt \
    mobile/shared/src/commonTest/kotlin/com/habittracker/domain/usecase/GetHabitTemplatesForIdentitiesUseCaseTest.kt \
    mobile/androidApp/src/test/kotlin/com/jktdeveloper/habitto/ui/onboarding/OnboardingViewModelTest.kt
rtk git commit -m "test(onboarding): identity reconcile + alsoFor dedupe + pick-order"
```

---

## Task 14: Final smoke + push PR

- [ ] **Step 1: Shared + android tests**

```bash
rtk ./gradlew :mobile:shared:testDebugUnitTest :mobile:androidApp:testDebugUnitTest 2>&1 | tail -10
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Debug + release builds**

```bash
rtk ./gradlew :mobile:androidApp:assembleDebug :mobile:androidApp:assembleRelease 2>&1 | tail -10
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Manual smoke (device)**

Clear app data + reinstall. Verify:
- Onboarding step 1 shows 13 identity cards in 2-col grid; tap multi-selects with primary border + check badge.
- Step 2 shows habits grouped by picked identity in pick order; "Also for X" pill appears on shared templates.
- Step 3 shows want rows; tap turns row error-themed.
- Step 4 shows Continue-with-email + Google buttons; "I'll do it later" exits to Home.
- Top app bars across the rest of the app render Material icons (no emoji) for identities, with hue tint.

- [ ] **Step 4: Push + open PR**

```bash
rtk git push -u origin feature/phase8-onboarding-seeds
gh pr create --title "Phase 8: onboarding redesign + identity/habit seed update" --body "$(cat <<'EOF'
## Summary

Rebuilds onboarding to match canvas v4 (4 steps, multi-select identity,
merged habit list with Also-for pills, error-themed wants, sign-in card)
and replaces the identity + habit catalogs with the canvas seeds —
13 identities with Material icons + per-identity hue, ~80 habit
templates with `alsoFor` shared-recommendation semantics.

## Highlights

- Identity gains `hue: Int` and switches `icon` to Material name (was emoji).
- HabitTemplate gains `iconName: String?`.
- 13-identity catalog (additive 5 new: Creator, Saver, Connector, Adventurer, Chef).
- Full habit template refresh per canvas; user habits unaffected (they own their thresholds).
- OnboardingScreen split into per-step composables under `ui/onboarding/steps/`.
- IdentityHue helper now reads `Identity.hue` directly (hard-coded map dropped).
- MaterialIconResolver maps Material names to Compose ImageVectors.
- Server migration adds columns + backfills 8 existing identities by stable id.

## Test plan

- [x] Shared + android unit tests pass
- [x] Debug + release builds succeed
- [ ] Manual smoke per plan Task 14 step 3
EOF
)"
```

---

## Self-Review

**Spec coverage:**
- ✅ Domain model additions (Tasks 2)
- ✅ Schema migration local + server (Tasks 1, 6)
- ✅ Seed rebuild (Task 3)
- ✅ Repo mappers (Task 4)
- ✅ Sync DTOs (Task 5)
- ✅ Material icon resolver + IdentityHue refactor (Task 7)
- ✅ VM pick-order (Task 8)
- ✅ Step composables — IdentityStep, HabitsStep, WantsStep, SignInStep (Tasks 9, 10, 11)
- ✅ OnboardingScreen scaffold (Task 12)
- ✅ Tests (Task 13)
- ✅ Smoke + PR (Task 14)

**Placeholder scan:** clean. Each task has concrete file paths, code blocks, and exact commands. The habit catalog enumeration in Task 3 deliberately points to the canvas source — the implementer ports row-for-row from canvas, no improvisation. Concrete example rows are provided for the first identity bucket.

**Type consistency:** `Identity.hue: Int` consistent across model, SQL (`INTEGER NOT NULL DEFAULT 142` cast at Long boundary in mappers), repo, sync DTO, UI. `HabitTemplate.iconName: String?` consistent across model, SQL (TEXT nullable), repo, sync DTO. `materialIconFor(name: String?): ImageVector` consistent across all step composables. `IdentityHue.forIdentity(identity: Identity?): Float` replaces `forIdentityId(id: String?)` everywhere.
