# Phase 9.1 — WantTimer UX Redesign

**Status:** in design.

**Branch target:** new branch `feature/phase9-1-timer-ux-redesign` off `main` after PR #23 merges. (Decision deferred to writing-plans step.)

**Source of truth:** canvas v5 `want-timer` DCSection (`canvas.html:424`) + `WantTimer` component (`screens.jsx:1111`) for the full-screen layout. Live-countdown notification has no canvas backing — this spec defines it. **Spec will be fed to Claude Design** to produce updated screens.

## Why

Phase 9 shipped an inline-only timer (banner + duration sheet on Want detail) plus a foreground service with live + completion notifications. Two gaps surfaced in smoke testing:

1. Canvas v5 defines a **full-bleed `WantTimer` screen** ("live cost" hero presentation, entry from Want detail) that was never built. Inline banner + canvas full-screen now conflict — two surfaces, no relationship.
2. **`want_timer_running` live notification was invented during implementation.** Catalog only includes `want_timer_end`. The visual style (body copy, progress, action affordances) is unspec'd.

## Goal

Land both surfaces with clear roles + spec the live notification — one coherent timer UX across Want detail, full-screen, and Android notification shade.

## Surface model: both, with roles

### Want detail

| Timer state | Want detail layout |
|--|--|
| No active timer, `unit == "min"` | HeroCard → "Start timer" button → bottom sheet on tap |
| No active timer, `unit != "min"` | HeroCard. **Start-timer button hidden.** |
| Active timer for THIS want | HeroCard → "Timer running · MM:SS" banner with Cancel pill. **Banner body tappable → opens full-screen WantTimer.** |
| Active timer for ANOTHER want | HeroCard → normal "Start timer" button. Tap → confirm-replace dialog (below). |

**Duration sheet:** 5 / 10 / 15 / 20 / 30 / 60 min chips (unchanged from Phase 9). On pick: start service → **auto-navigate to full-screen WantTimer**. Dismiss sheet.

**Confirm-replace dialog** (different-want overlap):
- Title: `Replace running timer?`
- Body: `You have a {minutesLeft} min timer for {otherWantName}. Starting a new one will log {elapsedMin} min and end it.` (Body switches to `…will discard it.` when `elapsedMin == 0`.)
- Buttons: `[Replace]` `[Keep]`.
- Replace path: partial-log + cancel old → proceed to duration sheet for new want.
- Keep path: dismiss, no state change.

**Same-want overlap:** no prompt. Treat as "restart" — silently partial-log old, start new.

### Full-screen `WantTimerScreen` (new)

Compose route `want-timer/{activityId}`. Full-bleed, no scaffold chrome competing with content. Entry points:

1. Auto-open after duration pick on Want detail.
2. Tap running banner on Want detail.
3. Tap live notification in shade.

Layout intent (Claude Design to detail):
- **LIVE COST hero** — large, dominant. Per-second visual emphasis on time + points-being-spent. Aligns with canvas v5 `want-timer` direction.
- Countdown: MM:SS, large display.
- Points-spent-so-far counter: refreshed every minute (matches notif cadence).
- Optional progress ring/arc around countdown.
- Want name + icon, small, above hero.
- **Cancel** CTA: prominent, full-width or large pill. Triggers partial-log path.
- Back arrow (top-left) + system back: navigate back to Want detail. **Timer keeps running.** Notification stays.

State on screen mounted while no timer running (e.g. orphan nav): show empty state ("No timer running") + Back button. Don't crash.

## Live-countdown notification

**Channel:** `want_timer_running` (LOW importance, no sound, no badge). Already created in Phase 9 — keep.

**Not in NotificationsSettings catalog.** Service-internal foreground notif. User mutes only via Android system channel settings. Master switch in app does NOT affect this notif (foreground service requires it).

**Style** (NotificationCompat.Builder):
- `setSmallIcon(R.drawable.ic_notification)`.
- `setLargeIcon(...)` — optional, use the want's iconKey if cheaply resolvable (skip if expensive).
- Title: `"{Want name} timer"`.
- Body: `"X min left · −Y pt spent"` — `X` = `ceil(remainingSec / 60)`, `Y` = points spent so far (running counter, minute-granular).
- Progress: `setProgress(totalMin, elapsedMin, false)` (determinate). Updates on minute boundary.
- Action: single `[Cancel]` button → triggers `WantTimerService.ACTION_STOP_PARTIAL_LOG` (new action constant).
- Tap (content intent): deep link → full-screen `WantTimerScreen` for this activityId. Falls back to Want detail if the route fails.
- `setOngoing(true)` + `setOnlyAlertOnce(true)` (no repeat ticker noise on minute updates).

**Update cadence:** minute boundary. Reuse current tick-loop logic:
```
tickDelay = ((remainingSec % 60).coerceAtLeast(1) * 1000L).coerceAtMost(60_000L)
```

**Completion:**
- Service cancels `NOTIF_RUNNING_ID` and fires `NOTIF_END_ID` on `want_timer_end` channel (HIGH, sound + vibrate). Body unchanged from Phase 9: `"{Want name} timer finished · {N} min logged · −{pt} pt"` (omit logged segment if non-min).
- End-notif tap → opens Want detail (not full-screen — timer is done).

## Cancel semantics: partial-log

Triggered by: banner Cancel pill, full-screen Cancel CTA, live-notif Cancel action.

```
elapsedMin = floor((now - startedAt).inWholeMinutes)
if want.unit == "min" && elapsedMin >= 1:
    LogWantUseCase.execute(taps = elapsedMin, deviceMode = THIS_DEVICE)
    toast = "Logged ${elapsedMin} min · −${pointsSpent} pt"
else:
    toast = "Timer cancelled"
state → CANCELLED
service stops, removes NOTIF_RUNNING_ID
```

Edge: if `LogWantUseCase` throws `InsufficientPointsException` (unlikely mid-timer but possible after rapid logs elsewhere), swallow + log warning + skip log. Don't block cancel. Toast: `"Timer cancelled (no log — insufficient points)"`.

## Non-min units

- UI gate: `WantDetailScreen` renders Start-timer button only when `want.unit == "min"`.
- Defensive: running banner still renders if a RUNNING row exists for this want (unit may have been edited mid-timer).
- `WantTimerController.start(...)` keeps `require(durationSec in 1..86400)`. No unit check at controller layer — UI is the gate.
- Service auto-log path keeps `if (activity.unit == "min")` guard (Phase 9 behavior). `WantTimerRecovery.scanOnStart` keeps same guard.

## Implementation map

| File | Change |
|--|--|
| `WantDetailScreen.kt` | Conditional Start-timer button on unit. Banner body tappable → nav. Overlap-confirm dialog. |
| `WantDetailViewModel.kt` | `pendingOverlapPrompt: PendingOverlap?` state. `requestStart(durationSec)` handles overlap detection. `confirmReplace()` + `dismissOverlap()` methods. Nav callback for full-screen open. |
| `WantTimerScreen.kt` (new) | Full-bleed Compose screen. Uses `WantTimerViewModel`. |
| `WantTimerViewModel.kt` (new) | Observes `wantTimerRepository.getActive(userId)` at 1Hz. Computes remaining + elapsed + pointsSpentSoFar. `cancel()` → `WantTimerController.cancelWithPartialLog`. |
| `WantTimerController.kt` | Add `suspend fun cancelWithPartialLog(userId)`. Replace existing `cancel(userId)` callers. |
| `WantTimerService.kt` | Notif body + progress + larger title. Tap target = deep link to full-screen. New `ACTION_STOP_PARTIAL_LOG` (action button intent). |
| `AppNavigation.kt` | Route `want-timer/{activityId}` → `WantTimerScreen`. |
| `AndroidManifest.xml` | Deep-link path `want-timer/{activityId}` on MainActivity intent-filter (existing scheme `com.jktdeveloper.habitto`). |

No SQLDelight schema changes. No new notification types in catalog. No new channels.

## Tests

- `WantTimerControllerTest`: add `cancelWithPartialLog` cases (min-unit elapsed≥1 → log; min-unit elapsed=0 → no log; non-min unit → no log; InsufficientPointsException → swallowed).
- `WantDetailViewModelTest`: overlap-prompt transitions (same want → no prompt; different want with running → prompt; confirm → partial-log + start new; dismiss → no change).
- `WantTimerViewModelTest` (new): 1Hz observe; cancel path triggers controller.
- Robolectric for service-level behavior changes (notif Cancel action triggers partial-log).
- Manual smoke: emulator install, start 5-min timer → confirm auto-nav to full-screen → back → banner shows → tap banner → full-screen again → cancel from full-screen → partial-log toast → check WantLog row.

## Migration choreography

1. Spec → Claude Design produces updated screens.
2. `writing-plans` skill produces implementation plan from this spec.
3. Implementation order: Controller change → ViewModel changes → new full-screen → service notif change → nav route → manifest → tests → smoke.
4. PR #23 (Phase 9) merges first. New branch `feature/phase9-1-timer-ux-redesign` off `main` carries this work.

## Open risks

- **Live notif tap → full-screen via deep link**: requires correct intent-filter + activity launch mode handling. SINGLE_TOP + clear-task semantics need testing. Fallback to Want detail acceptable if deep link fails.
- **`pointsSpentSoFar` on live notif body**: requires reading `activity.unitsPerPoint` + computing per-minute. Cheap, but caches activity once per service lifetime to avoid repeated DB hits.
- **Partial-log on cancel mid-LogWantUseCase race**: if user spam-taps Cancel, ensure idempotent state transition (CANCELLED set once). Use repo `setState` atomicity.

## Out of scope

- `bar_raised` / `bar_dropped` (Phase 10 — needs auto-target engine).
- Pause / resume functionality.
- Snooze action.
- Concurrent timers (one per want simultaneously).
- iOS layouts.
- Widgets.
- Sound / vibrate customization UI.
- Cross-device timer sync.
- Removing duplicate Phase 4 inline notification toggles in `SettingsScreen.kt` (separate follow-up).
