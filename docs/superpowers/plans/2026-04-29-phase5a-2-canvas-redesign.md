# Phase 5a-2: Canvas Redesign — actual screen layouts to match Claude Design

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement task-by-task.

**Goal:** Translate the JSX layouts from `/tmp/habitto-design/habitto/project/{home.jsx,screens.jsx,shared.jsx}` into Compose, matching the canvas mockups. Phase 5a (PR #7) shipped the token foundation + bottom nav; this phase replaces the screen interiors.

**Hide-unshipped rule (carries over from 5a):** no multi-identity chip strip, no freeze chip on Home, no per-habit streak micro-chip, no exchange-rate banner. Their dedicated screens come in 5b/5c/7.

**Worktree:** `.worktrees/phase5a-2-canvas-redesign`
**Branch:** `feature/phase5a-2-canvas-redesign`
**Design source:** `/tmp/habitto-design/habitto/project/{home.jsx,screens.jsx,shared.jsx,tokens.css}`

---

## File Structure

### Created

| File | Responsibility |
|---|---|
| `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/components/HabitGlyph.kt` | Tinted-circle habit icon — used on Home habit cards + Onboarding step 2 |
| `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/components/StepProgressBar.kt` | 4-step progress bar for Onboarding |
| `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/components/SyncChip.kt` | Pill chip with status (idle/running/synced/error) — extracted from HomeScreen, reused |

### Modified

| File | What changes |
|---|---|
| `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/home/HomeScreen.kt` | Top bar collapsed to "habitto" title + sync chip only (no settings icon — already done in 5a). Layout per home.jsx: identity strip slot (empty, render Spacer placeholder), DailyStatusCard, "Today's habits" section, habit cards new layout with HabitGlyph + tap-pending drain bar |
| `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/streak/StreakStrip.kt` | Match home.jsx DailyStatusCard exactly: 44dp flame container with state-based bg, streak number `headline numeral 44sp` (Instrument Serif), supporting line, 7-day heatmap row with day labels (M T W T F S S), HorizontalDivider, KPI row with vertical dividers between columns + Instrument Serif numerals + uppercase labels |
| `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/streak/StreakHistoryScreen.kt` | Match screens.jsx StreakHistory: top bar, summary card, month calendar list with weekday header row, lazy-load older months, day-detail bottom sheet on cell tap |
| `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/streak/MonthCalendar.kt` | Match screens.jsx MonthCalendar layout — month label, weekday header, padded grid |
| `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/settings/SettingsScreen.kt` | Match screens.jsx SettingsScreen: Section + Row primitives, master toggle, 4 notification rows, Account section, About section, LogoutDialog, permission banner |
| `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/auth/AuthScreen.kt` | Match screens.jsx AuthScreen: 4 variants (signin/signup/check/google), Input primitive, password toggle, "Continue with Google" outlined button with G logo, "or" divider |
| `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/onboarding/OnboardingScreen.kt` | Match screens.jsx OnboardStep: 4-step progress bar at top, step content (identity grid, habits multi-select with HabitGlyph, wants multi-select with cost-per-unit, sign-in CTA), Back/Next bottom bar |

---

## Tasks

### Task 1: Shared primitives — HabitGlyph, StepProgressBar, SyncChip extraction
**Files:** `ui/components/HabitGlyph.kt`, `ui/components/StepProgressBar.kt`, `ui/components/SyncChip.kt`. Modify `HomeScreen.kt` to import the extracted SyncChip.

### Task 2: Redesign DailyStatusCard (StreakStrip.kt) per home.jsx
**Files:** `ui/streak/StreakStrip.kt`.

### Task 3: Redesign HomeScreen layout per home.jsx (sans unshipped hooks)
**Files:** `ui/home/HomeScreen.kt`.

### Task 4: Redesign StreakHistoryScreen + MonthCalendar per screens.jsx
**Files:** `ui/streak/StreakHistoryScreen.kt`, `ui/streak/MonthCalendar.kt`.

### Task 5: Redesign SettingsScreen per screens.jsx
**Files:** `ui/settings/SettingsScreen.kt`.

### Task 6: Redesign AuthScreen per screens.jsx (Google button + variants)
**Files:** `ui/auth/AuthScreen.kt`.

### Task 7: Redesign OnboardingScreen per screens.jsx (4 steps)
**Files:** `ui/onboarding/OnboardingScreen.kt`.

### Task 8: Manual verification + close-out PR
**Files:** none (smoke + git operations).

---

> Each task: read the corresponding JSX section in `/tmp/habitto-design/habitto/project/`, translate to Compose with our tokens (HabitTypography roles, MaterialTheme.colorScheme, Spacing, FlameOrange, Heat ramp). Use `NumeralStyle` (Instrument Serif) for streak number + balance + KPI numerals.
>
> Tasks 2-7 each commit independently. Build green required between tasks. All Phase 3/4 unit tests must keep passing.
