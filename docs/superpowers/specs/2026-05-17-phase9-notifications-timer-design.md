# Phase 9 — Notifications + Want Timer

**Status:** in design.

**Branch:** `feature/phase9-notifications-timer` (worktree `.worktrees/phase9-notifications-timer`, off `main` post Phase 8 merge).

**Source of truth:** canvas v5 bundle (Claude Design). Re-fetch path: `https://api.anthropic.com/v1/design/h/-ud7FPa19bnsVbQTA99Jdg?open_file=canvas.html`. Local extraction during work: `/tmp/habitto-design/habitto/project/`.

Canvas refs:
- `canvas.html:424` — DCSection `want-timer` ("Full-bleed live cost. Entry from Want detail").
- `canvas.html:442` — DCSection `notifications` ("Standardized notification system · 13 types · 4 categories").
- `screens.jsx:1111` — `WantTimer` component.
- `screens-v4.jsx:10-40` — `N_CATEGORIES` + `NOTIFICATIONS` catalog.
- `screens-v4.jsx:47-110` — `NotificationCard`.
- `screens-v4.jsx:112-174` — `NotificationCatalog`.
- `screens-v4.jsx:184-358` — `NotificationsSettings` (grouped/separate variants + permission-blocked/master-off states).
- `screens-v4.jsx:359+` — `NotificationPermissionPrompt`.

## Why

Phase 4 shipped 2 notification types (`daily_reminder`, `streak_risk`) plus the WorkManager scaffold. Canvas v5 standardizes 13 notification types in 4 categories with a unified Settings surface. Phase 5+ backlog includes an on-device want timer with alarm on completion. Both ship together — timer end is one of the 13 notification types, sharing the Settings + Permission infra.

## Goal

One sentence: **ship a full canvas-aligned notification system (11 of the 13 types — bar_raised/dropped deferred to Phase 10 pending auto-target-adjust engine) plus a foreground-service want timer with live mm:ss countdown.**

## Want timer

### Domain

```kotlin
data class WantTimer(
    val id: String,
    val userId: String,
    val activityId: String,
    val durationSec: Int,
    val startedAt: Instant,
    val endsAt: Instant,
    val state: WantTimerState,
)

enum class WantTimerState { RUNNING, FINISHED, CANCELLED }
```

Local-only — no sync. Single-row-at-a-time semantics: starting a new timer cancels the previous (atomic in a SQLDelight transaction).

### Schema — SQLDelight migration 9

```sql
CREATE TABLE IF NOT EXISTS LocalWantTimer (
    id TEXT NOT NULL PRIMARY KEY,
    userId TEXT NOT NULL,
    activityId TEXT NOT NULL,
    durationSec INTEGER NOT NULL,
    startedAt INTEGER NOT NULL,
    endsAt INTEGER NOT NULL,
    state TEXT NOT NULL
);
```

No server migration (device-local).

### Service

`WantTimerService : Service` in `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/timer/`. `startForeground` with notification on `want_timer_running` channel (LOW importance, ongoing, no sound). Notification body updates every minute with mm:ss remaining (per canvas — minute granularity to conserve battery). Action button: "Cancel".

At `endsAt`:
1. If `activity.unit == "min"`: auto-log via `LogWantUseCase.execute(userId, activityId, taps = floor(durationSec / 60 / max(1, activity.unitsPerPoint)), deviceMode = DeviceMode.THIS_DEVICE)`. For non-min units (cups, meals, sessions): skip auto-log; user logs manually.
2. Post completion notification on `want_timer_end` channel (HIGH importance, sound + vibrate). Body: `"{activity.name} timer finished · {minutes} min logged · {pt} pt spent"` (omit "{N} logged" segment if no auto-log). Tap → WantDetail intent.
3. Transition `WantTimer.state = FINISHED`, persist.
4. `stopForeground(STOP_FOREGROUND_REMOVE)` + self-stop.

Cancel via notification action: transition `state = CANCELLED`, no log, stop self.

### UI

`WantDetailScreen` "Start timer" button (currently stub) opens duration bottom sheet — chips for 5/10/15/20/30/60 min. Confirm → `WantTimerService.start(activityId, durationSec)`. While running: `WantDetailScreen` shows a "Timer running · mm:ss · Cancel" banner above the hero. Cancel button stops the service.

## Notifications

### Channels — grouped topology (4 Android channels)

| Channel id | Importance | Sound |
|--|--|--|
| `reminder` | DEFAULT | gentle |
| `alert` | HIGH | yes + vibrate |
| `status` | DEFAULT | none |
| `system` | LOW | none |

Plus the two timer channels (`want_timer_running` LOW ongoing, `want_timer_end` HIGH) — kept separate so user can mute live countdown without losing the completion alert.

`NotificationChannels.kt` extended with all six.

### Type catalog (11 ship in Phase 9; 2 deferred)

| Id | Category | Default | hasTime | Trigger source |
|--|--|--|--|--|
| `daily_reminder` | reminder | on, 09:00 | ✓ | `DailyReminderWorker` (existing — rebind copy) |
| `daily_reminder_per_identity` | reminder | off, 17:30 | ✓ | new `PerIdentityReminderScheduler` — one WorkRequest per active identity. Body: "{IdentityName} hasn't shown up today" |
| `streak_risk` | alert | on, 21:00 | ✓ | `StreakRiskWorker` (existing — rebind) |
| `want_timer_end` | alert | on | — | `WantTimerService` on completion |
| `streak_frozen` | status | on | — | `DayBoundaryWorker` when freeze applied (frozen-day detected) |
| `streak_reset` | status | on | — | `DayBoundaryWorker` when streak broken |
| `tier_advanced` | status | on | — | post-day-rollover when current rate tier > yesterday's. Fired inside `DayBoundaryWorker`. Body: "Tier {N} unlocked — {rate}× spending" |
| `milestone_streak` | status | on | — | new `MilestoneWorker` runs post-day-rollover; checks 7/30/100/365 day thresholds; uses `NotificationFiringDateStore` for dedup. Body: "{N}-day streak — keep going" |
| `session_expired` | system | on | — | `SyncEngine` on auth-failure SyncState |
| `cloud_restore_complete` | system | on | — | `SyncEngine` after first-pull success on fresh install/login |
| `sync_failed_persistent` | system | on | — | after 3 consecutive failed sync runs (tracked by `SyncEngine`) |

**Deferred to Phase 10**: `bar_raised`, `bar_dropped` (require dynamic daily-target adjust engine — not yet built).

### Preferences

Extend `NotificationPreferences` (existing DataStore):
- `notificationsMaster: Boolean` (default true) — kill switch.
- `enabled: Map<NotificationTypeId, Boolean>` — per-type opt-in. Defaults per catalog table above.
- `time: Map<NotificationTypeId, LocalTime>` — only for `hasTime` types.

### Settings screen

`NotificationsSettings` composable per canvas grouped variant. Sections per category (Reminder, Alert, Status, System) with type rows underneath. Row:
- Tinted icon (per `n.tint`).
- Title + body preview.
- Right-side toggle (off → type disabled).
- For `hasTime` types: tap row → Material time picker bottom sheet.

Two state variants per canvas:
- `permission-blocked`: top banner "Notifications blocked by Android. Open system Settings." → intent to app notification settings.
- `master-off`: list dimmed; "Notifications muted" banner at top with toggle to turn back on.

Settings entry: You hub → Settings → "Notifications" row.

### Permission prompt

`NotificationPermissionPrompt` shown on first launch after install when `POST_NOTIFICATIONS` not granted (Android 13+). Rationale per canvas + "Allow" CTA → Android system permission dialog; "Skip" → dismiss + remember (no nag).

## Triggers — implementation map

| Existing file/component | Phase 9 change |
|--|--|
| `DailyReminderWorker` | rebind to new copy + channel `reminder` + dedup via `NotificationFiringDateStore` |
| `StreakRiskWorker` | rebind to channel `alert` + new copy |
| `DayBoundaryWorker` | gains 3 fire paths: `streak_frozen`, `streak_reset`, `tier_advanced`. Pre-existing day-rollover work continues |
| `NotificationScheduler` | adds 2 new workers/schedulers (`MilestoneWorker`, `PerIdentityReminderScheduler`), routes by `NotificationPreferences` |
| `NotificationChannels` | adds 4 grouped channels + 2 timer channels |
| `NotificationFiringDateStore` | extended key space for new types |
| `NotificationPreferences` | adds per-type Map + master switch |
| `SyncEngine` | adds 3 notification fire hooks: session_expired, cloud_restore_complete, sync_failed_persistent (after 3 retries) |
| `WantDetailScreen` | replace "Start timer" stub with bottom sheet + service start |
| `WantDetailViewModel` | observe active `WantTimer` row + expose running state to Screen |
| `AppContainer` | wires `WantTimerService`-related repos, `MilestoneWorker`, `PerIdentityReminderScheduler` |

## Tests

- `WantTimerService` start/cancel/auto-log/non-min unit paths (Robolectric).
- `MilestoneWorker` threshold detection + dedup.
- `PerIdentityReminderScheduler` reconcile (add/remove identity).
- `DayBoundaryWorker` new fire paths (streak_frozen, streak_reset, tier_advanced).
- `NotificationPreferences` defaults + Map round-trip.
- `NotificationsSettings` composable smoke (Robolectric or @Preview).
- `SyncEngine` post-failure notification firing (3-strike rule).

## Migration choreography

1. SQLDelight migration 9 — `LocalWantTimer` table.
2. `NotificationChannels` + `NotificationPreferences` extend (no migration; DataStore additive).
3. Workers + Service implementations.
4. Settings + Permission UI.
5. WantDetail timer entry.
6. Tests.
7. Smoke: clear app data + reinstall; run through onboarding + start timer + verify completion notification + open Settings → toggle types + check Android notification settings shows 6 channels.

No server migration (timer + prefs are device-local).

## Open risks

- **POST_NOTIFICATIONS permission denial**: app still functions but no notifications. Settings shows `permission-blocked` banner.
- **Foreground service battery**: limit update cadence to minute boundaries (per canvas).
- **Service killed by OS during low-memory**: WantTimer DB row outlives the service. On app restart, check for RUNNING timer past its `endsAt` → mark FINISHED + post completion notification (recovery path).
- **Per-identity reminder volume**: user with 5 identities = 5 separate notifications at 17:30. Acceptable per canvas; can revisit if noisy.

## Out of scope

- `bar_raised` / `bar_dropped` — require dynamic auto-target-adjust engine (Phase 10 candidate).
- Notification action buttons beyond "Cancel" on timer + "Open Settings" on permission banner. No "Snooze" action.
- Per-habit reminder (canvas surfaces per-identity, not per-habit).
- iOS notifications.
- Android widgets.
- Cross-device timer sync.
- Sound + vibrate customization (system defaults per channel).
