# Backlog

State as of 2026-08-10. The phase table in
`docs/superpowers/specs/2026-04-20-habit-tracker-design.md` §12 is stale — it lists
seven phases, ten have shipped. This file is the live one.

## Shipped

Phases 1–10, all merged to `main` (head `3b9845b`):

| Phase | What |
|---|---|
| 1 | Supabase schema + RLS, KMP skeleton, SQLDelight, Ktor, theme, soft-delete log model |
| 2 | Core loop: onboarding, identity→habit setup, log need/want, balance, guest mode, auth |
| 3 | Sync + auth hardening, Google OAuth, cross-device restore |
| 4 | Streaks, per-habit progress, notifications |
| 5a–5e | Design system, multi-identity, identity CRUD, onboarding redesign, habit browse/form/delete |
| 6 | Exchange rate algorithm |
| 7 | Want CRUD, units-per-point pivot |
| 8 | Onboarding seeds |
| 9 / 9.1 | Notifications + want timer, timer UX redesign |
| 10 | Four Glance home-screen widgets (balance, quick-log list, quick-log grid, streak) |

Two items previously carried as open notes are **done**: `habit.effectiveFrom` is
implemented across the domain model, schema, streak use cases and sync; the onboarding
redesign cleanup landed (a single `OnboardingScreen.kt` remains).

## Phase 12 — Notifications settings + permission prompt (shipped, PR #26)

Merged 2026-08-10. Same design canvas, file `notifications.html` /
`components/notifications.jsx`.

Landed: per-type labels and icons (`ui/settings/NotificationTypeUi.kt`) replacing the
raw-enum-key rows; category group cards via `SettingsGroup(prominent, dimmed)`; blocked
and paused banners; `rememberNotificationPermissionGranted()` fixing the stale banner
after a trip to system settings; permission prompt redrawn as a `ModalBottomSheet` and
moved off onboarding step 1 onto the Home route; pinned top bars on both settings
screens.

Also shipped in the same PR: want timers no longer post an ongoing notification when
timer notifications are off — the foreground service is skipped and
`WantTimerFinalizeWorker` finishes the timer instead.

Out of scope by decision: posted notification copy in the workers,
`bar_raised`/`bar_dropped` (feature never built), per-category master switches, iOS.

## Known bugs

1. **No way to start a timer for a timed want from Home** — `HomeViewModel.tapWant`
   (`HomeViewModel.kt:349`) always runs the pending-count/undo path, whatever the want
   is. The timer is reachable only via long-press → want detail, or from the quick-log
   grid widget, which does open the duration sheet for timed wants. Home should match.

## Phase 11 — You hub + settings redesign (shipped, PR #25)

Merged 2026-08-10. You hub and Settings moved off bare M3 `ListItem`s onto the shared
`SettingsGroup` card + `SettingsRow` primitives; `SettingsViewModel.summarize` now
reports `"All on"` / `"N of M on"` / `"Off"` (the old `"All on · N paused"` contradicted
itself on a fresh install, where `DAILY_REMINDER_PER_IDENTITY` defaults off).

Design source for both this phase and Phase 12: project
`019dd32e-8a8d-7707-b080-fc31a631b693`, read through the `claude_design` MCP
(`DesignSync`), auth via `/design-login`. Large files come back as content-hash
references with a 1800s TTL — if one expires mid-session it stays dead on re-fetch, so
pull design files early or from a fresh session.

## Open work, not started

1. **iOS** — SwiftUI screens + WidgetKit extensions over the same shared KMP module.
   Largest remaining spec item; deserves its own spec → plan → phase cycle.
2. **Sync pull has no pagination** — GitHub issue #20. A first sync of an account with
   more than 1000 logs silently truncates the oldest. Real data loss, bounded fix.
   Deferred pending a sync-focused phase or a user hitting it.
3. **Overlay enforcement** (`SYSTEM_ALERT_WINDOW`) — spec backlog, marked post-iOS.
4. **Phase 10 QA leftovers** — never run on a device: quick-log grid scrolling past the
   visible rows, and tap latency with all seven widgets pinned at once. Checklist under
   `docs/qa/`.
