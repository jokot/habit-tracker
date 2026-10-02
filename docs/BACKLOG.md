# Backlog

State as of 2026-10-02. The phase table in
`docs/superpowers/specs/2026-04-20-habit-tracker-design.md` §12 is stale — it lists
seven phases, and twelve have shipped. This file is the live one.

## Shipped

Phases 1–12 and four follow-up fixes, all merged to `main` (head `7db782c`):

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
| 11 | You hub + settings redesign (PR #25) |
| 12 | Notifications settings + permission prompt (PR #26) |

Two items previously carried as open notes are **done**: `habit.effectiveFrom` is
implemented across the domain model, schema, streak use cases and sync; the onboarding
redesign cleanup landed (a single `OnboardingScreen.kt` remains).

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

## Fixes after Phase 12 (shipped)

| PR | Merged | What |
|---|---|---|
| #27 | 2026-08-10 | Widgets no longer go empty after a cold start. `LastAuthUserStore` keeps the last signed-in user id, so widgets, workers and `WantTimerService` read the correct rows before the session loads. Also: streak grid fills its frame, widgets default to 2×2, quick-log tiles fit their cells. |
| #28 | 2026-08-11 | Timed wants start their timer from Today. A tap opens the "How long?" sheet on Home. A tap on a running want shows a snackbar with the time left. The predicate moved to `WantActivity.isTimed`. |
| #29 | 2026-08-11 | The pending card of a timed want says `Starts in 3s` and `−1 pt at start`. Before, it said `Spends in 3s` with a total for each tap. |

## Known bugs

1. **Sync pull has no pagination** — GitHub issue #20. A first sync of an account with
   more than 1000 rows in one table loses the oldest rows. **In review: PR #30**
   (`fix/sync-pagination`). The PR adds `fetchAllPages` for all six pull queries.
   Before merge, do one sync with a signed-in account and `SYNC_PAGE_SIZE` set to 2.
   This check confirms that Postgrest accepts the `range` and the two `order` clauses.

## Open work, not started

1. **iOS** — SwiftUI screens + WidgetKit extensions over the same shared KMP module.
   Largest remaining spec item; deserves its own spec → plan → phase cycle.
2. **Sync pull latency** — items 2 and 3 of issue #20, not part of PR #30. Run the six
   pulls in parallel. Move the pull off the auth screen.
3. **Overlay enforcement** (`SYSTEM_ALERT_WINDOW`) — spec backlog, marked post-iOS.
4. **Device QA leftovers** — not done on a device yet:
   - Phase 10: quick-log grid scrolling past the visible rows, and tap latency with all
     seven widgets pinned at once. Checklist in
     `docs/qa/2026-08-08-phase10-widgets-v2-qa.md`.
   - PR #27: a cold start in airplane mode while signed in. Widgets must show the data
     of the user. This test is the one that proves the `LastAuthUserStore` fix.
