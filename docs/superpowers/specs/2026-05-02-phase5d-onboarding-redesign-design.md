# Phase 5d — Onboarding Identity Step Redesign

> **Branch:** `feature/phase5d-onboarding-redesign`. **Worktree:** `.worktrees/phase5d-onboarding-redesign`.

## Goal

Update the **identity step** of OnboardingScreen to follow canvas `OnboardIdentityMulti` (screens.jsx:2491). Three deltas:

1. Title and subtitle copy.
2. New "1–3" hint card between subtitle and identity grid.
3. Identity tile visuals — hue-tinted selected state with accent border + check badge, matching the AddIdentityStep1Screen treatment shipped in Phase 5c-2.

## Non-goals

- Habits / Wants / Sync onboarding steps. Untouched.
- Step indicator (`StepProgressBar`). Stays as-is for visual consistency across all four steps.
- Onboarding bottom bar. Current implementation preferred over the canvas's "{N} selected · Skip · Continue" version.
- Custom identity tile in the grid. Omitted entirely; users discover Custom later via `AddIdentityFlow` once Phase 5c custom identity ships.
- Multi-identity selection logic. Already implemented in 5b — selection state, click handlers, validation are unchanged.

## Architecture

Single-screen visual update. No data layer changes, no nav changes, no new files. Modify `IdentityStepBody` Composable (currently in `OnboardingScreen.kt` ~line 274) in place.

## Components

### Title + subtitle

In the existing `stepCopy(OnboardingStep.IDENTITY)` block (lines 76-79):

```kotlin
OnboardingStep.IDENTITY -> StepCopy(
    title = "Who are you becoming?",
    subtitle = "Pick everyone that's true. You'll see habits for each.",
)
```

(Was: "Who do you want to become?" / "Choose an identity. Habitto suggests habits that support it.")

### Hint card

New private Composable inside `OnboardingScreen.kt`:

```kotlin
@Composable
private fun PickHint(modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = StreakFrozenBg,        // existing token from theme (light frozen bg)
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
                tint = StreakFrozen,    // existing accent token
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

Render inside `IdentityStepBody`, immediately after the existing title/subtitle block and before the identity grid:

```kotlin
PickHint(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
```

(`StreakFrozenBg` and `StreakFrozen` already exist in the theme. If the imports differ, use whatever the existing FrozenOverlay code references.)

### Identity tile visuals

Replace the existing tile colors (currently `MaterialTheme.colorScheme.primaryContainer` for selected, `MaterialTheme.colorScheme.primary` for selected border) with hue-derived `Color.hsl` values matching `AddIdentityStep1Screen.IdentityCandidateTile` from Phase 5c-2:

- Selected tile background: `Color.hsl(IdentityHue.of(identity), saturation = 0.30f, lightness = 0.94f)`
- Selected tile border: `Color.hsl(IdentityHue.of(identity), saturation = 0.55f, lightness = 0.50f)`, 2dp width
- Unselected tile background: `MaterialTheme.colorScheme.surface`
- Unselected tile border: `MaterialTheme.colorScheme.outlineVariant`, 2dp width
- Check badge (top-right of selected tile, 22dp circle): `Color.hsl(hue, 0.55f, 0.50f)` bg, white check icon (14dp)

Adapt to the hue helper actually used in `AddIdentityStep1Screen` (likely `IdentityHue.of(...)` or a similar function). Read `AddIdentityStep1Screen.kt` and mirror exactly to keep both surfaces visually identical.

Inside the existing identity tile rendering loop, swap the color expressions. No structural change — same `LazyVerticalGrid` cells, same `IdentityAvatar`/`HabitGlyph` sizing, same name + description text.

## Data flow

Unchanged. `OnboardingViewModel.toggleIdentity(id)` already maintains a Set of selected identityIds. The screen just consumes `uiState.selectedIdentityIds`.

## Error handling

No new failure modes. Hint card and tile colors are static. If the StreakFrozen / StreakFrozenBg theme tokens are renamed in the future, this screen would need updating — but that's the same risk as the global `StreakStrip`.

## Testing

- **No unit tests.** UI-only change with no new logic.
- **Manual smoke:**
  - [ ] Fresh install → onboarding launches → identity step shows new title "Who are you becoming?".
  - [ ] Subtitle: "Pick everyone that's true. You'll see habits for each."
  - [ ] Hint card visible: lightbulb icon + "Most people pick **1–3** to start. You can add more later."
  - [ ] Tap an identity tile → background turns to hue-tinted light fill + accent border + check badge top-right.
  - [ ] Tap multiple → all stay selected (multi-select behavior preserved).
  - [ ] Tap Continue → advances to Habits step (existing flow unchanged).
  - [ ] Light + dark mode both render correctly (StreakFrozenBg has dark variant).

## Migration / rollout

- No schema changes.
- No data migration.
- Single APK deploy.
- Existing users who already finished onboarding never see this screen again — change only affects new onboarding flows (fresh install, account reset, sign-out + onboard).

## Open questions

None. Design locked.
