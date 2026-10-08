# Backlog

State as of 2026-10-09. The phase table in
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
| #32 | 2026-10-03 | Fixes issue #31. The six pull fetches run in parallel. `RealPostgrestSyncTest` tests the paging against a local Supabase. |
| #30 | 2026-10-03 | Fixes items 1 and 3 of issue #20. All six pulls use keyset paging, so a first sync no longer loses rows past the 1000-row cap. Sign-in opens Home at once, and each Today section shows a shimmer until its data is local. Streak History updates live and computes off the main thread. A sync at `SYNC_PAGE_SIZE` 2 against the real server pulled all 348 rows correctly. |

## Sync bugs from the trace of 2026-10-03 (all fixed)

Found on 2026-10-03 while tracing the sync flow for `docs/sync-flow.md`. Each bug has
an issue, #33 to #37. Most severe first:

1. **#33 A session expiry can delete unsynced rows.** Fixed in PR #38.
   When the token refresh fails and unsynced changes exist, the app now keeps the rows.
   The next sign-in with the same account pushes them. See `docs/sync-flow.md` §6.
2. **#34 A widget log does not start a sync.** Fixed in PR #40. A signed-in
   widget log queues a `WIDGET_WRITE` job. A background process loads the session from
   storage, and counts the remembered user as signed in. See `docs/sync-flow.md` §2 and §6.
3. **#35 The push stamps `synced_at` with the phone clock.** Fixed in PR #41.
   Server triggers set `synced_at` and `updated_at`, and a pull asks again for the last 5 s
   before the watermark. See `docs/sync-flow.md` §4.
4. **#36 The background sync jobs have no network constraint.** Fixed in PR #40. Every job except `MANUAL` waits for `NetworkType.CONNECTED`. A
   job that starts on a reconnect waits for the token refresh. See `docs/sync-flow.md` §2.
5. **#37 The push sends one request per row, in series.** Fixed in PR #41. The
   push sends 500 rows per request. On a phone, 222 logs took 1 request and 7 s.

## Plan to the first release

Decided on 2026-10-09. No user except the developer uses the app yet, so the next goal is
real use, not more features. Each feature below that waits for a signal waits for this
release.

1. **One PR** with the 3 items of "Before release: fix".
2. **The 5 items** of "Before release: check".
3. **A Play Store closed test** for 5 to 20 testers, with crash reporting and 3 events:
   a logged habit, a spent want, and a return on day 7.
4. **2 to 4 weeks of use.** Then choose between the features in "Wait for a signal".

iOS waits until the Android test shows that users come back. iOS adds a second UI and a
second widget system to each later change.

## Before release: fix

1. **A sync that the phone stops shows as a failure.** In the background, ColorOS (OPPO)
   and similar systems stop a sync job after about 5 s. `runCatching` in `SyncEngine.sync`
   (`SyncEngine.kt:80`) also catches the `CancellationException`. The engine then sets
   "Sync failed", and `SyncFailureCounter` counts 1 failure. After 3 stops in a row, the
   user gets "Sync has been failing — check your connection", but no sync failed. A
   stopped sync must not set `Error`, and must not count. Seen on an OPPO CPH2737 in the
   QA of PR #41: `sync(POST_LOG) failed — JobCancellationException`.
2. **No space above the identity strip when the timer banner shows.** Found in the QA of
   PR #41. `HomeTimerBanner` (`HomeScreen.kt`) touches the "I AM" row below it. The strip
   has no top padding, so the banner needs a gap below it. Check the rate-ladder banner
   too, because it is the next item in the same list.
3. **"Coming soon" dead ends in Add Identity.** See deferred features 1 to 3:
   - Build the custom habit in step 2. The habit form exists.
   - Remove the "Custom" identity tile from step 1.
   - Remove the search field from step 1. It filters nothing, and step 1 shows only 13
     identities.

## Before release: check

1. **Apply the migration `20261006000000_server_sync_timestamps` to prod** with
   `supabase db push`. Without it, prod keeps the phone time, and #35 stays open on prod.
2. **Test Google sign-in on prod.** The local Supabase has no Google provider, so no QA
   since PR #38 tested it.
3. **Find out if prod requires email confirmation.** Look in the Supabase dashboard,
   under Authentication. If it does, a user who misses the email cannot sign up, and
   deferred feature 10 (resend) moves to "Before release: fix".
4. **Run the device QA leftovers:**
   - Phase 10: quick-log grid scrolling past the visible rows, and tap latency with all
     seven widgets pinned at once. Checklist in
     `docs/qa/2026-08-08-phase10-widgets-v2-qa.md`.
   - PR #27: a cold start in airplane mode while signed in. Widgets must show the data
     of the user. This test is the one that proves the `LastAuthUserStore` fix.
5. **Build the release against prod.** The worktrees `fix-session-expiry-push`,
   `fix-sync-triggers` and `fix-batch-push` point `local.properties` at the local
   Supabase, and they have a debug manifest for clear-text HTTP. Each keeps the prod file
   as `local.properties.prod`.

## Known limits, not fixed

Decided on 2026-10-09: these do not block the release. Fix one when a tester reports it.

1. **A running want timer shows only on the device that started it.** Found in the QA of
   PR #41. Sign in as the same user on device A and device B. Start a timed want on device
   A. Device B shows no timer, and the balance of device B does not show the points that
   the timer uses.
   - `LocalWantTimer` is a local SQLite table. No Supabase table and no sync code exist
     for it.
   - The want log arrives on the server only when the timer ends. Until then, device B
     can spend the same points again.
   - A fix needs a product decision: show the timer on each device, or only lock the
     points. It also needs a server table, RLS and a sync path for the timer rows.
   - Why it waits: a double spend needs 2 devices, a running timer and a spend in that
     time. Put it in the notes for the testers.
2. **The count in the session-expiry notice includes unsynced seeded wants.** One check
   showed 17 changes for 3 logs (PR #38). The number confuses, but no data is lost.
3. **No toast when a session ends in a background process,** for example in a widget
   update. The Auth notice still shows the held changes (PR #38).
4. **Each sync pulls the newest batch again,** about 60 KB for 222 logs. The 5 s overlap
   of the watermark causes it (PR #41).
5. **A new log can wait for the backoff of an older job.** With `KEEP`, a new log joins a
   job that ColorOS stopped, and that job waits for its backoff. A visit to the app starts
   a sync anyway (PR #40).
6. **Last push wins.** An edit made offline and pushed later replaces a newer edit from
   another device (PR #41).
7. **A large push can stop in the background on ColorOS.** Batches of 500 fit in the 5 s
   for a normal push. A visit to the app finishes a larger one (PR #41).
8. **The widget misses taps faster than about 4 per second.** A script that tapped 14
   times per second saved 61 of 200 taps. A person does not tap that fast.
9. **Dead code: the sign-out dialog of Today.** `HomeViewModel.beginSignOut()` and its
   dialog have no caller. Settings has the only sign-out. Remove them when `HomeViewModel`
   changes next.

## Open work, not started

1. **iOS** — SwiftUI screens + WidgetKit extensions over the same shared KMP module.
   Waits for the result of the Android test. See "Plan to the first release".
2. **Issue #39: Today links and logging from the detail screens.** The Today habit and
   want sections get a link to their list screens. Habit detail and want detail get a log
   action. Needs a product decision on the link label and on the log action first.

## Deferred features: decisions

The specs deferred these features, and no later phase built them. Checked against `main`
at `831b68d` on 2026-10-09. Each item names the spec that deferred it. The decisions are
from 2026-10-09, and they assume that no user except the developer uses the app yet.

### Build before release

1. **Custom habit inside Add Identity.** The "+ Define a custom habit" button in step 2
   shows a "Coming soon" toast (`AddIdentityStep2Screen.kt:191`). The habit form exists,
   so this is mostly navigation. A dead end in the first flow of a new user costs the most.
   Specs: Phase 5c-2 and 5e-3.
2. **Resend the confirmation email,** only if prod requires email confirmation (see
   "Before release: check" item 3). Sign-up shows a "check your email" state
   (`AuthScreen.kt:161`), but no resend action. Spec: Phase 3.

### Remove the UI, do not build the feature

3. **Custom identity.** The user picks a name, an icon and a colour. The "Custom" tile in
   step 1 shows a "Coming soon" toast (`AddIdentityStep1Screen.kt:191`). `Identity` has no
   owner field, and the `identities` table holds only the 13 global seed rows. So the
   feature needs a schema change, RLS, sync for rows that a user owns, and an icon and
   colour picker. Custom habits already cover personal goals. Build it when users ask for
   an identity that the 13 do not cover. Spec: Phase 5c-2.
4. **Search in Add Identity step 1.** The field is a placeholder with no filter
   (`AddIdentityStep1Screen.kt:134`). A search over 13 items adds nothing, and a field
   that does nothing looks like a bug. Spec: Phase 5c-2.

### Wait for a signal from the testers

5. **Habit reorder.** The design shows a drag handle. Habits have no order field, and the
   list sorts by name. Signal: users with many habits ask for a different order. The test
   account has 20 habits, so this signal can come early. Specs: Phase 5e-1 and 5e-3.
6. **Freeze stock.** The user earns and spends freezes. Today a frozen day is automatic
   ("never miss twice"), with no count and no screen. Signal: users stop after a broken
   streak. It changes the core rule, so it needs data. Specs: Phase 4 and 5a.
7. **Automatic daily targets.** An engine raises or lowers a daily target from the
   history, with the notifications `bar_raised` and `bar_dropped`. Nothing changes
   `dailyTarget` except the habit form. Signal: users stay at the same target for weeks,
   or stop because a target is too hard. A wrong guess annoys users. Specs: Phase 9
   and 9.1.
8. **Android overlay enforcement** (`SYSTEM_ALERT_WINDOW`). The app warns or blocks while
   the user spends time on a want. The manifest has no such permission. It is the only
   feature that makes the point cost real instead of an honour system. The master spec
   (§12) puts it after iOS. Signal: users log fewer wants than they use. If this signal
   comes, build it before iOS.
9. **The timer on other devices.** See "Known limits, not fixed" item 1. Signal: a tester
   uses 2 devices.

### Do not build

These do not change the reason to use the app. Do not plan them again without a request
from a user:

10. **Theme picker.** The app follows the system theme (`isSystemInDarkTheme()`). Spec:
    Phase 4.
11. **Data export and backup.** The cloud sync keeps the data. Plan it again only if a
    user or the Play Store requires it. Spec: Phase 4.
12. **Past-spend rate text,** for example "This was logged at the 1.2× rate". Spec:
    Phase 6.
13. **Per-habit streak on the Today cards.** Habit detail shows it. On 20 cards, it fills
    the screen that users open each day. Spec: Phase 5a.
14. **Per-habit streak widget.** The streak widget shows the overall streak. Spec: master
    spec §3.2.
15. **Habit icon picker.** The icon comes from the name (`habitIcon(name)`), and `Habit`
    has no icon field. Spec: Phase 5e-3.
16. **Habit list grouped by identity.** The habit list is one flat list. Spec: Phase 5e-2.
17. **Past identities,** a collapsed section on the Identities list (canvas line 2220).
    Specs: Phase 5c-2 and 5e-3.
18. **Periodic and live sync,** a periodic WorkManager sync or Supabase Realtime. Since
    PR #40, sync runs on each trigger and on each reconnect. Spec: Phase 3.
19. **More timer actions:** pause and resume, more than one timer at a time, and snooze.
    `WantTimerState` has only RUNNING, FINISHED and CANCELLED. A pause makes the timer
    easy to cheat. Specs: Phase 9 and 9.1.
20. **More dev tools:** force sync, clock offset, trigger a session expiry, wipe data.
    Add each one when a debug session needs it. Spec: Dev tools.

### After iOS

21. **Apple Sign-In.** Tied to the iOS work. Spec: Phase 3.

### Deferred, and built later

These items appear in the deferred lists of the specs, but a later phase built them. Do
not plan them again:

- Milestone, timer-end and tier-up notifications: `MILESTONE_STREAK`, `WANT_TIMER_END`
  and `TIER_ADVANCED` in `NotificationTypeId.kt`.
- Habit and want CRUD, the want timer, the identity screens, the widgets and the
  exchange-rate screen.
- Custom habits outside Add Identity: `HabitFormScreen.kt`.
- The edit action on habit detail: `HabitDetailScreen.kt:71`.
- Identity stats that use the link dates of each day: `ComputeIdentityStatsUseCase.kt:117`.
- Removal of the old notification switches from Settings.
- The per-habit streak number: `ComputePerHabitStreakUseCase`, on habit detail.
