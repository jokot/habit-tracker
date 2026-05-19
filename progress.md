# Habit Tracker — Progress

**Last updated:** 2026-05-19
**Active branch:** `feature/phase9-notifications-timer` (26 commits ahead of main, pushed, no PR yet)
**Main tip:** `c55dc5a` (Merge PR #22, phase 8)

## Merged phases

| Phase | Scope | Merge |
|--|--|--|
| 1 | Foundation: KMP + SQLDelight + Compose | ✅ |
| 2 | Core loop: HabitLog + WantLog + points + Today | ✅ |
| 3 | Sync + auth: Supabase + Google Sign-In | ✅ |
| 4 | Streaks + notifications scaffold (3 channels, 2 worker types) | ✅ |
| 5a | Design system + canvas v1 redesign | ✅ |
| 5a-2 | Canvas v2 redesign | ✅ |
| 5b | Multi-identity | ✅ |
| 5c (+1, +2) | Identity management + CRUD | ✅ |
| 5d | Onboarding redesign | ✅ |
| 5e (+2, +3) | Habit CRUD | ✅ |
| dev-tools | Internal tooling | ✅ |
| 6 | Exchange rate (tier ladder 1×–2× by streak) | ✅ |
| 7 | Want CRUD (PR #21) | ✅ |
| 8 | Onboarding seeds + 88 templates (PR #22) | ✅ |

## Phase 9 — in flight

**Notifications + Want timer.** Spec: `docs/superpowers/specs/2026-05-17-phase9-notifications-timer-design.md`. Plan: `docs/superpowers/plans/2026-05-17-phase9-notifications-timer.md`.

Shipped on branch (not yet merged):
- 11 canvas notification types in 4 grouped channels (`reminder`/`alert`/`status`/`system`) + 2 timer channels.
- SQLDelight migration 9 → `LocalWantTimer` (local-only).
- `WantTimerService` (foreground, mm:ss countdown, auto-log on `unit=="min"`, app-start recovery).
- `NotificationsSettingsScreen` + first-launch `NotificationPermissionPromptHost`.
- Per-identity reminders + reconcile observer on identity changes.
- `DayBoundaryWorker` emits `tier_advanced`; `MilestoneWorker` handles 7/30/100/365.
- `SyncEngine` observer fires `session_expired` / `cloud_restore_complete` / `sync_failed_persistent` (3-strike).

Tests green: `:mobile:androidApp:testDebugUnitTest` + `:mobile:shared:jvmTest`. `:mobile:androidApp:assembleDebug` SUCCESSFUL.

## Phase 9 open follow-ups

- Open PR via web URL or `gh pr create`.
- Manual smoke test on emulator: install, grant permission, start 5-min timer, verify completion notif + auto-logged row, toggle master + per-type in NotificationsSettings, confirm 6 channels in Android system settings.
- Cleanup: `SettingsScreen.kt` still has Phase 4 inline notification toggles AND the new nav row to `NotificationsSettingsScreen` — duplicate UI. Remove inline toggles in a follow-up.
- Subtle: WantTimer `taps = (durationSec/60).coerceAtLeast(1)` ignores `unitsPerPoint`. Spec wanted `floor(durationSec / 60 / max(1, unitsPerPoint))`. OK if seed `unit=="min"` rows all use `unitsPerPoint=1` — verify.

## Deferred / backlog

### Phase 10 candidates (notifications)
- `bar_raised` + `bar_dropped` notifications — require auto-target-adjust engine (doesn't exist yet).

### Streak correctness
- `habit.effectiveFrom` field — past streak stability when adding/removing habits or identities. Deferred from Phase 5c-2. New habits currently backfill all prior days, distorting streaks.

### Sync
- **GH #20** — Sync pull has no pagination. >1000 logs truncated on first sync. Deferred until heavy-user complaint or sync-focused work.

### Onboarding
- Old onboarding screen still lives alongside new canvas `OnboardIdentityMulti` screen (title "Who are you becoming?", screens.jsx:2490). Remove old screen on cleanup pass.

### Docs / repo hygiene
- **GH #4** — README missing.
- Keep `progress.md` updated as phases ship.

### Out of scope (deferred indefinitely per Phase 9 spec)
- iOS notifications.
- Android widgets.
- Snooze action on notifications.
- Custom sound + vibrate config.
- Cross-device timer sync.

## Resume on another laptop

```bash
git clone https://github.com/jokot/habit-tracker.git
cd habit-tracker
git worktree add -b feature/phase9-notifications-timer \
  .worktrees/phase9-notifications-timer \
  origin/feature/phase9-notifications-timer
cd .worktrees/phase9-notifications-timer
./gradlew :mobile:androidApp:testDebugUnitTest :mobile:shared:jvmTest
```

Local config not in repo (provide per laptop):
- `local.properties` — Android SDK path.
- `BuildConfig.SUPABASE_URL` / `SUPABASE_ANON_KEY` / `GOOGLE_WEB_CLIENT_ID` (via Gradle properties or env).
