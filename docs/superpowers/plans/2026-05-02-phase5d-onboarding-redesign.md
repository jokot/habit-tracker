# Phase 5d Onboarding Identity Step Redesign — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Update the Identity step of `OnboardingScreen` to match canvas `OnboardIdentityMulti` — new title + subtitle copy, new "1–3" hint card, and tile-color lightness aligned with `AddIdentityStep1Screen`.

**Architecture:** Single-file change in `OnboardingScreen.kt`. Modify `stepCopy(IDENTITY)` text, add a `PickHint` private composable, render it inside `IdentityStepBody`, and tweak `IdentityGridCell` selected-bg lightness from `0.92f` to `0.94f` to match the canonical `IdentityCandidateTile` from Phase 5c-2.

**Tech Stack:** Compose Material 3, existing `IdentityHue.forIdentityId(...)`, existing theme tokens `StreakFrozen` / `StreakFrozenBg`.

**Worktree:** `.worktrees/phase5d-onboarding-redesign`. **Branch:** `feature/phase5d-onboarding-redesign`. **Spec:** `docs/superpowers/specs/2026-05-02-phase5d-onboarding-redesign-design.md`.

---

## File Map

**Modify (single file):**
- `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/onboarding/OnboardingScreen.kt`

No new files. No tests (UI-only change). No other onboarding steps touched.

---

## Task 1: Update title + subtitle copy

**File:**
- Modify: `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/onboarding/OnboardingScreen.kt:75-79`

- [ ] **Step 1: Replace `stepCopy(OnboardingStep.IDENTITY)` body**

Find the existing block:

```kotlin
OnboardingStep.IDENTITY -> StepCopy(
    title = "Who do you want to become?",
    subtitle = "Choose an identity. Habitto suggests habits that support it.",
)
```

Replace with:

```kotlin
OnboardingStep.IDENTITY -> StepCopy(
    title = "Who are you becoming?",
    subtitle = "Pick everyone that's true. You'll see habits for each.",
)
```

- [ ] **Step 2: Build to verify compile**

```bash
cd /Users/jokot/dev/habit-tracker/.worktrees/phase5d-onboarding-redesign
rtk ./gradlew :mobile:androidApp:compileDebugKotlinAndroid
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
rtk git add mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/onboarding/OnboardingScreen.kt
rtk git commit -m "$(cat <<'EOF'
feat(onboarding): identity step copy — "Who are you becoming?"

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 2: Add PickHint composable + render in IdentityStepBody

**File:**
- Modify: `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/onboarding/OnboardingScreen.kt`

- [ ] **Step 1: Add `Lightbulb` icon import + theme color imports**

Add the following imports near the top of the file (alongside the existing icon imports):

```kotlin
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import com.jktdeveloper.habitto.ui.theme.StreakFrozen
import com.jktdeveloper.habitto.ui.theme.StreakFrozenBg
import com.jktdeveloper.habitto.ui.theme.StreakFrozenBgDark
import com.jktdeveloper.habitto.ui.theme.StreakFrozenDark
```

(If any of these imports already exist in the file, do not duplicate.)

- [ ] **Step 2: Add `PickHint` private composable**

Insert this composable in the file. A clean location is just before the existing `IdentityStepBody` definition (around line 273):

```kotlin
@Composable
private fun PickHint(modifier: Modifier = Modifier) {
    val isDark = isSystemInDarkTheme()
    val bg = if (isDark) StreakFrozenBgDark else StreakFrozenBg
    val accent = if (isDark) StreakFrozenDark else StreakFrozen
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = bg,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Lightbulb,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = buildAnnotatedString {
                    append("Most people pick ")
                    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append("1–3") }
                    append(" to start. You can add more later.")
                },
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
```

- [ ] **Step 3: Render `PickHint` inside `IdentityStepBody`**

The current `IdentityStepBody` (around line 274) starts directly with a `LazyVerticalGrid`. Wrap it in a `Column` so the hint can sit above the grid. Replace the body of `IdentityStepBody` with:

```kotlin
@Composable
private fun IdentityStepBody(
    identities: List<Identity>,
    selectedIds: Set<String>,
    onToggle: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
    ) {
        PickHint(modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(top = 0.dp, bottom = 96.dp),
        ) {
            items(identities, key = { it.id }) { identity ->
                IdentityGridCell(
                    identity = identity,
                    selected = identity.id in selectedIds,
                    onSelect = { onToggle(identity.id) },
                )
            }
        }
    }
}
```

(Notes:
- The outer `Column` takes the previous horizontal padding; the inner `LazyVerticalGrid` no longer needs it.
- `contentPadding.top = 0.dp` because the `PickHint` already provides spacing below itself.
- `bottom = 96.dp` preserves the existing space for the sticky bottom bar.)

- [ ] **Step 4: Build to verify**

```bash
rtk ./gradlew :mobile:androidApp:compileDebugKotlinAndroid
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
rtk git add mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/onboarding/OnboardingScreen.kt
rtk git commit -m "$(cat <<'EOF'
feat(onboarding): "Most people pick 1–3" hint card above identity grid

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 3: Align tile selected-bg lightness with AddIdentityStep1Screen

**File:**
- Modify: `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/onboarding/OnboardingScreen.kt:304`

The current onboarding tile uses `Color.hsl(hue, 0.30f, 0.92f)` for selected background. `AddIdentityStep1Screen.IdentityCandidateTile` (Phase 5c-2 canonical version) uses `0.94f`. Tiny visual drift; align for cross-screen consistency.

- [ ] **Step 1: Update lightness value**

In `IdentityGridCell`, find the line:

```kotlin
val selectedBg = Color.hsl(hue = hue, saturation = 0.30f, lightness = 0.92f)
```

Replace with:

```kotlin
val selectedBg = Color.hsl(hue = hue, saturation = 0.30f, lightness = 0.94f)
```

- [ ] **Step 2: Build to verify**

```bash
rtk ./gradlew :mobile:androidApp:compileDebugKotlinAndroid
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
rtk git add mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/onboarding/OnboardingScreen.kt
rtk git commit -m "$(cat <<'EOF'
refactor(onboarding): align identity tile lightness with AddIdentityStep1Screen

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 4: Manual smoke + push + PR

**Files:** none (validation + git ops).

- [ ] **Step 1: Run full test suite**

```bash
cd /Users/jokot/dev/habit-tracker/.worktrees/phase5d-onboarding-redesign
rtk ./gradlew :mobile:shared:testDebugUnitTest :mobile:androidApp:testDebugUnitTest
```

Expected: BUILD SUCCESSFUL, all existing tests pass (no new tests added).

- [ ] **Step 2: Install + smoke**

```bash
rtk ./gradlew :mobile:androidApp:installDebug
```

Then on the device or emulator:

- [ ] Fresh install OR clear app data → onboarding launches.
- [ ] Identity step shows title **"Who are you becoming?"** (was: "Who do you want to become?").
- [ ] Subtitle: **"Pick everyone that's true. You'll see habits for each."**.
- [ ] Below subtitle: hint card with light cyan/frozen background, lightbulb icon (cyan), text **"Most people pick 1–3 to start. You can add more later."** with "1–3" in semibold.
- [ ] Tap any identity tile → background turns light hue tint, accent border, white check badge top-right.
- [ ] Tap multiple identities → all stay selected; counter at bottom updates as before.
- [ ] Continue button enabled with ≥1 selected → advances to Habits step (existing flow unchanged).
- [ ] Toggle dark mode → hint card uses dark frozen tokens (`StreakFrozenBgDark` + `StreakFrozenDark`); tiles still readable.

- [ ] **Step 3: Push branch**

```bash
rtk git push -u origin feature/phase5d-onboarding-redesign
```

- [ ] **Step 4: Create PR**

```bash
rtk gh pr create --base main --head feature/phase5d-onboarding-redesign \
    --title "Phase 5d: onboarding identity step redesign" \
    --body "$(cat <<'BODY'
## Summary

Identity step of OnboardingScreen now matches canvas `OnboardIdentityMulti`:

- Title: "Who do you want to become?" → **"Who are you becoming?"**
- Subtitle: "Choose an identity. Habitto suggests habits that support it." → **"Pick everyone that's true. You'll see habits for each."**
- New hint card between subtitle and grid: lightbulb icon + "Most people pick 1–3 to start. You can add more later." (frozen-bg surface)
- Identity tile selected-bg lightness aligned with `AddIdentityStep1Screen` (`0.92f` → `0.94f`)

## Out of scope

- Habits / Wants / Sync onboarding steps unchanged
- Onboarding bottom bar unchanged (current preferred over canvas's "{N} selected · Skip · Continue")
- Step indicator (`StepProgressBar`) unchanged for cross-step consistency
- Custom identity tile not added (deferred to Phase 5c custom identity)

## Test plan
- [x] `:mobile:shared:testDebugUnitTest` green
- [x] `:mobile:androidApp:testDebugUnitTest` green (no new tests)
- [x] Manual: identity step copy + hint card + tiles render correctly in light + dark mode
- [x] Manual: Continue advances to Habits step (multi-identity flow unchanged)

🤖 Generated with [Claude Code](https://claude.com/claude-code)
BODY
)"
```

---

## Self-Review

**Spec coverage:**
- Title + subtitle update → Task 1 ✓
- Hint card (PickHint composable + render in IdentityStepBody) → Task 2 ✓
- Tile color alignment → Task 3 ✓
- Manual smoke + push + PR → Task 4 ✓

Note: the spec described "tile visuals — hue-tinted selected state with accent border + check badge" as a major delta. After reading the existing `IdentityGridCell` in `OnboardingScreen.kt`, that visual is **already in place** (hue-tinted bg, accent border, white check badge top-right). The only real divergence from `AddIdentityStep1Screen` is the lightness value (`0.92f` vs `0.94f`). Task 3 closes that gap. The spec's broader "match AddIdentityStep1Screen visuals" requirement is therefore satisfied by Task 3 alone — no full visual rewrite needed.

**Placeholder scan:** none.

**Type consistency:**
- `PickHint` signature `(modifier: Modifier = Modifier)` consistent with usage at the call site.
- Theme tokens `StreakFrozen` / `StreakFrozenBg` / `StreakFrozenDark` / `StreakFrozenBgDark` confirmed to exist in `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/theme/Color.kt`.
- `IdentityHue.forIdentityId(...)` already used by both the existing onboarding tile and `AddIdentityStep1Screen` — no new helper needed.
- `Icons.Outlined.Lightbulb` is in `material-icons-extended` (already on classpath per existing usage of other outlined icons in 5c-2).
