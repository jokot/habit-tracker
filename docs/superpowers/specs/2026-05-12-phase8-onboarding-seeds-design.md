# Phase 8 — Onboarding Redesign + Identity / Habit Seed Update

**Status:** in design.

**Branch:** `feature/phase8-onboarding-seeds` (worktree `.worktrees/phase8-onboarding-seeds`, off `main` post Phase 7 merge).

**Source of truth:** canvas v4 bundle (Claude Design). Re-fetch path: `https://api.anthropic.com/v1/design/h/hc_UtWXPRooWP9x3pmQFlw?open_file=canvas.html`. Local extraction during work: `/tmp/habitto-design/habitto/project/`.

## Why

Onboarding screens still reference legacy 8-identity catalog and emoji icons. Canvas v4 ships:
- 13-identity catalog with Material icon names + per-identity `hue` (OKLCH chroma anchor).
- ~80-row HABIT_TEMPLATES catalog with `alsoFor` shared-recommendation semantics.
- Multi-select identity step (already partly there in VM but visual + copy diverge).
- Merged habits list with "Recommended by" pills.
- Updated step copy ("Who do you want to become?", "Pick habits that prove it.", "What pulls you away?", "Sync across devices?").

## Goal

One sentence: **match canvas v4 onboarding flow + catalog, with multi-identity selection driving a merged shared-recommendation habit list.**

## Domain model

```kotlin
data class Identity(
    val id: String,
    val name: String,
    val description: String,
    val icon: String,       // CHANGED — Material icon name (was emoji)
    val hue: Int,           // NEW — OKLCH hue 0..360 for tint
)

data class HabitTemplate(
    val id: String,
    val name: String,
    val iconName: String,   // NEW — Material icon name
    val unit: String,
    val defaultThreshold: Double,
    val defaultDailyTarget: Int,
)
```

`identityHabitMap: Map<identityId, List<templateId>>` shape unchanged; new templates and shared `alsoFor` entries land as additional bucket memberships.

## Catalog (rebuilt from canvas)

### Identities — 13 total

Stable UUIDs preserved for existing 8 (`00000000-...01..08`). New 5 use fresh stable UUIDs `00000000-...09..13`.

| Id | Name | Icon | Hue |
|--|--|--|--|
| 01 | Reader | menu_book | 30 |
| 02 | Builder | code | 225 |
| 03 | Athlete | directions_run | 5 |
| 04 | Writer | edit_note | 285 |
| 05 | Learner | school | 190 |
| 06 | Minimalist | eco | 130 |
| 07 | Devotee | self_improvement | 255 |
| 08 | Health-Conscious | favorite | 155 |
| 09 | Creator | palette | 315 |
| 10 | Saver | savings | 75 |
| 11 | Connector | handshake | 350 |
| 12 | Adventurer | explore | 170 |
| 13 | Chef | restaurant | 45 |

### Habit templates — ~80 total

Full catalog refresh per canvas. Existing 31 templates: those with stable matching names keep their UUIDs; others retire (rows already created from them in user habits keep working since habits store their own threshold + target). New templates get fresh stable UUIDs. The detailed catalog is the source of truth at `/tmp/habitto-design/habitto/project/screens-v3.jsx:1117` (HABIT_TEMPLATES object); the implementation plan enumerates each row.

`alsoFor` in canvas → identityHabitMap entries: a template with `alsoFor: ['health']` listed under `athlete` appears in both `athlete` AND `health` buckets in the map.

## Schema migration

### Local — SQLDelight migration 8

```sql
ALTER TABLE LocalIdentity ADD COLUMN hue INTEGER NOT NULL DEFAULT 142;
-- icon column retains TEXT type; content semantics flip from emoji to
-- Material icon name. Existing rows get overwritten on next reconcile.

ALTER TABLE LocalHabitTemplate ADD COLUMN iconName TEXT;
```

`LocalHabit` table unchanged — habits store their own iconName from when they were created via template.

### Server — Supabase migration

```sql
alter table identities add column hue integer not null default 142;
-- icon stays text; content flips emoji → Material name on next reconcile push.

alter table habit_templates add column icon_name text;

-- Backfill canonical hue + icon for existing 8 identities by id:
update identities set hue = 30,  icon = 'menu_book'        where id = '00000000-0000-0000-0000-000000000001';
update identities set hue = 225, icon = 'code'             where id = '00000000-0000-0000-0000-000000000002';
update identities set hue = 5,   icon = 'directions_run'   where id = '00000000-0000-0000-0000-000000000003';
update identities set hue = 285, icon = 'edit_note'        where id = '00000000-0000-0000-0000-000000000004';
update identities set hue = 190, icon = 'school'           where id = '00000000-0000-0000-0000-000000000005';
update identities set hue = 130, icon = 'eco'              where id = '00000000-0000-0000-0000-000000000006';
update identities set hue = 255, icon = 'self_improvement' where id = '00000000-0000-0000-0000-000000000007';
update identities set hue = 155, icon = 'favorite'         where id = '00000000-0000-0000-0000-000000000008';
```

New identities + new habit templates inserted via reconcile path (additive); seed.sql also updated for first-deploy parity.

## Sync DTO

`IdentityDto` adds `icon: String` + `hue: Int`. `HabitTemplateDto` adds `icon_name: String?`. Existing serializers extended.

## Onboarding screen redesign

Step enum unchanged: `IDENTITY → HABITS → WANTS → SYNC`.

VM state shape unchanged: `selectedIdentityIds: Set<String>`, `selectedTemplateIds: Set<String>`, `selectedActivityIds: Set<String>`. `toggleIdentity` already multi (additive Set).

Step 2 grouping needs deterministic pick order, but `Set<String>` is unordered. Implementation switches the underlying set to `LinkedHashSet` (or `MutableSet` created via `linkedSetOf()`) so iteration reflects insertion order. Public field type can stay `Set<String>` — `LinkedHashSet` is still a `Set`.

Files (new):
- `ui/onboarding/steps/IdentityStep.kt`
- `ui/onboarding/steps/HabitsStep.kt`
- `ui/onboarding/steps/WantsStep.kt`
- `ui/onboarding/steps/SignInStep.kt`

`OnboardingScreen.kt` shrinks to scaffold: progress bar (4 dots), `Step N of 4` label, big title (28sp bold, -0.4 letter-spacing), subtitle (body-md muted), body slot dispatches to the step composable, bottom bar (Skip/Back left, Next/I'll do it later right).

### Step copy (locked verbatim from canvas)

| Step | Title | Subtitle | Primary CTA |
|--|--|--|--|
| 1 | Who do you want to become? | Choose an identity. Habitto suggests habits that support it. | Next |
| 2 | Pick habits that prove it. | Each habit earns points. Stay above your daily target to bank them. | Next |
| 3 | What pulls you away? | Wants cost points. Pick the ones you do without thinking. | Next |
| 4 | Sync across devices? | Sign in to sync. Skip if you'd rather stay local. | I'll do it later |

### Step 1 — Identity (multi-select)

- 2-column grid of identity cards.
- Card: 14dp padding, rounded 16dp, surface bg / outlineVariant 1dp border by default. Selected: primaryContainer bg + 2dp primary border + 22dp check badge top-right (primary bg, onPrimary check icon).
- Icon: 36dp HabitGlyph (Material icon tinted via `oklch(0.35 0.10 $hue)` foreground on `oklch(0.92 0.04 $hue)` background).
- Title: name 14sp semibold. Subtitle: description body-small onSurfaceVariant.
- Validation: Next disabled until ≥ 1 identity picked.

### Step 2 — Habits (merged + Recommended-by)

- Single flat list of templates filtered by union of selected identities, deduped by id.
- Sort: identity-grouped sections (header = identity name). Section order = order of picked identities (the order the user tapped them in Step 1). Shared templates (`alsoFor`) appear under their primary identity bucket only, with an "Also for {Other}" pill on the row to surface the cross-recommendation.
- Row: HabitGlyph 40dp (hue from primary identity) + Column(name, "Target N {unit} · {threshold} per pt") + 24dp checkbox.
- Row bg: surface / outlineVariant by default; primaryContainer + primary border when selected.
- Validation: Next allowed with 0 habits (user can add later). Suggested floor: 1+.

### Step 3 — Wants

- Flat list of 14 want activities. Each row: 40dp rounded-10 surface-1 Box w/ Material icon + name + 24dp checkbox.
- Selected row: errorContainer bg + error 1dp border. Check badge: error bg + white check.
- Validation: Next allowed with 0 wants.

### Step 4 — Sign in

- Card (surface, rounded 16, outlineVariant border) with two stacked buttons:
  - "Continue with email" — filled button, 48dp, navigates to AuthScreen (signin).
  - "Continue with Google" — outlined button, 48dp, triggers Google OAuth.
- Footer caption: "You can sign in later from Settings. Your data stays on this device until you do." body-small muted, center.
- Bottom bar CTA: "I'll do it later" (text button) → `finish()` → Home.

## Tests

- `SetupUserIdentitiesUseCaseTest`: extend with 13-identity reconcile (5 new identities inserted, 8 existing preserved by id, hue + icon overwritten by seed values).
- `GetHabitTemplatesForIdentitiesUseCaseTest`: union dedupe across `alsoFor` entries. Sort assertions per canvas group order.
- `OnboardingViewModelTest`: existing toggle/finish flows continue to pass.
- Step composable smoke tests (Robolectric snapshot or @Preview only — keep light).

## Migration choreography

1. SQLDelight migration 8 — `hue` + `iconName` columns.
2. Server migration — `hue` + `icon_name` columns + identity backfill.
3. SeedData rebuilt with 13 identities + canvas habit catalog.
4. Sync DTOs extended.
5. Onboarding screen split into step composables.
6. Tests rebaselined.

User-side: clear app data + reinstall to ensure local seed matches canvas.

## Open risks

- **Existing user habits**: rows reference template UUIDs. Templates whose UUIDs disappear under the wholesale refresh leave habits with dangling `templateId`. Mitigated by habits storing their own threshold/target/icon — template is a lookup, not a hard dependency.
- **Material icon coverage**: canvas uses ~60 distinct icon names. Confirm all map to `androidx.compose.material.icons.Icons.Default.*` or `Icons.AutoMirrored.Filled.*`. Any missing → fall back to `Icons.Default.LabelImportant`.
- **Hue color tinting**: Compose lacks built-in OKLCH; use existing `IdentityHue` helper (already in codebase) — extend if needed to take Int 0..360.

## Out of scope

- Notifications (Phase 4 leftover, separate phase).
- Android widgets (Phase 5).
- iOS (Phase 6 gated).
- Want CRUD adjustments (Phase 7 shipped).
- Auth screen redesign (separate canvas section).
- Per-identity rate ladders.
