# Phase 5a — Design System Migration + Existing Screen Redesigns

**Date:** 2026-04-29
**Phase:** 5a (per master spec — design pass before identity / CRUD work)
**Depends on:** Phase 4 (streaks + notifications + settings) merged to main
**Branch:** `feature/phase5a-design-system`
**Worktree:** `.worktrees/phase5a-design-system`
**Design source of truth:** `docs/design/claude-design-prompt.md` + Claude Design canvas bundle (HTML/CSS prototype)

---

## 1. Goal

Adopt the new design system (tokens, fonts, components) and redesign every shipped screen to match. No new features — visual-only refactor that establishes the design foundation for subsequent phases.

**Success metric:** every shipped screen visually matches the canvas mockups; all Phase 4 functionality preserved; WCAG AA verified light + dark.

**Why now:** identity hub, CRUD, freezes, exchange-rate, and other future surfaces will be designed on top of these tokens — locking the foundation prevents retrofit churn later.

---

## 2. Scope

### In Scope

- **Token migration** — `Color.kt`, `Typography.kt`, `Spacing.kt` rewritten to match `tokens.css` from the design bundle. Warm `#FAF8F4` light bg, `#121210` dark bg, GitHub-style heat ramp, sync chip tokens, full Material 3 schemes.
- **Font bundling** — Inter (UI), Instrument Serif (display numerals — streak count, balance hero), JetBrains Mono (monospace) shipped as `.ttf` in `res/font/` with OFL license files in `res/raw/`.
- **Bottom nav infrastructure** — 3-item bottom nav (Today / Streak / You), shown only on those 3 destinations, hidden on every other screen (Onboarding, Auth, Settings reached from You, modals).
- **`You` hub screen (empty placeholder)** — top-level destination behind the You tab. For 5a it renders only: Settings row + Account section (email + Sign out when authenticated, Sign in CTA when guest). All other rows planned by design (Identity, Manage Habits, Manage Wants, Freezes, Exchange Rate) are NOT in code yet.
- **Existing-screen redesigns** — Onboarding, Auth, Today (Home), Streak history, Settings refactored to match canvas mockups using new tokens.
- **Hardcoded color audit** — every `Color(0xFF...)` literal in shipped code replaced with `MaterialTheme.colorScheme.X` or a new semantic token where appropriate.
- **Settings entry point moves** — Settings icon removed from Home top app bar; Settings is now reached only via You hub.

### Out of Scope (Deferred)

- **Identity surface on Home** (multi-identity chip strip) — Phase 5b
- **Per-habit streak micro-chip** on habit cards — Phase 5c
- **Freeze inventory chip** next to streak number — Phase 5c
- **Exchange-rate banner** above Wants section + tap-balance affordance — Phase 7
- **Want list / detail / form** screens — Phase 5c
- **Identity list / detail / Add identity flow** — Phase 5b
- **Habit detail / form / CRUD** screens — Phase 5c
- **Freezes screen** — Phase 5c
- **Want timer** screen — Phase 5c
- **Android home-screen widgets** — Phase 6
- **Exchange-rate UI** — Phase 7

---

## 3. Decisions Locked During Brainstorm

1. **Atomic migration.** Single PR, single branch. All token + screen changes land together. No "phase 5a-1" / "5a-2" split — token shift IS visible, no invisible plumbing PR.
2. **Bundled fonts (.ttf in res/font/).** No Google Fonts API runtime. Avoids FOUT on first launch, supports offline-first product promise. Costs ~500KB–1MB APK weight; acceptable.
3. **You hub is a stub.** Only Settings + Account rows. No "Coming soon" rows. Honest empty hub. 5b adds Identity, 5c adds Manage Habits/Wants/Freezes, 7 adds Exchange Rate.
4. **Bottom nav strictly 3-tab.** Visible ONLY on Today/Streak/You. Hidden on Onboarding, Auth, Settings (when reached from You), and any future detail / form / sheet screens.
5. **Migrate ALL hardcoded colors to semantic tokens.** Two-color-system state is technical debt; codebase is small enough to do completely in 5a.
6. **StreakHistory has dual entry.** Bottom nav "Streak" tab + DailyStatusCard "View all" link both lead to StreakHistory. Backstack handles the difference.
7. **Unshipped feature hooks: not in code.** Each ships with its own feature phase; no feature flags or "Coming soon" placeholders.

---

## 4. Tokens (matching `tokens.css`)

### 4.1 Colors — Light

| Token | Value | Usage |
|---|---|---|
| `m-bg` | `#FAF8F4` | Window bg (warmer than pure white) |
| `m-surface` | `#FFFFFF` | Card / sheet bg |
| `m-surface-1` | `#F4F1EC` | Resting card |
| `m-surface-2` | `#ECE7DF` | Nested surface |
| `m-surface-var` | `#EEEEEE` | Surface variant |
| `m-on-surface` | `#1B1A17` | Primary text |
| `m-on-surface-var` | `#5C5A55` | Secondary text |
| `m-outline` | `#BFB9AE` | Strong divider / border |
| `m-outline-var` | `#DCD7CD` | Subtle divider |
| `m-primary` | `#2E7D32` | Earn / completion |
| `m-on-primary` | `#FFFFFF` | |
| `m-primary-cont` | `#C8E6C9` | Tonal button bg |
| `m-secondary` | `#1565C0` | Sync / accent |
| `m-error` | `#C62828` | |
| `m-flame` | `#FF6F00` | Streak flame icon |
| `m-flame-soft` | `#FFE6CC` | Flame container tint |

**Heat ramp (locked from Phase 4 design carryover):**
- L0 `#D0D7DE` / L1 `#9BE9A8` / L2 `#40C463` / L3 `#30A14E` / L4 `#216E39`
- Border `#C8C8C8`

**Streak status:**
- Frozen `#00838F` cyan / Frozen bg `#E0F7FA`
- Broken `#C62828` red / Broken bg `#FFEBEE`
- Today ring `#757575`

**Sync chip:**
- Idle `#ECE7DF` / `#5C5A55`
- Running `#FFF3C4` / `#7A4F01`
- Synced `#D7E8FF` / `#0D47A1`
- Error `#FFD9D9` / `#B71C1C`

### 4.2 Colors — Dark

Mirrored per `tokens.css`:
- bg `#121210`, surface `#181816`, surface-1 `#221F1A`, surface-2 `#2C2823`
- on-surface `#ECE9E2`, on-surface-var `#B5B0A6`, outline `#6E6A60`
- primary `#81C784`, secondary `#64B5F6`, error `#EF9A9A`, flame `#FFA040`
- Heat L0–L4: `#161B22` / `#0E4429` / `#006D32` / `#26A641` / `#39D353`, border `#30363D`
- Frozen `#4DD0E1` / `#0E3A47` (brighter cyan), Broken `#EF9A9A` / `#3A1414`

### 4.3 Typography

| Role | Font / weight / size / line-height |
|---|---|
| `display-l` | Inter 400 57/64 letter-spacing -1px |
| `headline-l` | Inter 600 32/40 |
| `headline-m` | Inter 600 28/36 |
| `headline-s` | Inter 600 24/32 |
| `title-l` | Inter 600 22/28 |
| `title-m` | Inter 500 16/24 |
| `title-s` | Inter 500 14/20 |
| `body-l` | Inter 400 16/24 |
| `body-m` | Inter 400 14/20 |
| `body-s` | Inter 400 12/16 |
| `label-l` | Inter 500 14/20 |
| `label-m` | Inter 500 12/16 |
| `label-s` | Inter 500 11/16 uppercase |
| `mono` | JetBrains Mono 500 12/16 |
| `numeral` | Instrument Serif 400 — used for streak count, balance hero, identity stats |

### 4.4 Spacing

`xs 2 / sm 4 / md 8 / lg 12 / xl 16 / 2xl 24 / 3xl 32 / 4xl 48 / 5xl 64` — already matches existing `Spacing.kt`. Verify only.

### 4.5 Radius

`xs 4 / sm 8 / md 12 / lg 16 / xl 24 / pill 999`

### 4.6 Motion

`fast 150ms / base 220ms / slow 320ms`. Easing: `ease-out cubic-bezier(0.2, 0.7, 0.3, 1)`, `ease-in cubic-bezier(0.4, 0, 1, 1)`, `spring cubic-bezier(0.34, 1.4, 0.64, 1)`.

---

## 5. Files

### 5.1 Created

| File | Purpose |
|---|---|
| `mobile/androidApp/src/androidMain/res/font/inter_regular.ttf` | UI body |
| `mobile/androidApp/src/androidMain/res/font/inter_medium.ttf` | UI medium |
| `mobile/androidApp/src/androidMain/res/font/inter_semibold.ttf` | Headlines |
| `mobile/androidApp/src/androidMain/res/font/inter_bold.ttf` | Strong emphasis |
| `mobile/androidApp/src/androidMain/res/font/instrument_serif_regular.ttf` | Display numerals |
| `mobile/androidApp/src/androidMain/res/font/jetbrains_mono_regular.ttf` | Mono |
| `mobile/androidApp/src/androidMain/res/raw/ofl_inter.txt` | License |
| `mobile/androidApp/src/androidMain/res/raw/ofl_instrument_serif.txt` | License |
| `mobile/androidApp/src/androidMain/res/raw/ofl_jetbrains_mono.txt` | License |
| `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/theme/Fonts.kt` | `FontFamily` defs |
| `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/navigation/BottomNav.kt` | 3-item bottom nav composable |
| `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/you/YouHubScreen.kt` | Empty placeholder hub |
| `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/you/YouHubViewModel.kt` | Hub state |
| `mobile/androidApp/src/test/kotlin/com/jktdeveloper/habitto/ui/you/YouHubViewModelTest.kt` | Hub tests |

### 5.2 Modified (heavy)

| File | Change |
|---|---|
| `ui/theme/Color.kt` | Full rewrite to match tokens.css schema |
| `ui/theme/Typography.kt` | Inter + Instrument Serif + JetBrains Mono wired into M3 type roles |
| `ui/theme/Theme.kt` | Wire fonts into MaterialTheme; surface-1 / surface-2 custom tokens accessible |
| `ui/theme/Spacing.kt` | Verify match (likely no diff) |
| `ui/navigation/AppNavigation.kt` | Add `Screen.You`; render bottom nav conditionally; route between Today/Streak/You |
| `ui/onboarding/OnboardingScreen.kt` | Redesign per canvas |
| `ui/auth/AuthScreen.kt` | Redesign per canvas (sign in / sign up / Google / check email states) |
| `ui/home/HomeScreen.kt` | Redesign per canvas; remove Settings icon from top bar; remove unshipped hooks (freeze chip, exchange banner, per-habit chip if exists) |
| `ui/streak/DailyStatusCard.kt` (StreakStrip.kt) | Match canvas spec; tighten spacing |
| `ui/streak/StreakHistoryScreen.kt` | Redesign per canvas |
| `ui/streak/MonthCalendar.kt` | Heatmap cell tokens (no logic change) |
| `ui/settings/SettingsScreen.kt` | Redesign per canvas |
| `ui/auth/LogoutDialog.kt` | Token migration only |
| Any file referencing `Color(0xFF...)` literal | Replace with semantic token |

### 5.3 Removed

- Settings icon `IconButton` from Home top app bar
- Any direct hex color literals across the codebase (replaced with tokens)

---

## 6. Navigation

### 6.1 Routes

```
Screen.Auth.route          = "auth"
Screen.Onboarding.route    = "onboarding"
Screen.Home.route          = "home"          // labeled "Today" in nav UI
Screen.Settings.route      = "settings"
Screen.StreakHistory.route = "streak-history"
Screen.You.route           = "you"           // NEW
```

### 6.2 Bottom nav rendering

Inside `AppNavigation.kt`:

```kotlin
val currentBackStackEntry by navController.currentBackStackEntryAsState()
val currentRoute = currentBackStackEntry?.destination?.route
val showBottomNav = currentRoute in setOf(
    Screen.Home.route,
    Screen.StreakHistory.route,
    Screen.You.route,
)

NavHost(navController, startDestination = ...) { ... }

if (showBottomNav) BottomNav(currentRoute = currentRoute, navController = navController)
```

Where `BottomNav` is a `NavigationBar` with 3 `NavigationBarItem`s.

### 6.3 Tab tap behavior

```kotlin
navController.navigate(route) {
    popUpTo(navController.graph.startDestinationId) { saveState = true }
    launchSingleTop = true
    restoreState = true
}
```

### 6.4 "View all" from DailyStatusCard

Pushes onto the current backstack:
```kotlin
navController.navigate(Screen.StreakHistory.route)
```

Back returns to Today. This differs from tab-tap which sets StreakHistory as the active tab. Both are valid entries; nav state respects the entry path.

---

## 7. You Hub (placeholder)

### 7.1 Layout

- Top app bar (CenterAlignedTopAppBar): "You" title (no back arrow — top-level)
- LazyColumn:
  - Account section header
    - When authenticated: ListItem with email (disabled-styled, informational) + ListItem "Sign out" (error tint)
    - When guest: ListItem "Sign in to sync"
  - Settings row (ListItem with chevron, opens `Screen.Settings.route`)

That's it. No identity card, no Manage Habits/Wants, no Freezes, no Exchange Rate.

### 7.2 ViewModel

```kotlin
class YouHubViewModel(
    private val container: AppContainer,
) : ViewModel() {
    val authState: StateFlow<AuthState> = container.authState
    val email: String? get() = container.currentAccountEmail()

    fun signOut(onComplete: () -> Unit) {
        viewModelScope.launch {
            container.signOutFromSettings()
            onComplete()
        }
    }
}
```

Reuses Phase 4's `signOutFromSettings()` helper. Sign-out from You hub navigates back to Today after completion (same pattern as Settings sign-out).

### 7.3 Navigation away

- Tap Settings row → `navController.navigate(Screen.Settings.route)`
- Tap Sign in CTA → `navController.navigate(Screen.Auth.route)`
- Sign out → after completion, navigate to Today + clear backstack

---

## 8. Hardcoded Color Audit

Before declaring 5a done, every `Color(0xFF...)` literal in shipped code must be replaced with either:
- `MaterialTheme.colorScheme.X` for any color that maps semantically
- A new top-level token in `Color.kt` (e.g. `SyncRunningBg`, `HeatL3`, etc.) if Material color scheme doesn't fit

**Audit candidates (Phase 4 leftovers):**
- `HomeScreen.kt`: SyncStatusChip uses inline hex for amber/red/blue states — migrate to `SyncRunningBg`/`SyncRunningFg` tokens
- `StreakStrip.kt`: `FlameOrange = Color(0xFFFF6F00)` — promote to `Color.kt` as `FlameOrange`
- `Color.kt` `StreakComplete*`, `StreakFrozen*`, `StreakBroken*`, `StreakEmpty*`, `StreakTodayOutline` — keep as Phase 4 (already tokens)
- `StreakStrip.kt` `EarnedGreen()` — replace with `MaterialTheme.colorScheme.primary` or a `StreakComplete` token reference

This audit pass is a separate task in the plan, run after all 5 screen redesigns.

---

## 9. Error Handling

Phase 5a is visual. No new error paths.

- Font loading failure → Compose `Font(R.font.inter_regular)` falls back to system sans (Compose default). Acceptable.
- Bottom nav state stale → unlikely (recomposes on backstack change). Sanity-check via Compose preview at impl time.
- Color contrast regression → caught at manual smoke (every redesigned surface checked at WCAG AA in light + dark).
- Existing Phase 4 error flows (sync errors, auth failures, permission denials) untouched.

---

## 10. Testing

### 10.1 Unit

- `YouHubViewModelTest` — empty hub state, sign-out invokes container helper, auth state changes trigger UI flow.
- All existing Phase 3 + Phase 4 tests must keep passing.

### 10.2 Manual Smoke (close-out)

- [ ] Inter renders on every screen, no system-default fallback visible
- [ ] Instrument Serif renders for streak count + balance hero on DailyStatusCard
- [ ] JetBrains Mono renders where used (sync chip text, technical readouts if any)
- [ ] Background color is warm `#FAF8F4` light / `#121210` dark
- [ ] Bottom nav appears on Today, Streak, You only
- [ ] Bottom nav hidden on Auth, Onboarding, Settings, modal dialogs, sheets
- [ ] Tap Today/Streak/You — selected indicator updates correctly
- [ ] Today's DailyStatusCard: streak number + 7-day heatmap + KPI row + "View all" — no freeze chip, no per-habit chip, no exchange banner
- [ ] Today: pull-to-refresh works (manual sync only triggers indicator)
- [ ] Streak history: month calendar with heatmap, day-detail bottom sheet works
- [ ] You hub: Settings row → Settings, Account row shows email when signed in, Sign in CTA when guest
- [ ] Settings sign-out: dialog with unsynced count → spinner → returns to Today
- [ ] Onboarding: 4 steps work end-to-end
- [ ] Auth: sign up / sign in / Google / check email all functional
- [ ] WCAG AA verified light + dark for every redesigned surface
- [ ] Phase 4 tests still pass: streak compute, point rollover, notifications, sync

---

## 11. Migration / Compatibility

- **No DB migration.** No schema change.
- **No remote schema change.**
- **First launch after upgrade:** existing users see redesigned screens immediately. No onboarding re-run, no data loss.
- **Settings entry point change:** users who learned to tap the Settings icon on Home top bar will need to discover the You tab. Acceptable — bottom nav is more discoverable than overflow icon.

---

## 12. Open Decisions (resolved)

- ✅ Atomic migration, single PR
- ✅ Bundle fonts as `.ttf` in res
- ✅ You hub is empty placeholder (Settings + Account only)
- ✅ Bottom nav only on Today / Streak / You
- ✅ Migrate ALL hardcoded color literals to semantic tokens
- ✅ Dual StreakHistory entry (bottom nav + "View all")
- ✅ Unshipped feature hooks not rendered (no flags, no "Coming soon")

---

## 13. Phase 5a Definition of Done

- [ ] `Color.kt`, `Typography.kt`, `Theme.kt` rewritten per tokens.css
- [ ] Inter + Instrument Serif + JetBrains Mono `.ttf` files in `res/font/`, OFL licenses in `res/raw/`
- [ ] Bottom nav infrastructure in `BottomNav.kt`, conditionally rendered in AppNavigation
- [ ] You hub screen (placeholder) functional
- [ ] All 5 existing screens redesigned matching canvas mockups
- [ ] Settings icon removed from Home top app bar
- [ ] Hardcoded color audit complete; no `Color(0xFF...)` literals remain in shipped code
- [ ] All Phase 3/4 tests still pass
- [ ] New `YouHubViewModelTest` passes
- [ ] Manual smoke checklist (§10.2) passes on emulator
- [ ] WCAG AA verified light + dark
- [ ] PR opened against `main` with screenshots
