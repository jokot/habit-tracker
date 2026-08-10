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

## Phase 11 — You hub + settings redesign (in progress, blocked)

Branch `feature/phase11-you-settings-redesign`, worktree
`.worktrees/phase11-you-settings-redesign`. Nothing implemented yet.

**Goal:** redesign the You hub and the settings screens to match the Claude Design
canvas. Behavior and ViewModels stay; only the UI layer changes.

**Design source:** project `019dd32e-8a8d-7707-b080-fc31a631b693`
(<https://claude.ai/design/p/019dd32e-8a8d-7707-b080-fc31a631b693?file=you-hub.html>),
read through the `claude_design` MCP (`DesignSync`), auth via `/design-login`.

Files to read: `you-hub.html`, `components/you-hub.jsx`, `shared.jsx`, `tokens.css`.
`frames/design-canvas.jsx` is canvas chrome only (pan/zoom, artboard drag) — skip it.

**Blocker (2026-08-10):** `you-hub.html` and `components/you-hub.jsx` return an expired
cache reference — `Entry not found (CCR TTL: 1800 seconds)`. Large MCP results are
swapped for a content-hash reference; those two aged out mid-session, and re-fetching
returns the same dead reference instead of re-storing. Not fixable in-session.
**Fetch them from a fresh session**, where they come back clean, or work from
screenshots. `tokens.css` and `frames/design-canvas.jsx` fetched fine as new content.

**What exists today:**

| File | Lines |
|---|---|
| `ui/you/YouHubScreen.kt` | 186 |
| `ui/you/YouHubViewModel.kt` | 54 |
| `ui/settings/SettingsScreen.kt` | 415 |
| `ui/settings/SettingsViewModel.kt` | 98 |
| `ui/settings/NotificationsSettingsScreen.kt` | 156 |
| `ui/settings/NotificationsSettingsViewModel.kt` | 42 |

`YouHubScreen` structure: `IdentityHubCard`, then sections `Tracking` (Habits),
`Earn & spend` (point exchange rate, shown as `Nx · earned by N-day streak`, and Wants),
`Account` (email + sign out, or a sign-in row when unauthenticated), `App` (Settings).
Every row is a bare M3 `ListItem` — no card, elevation or colour treatment. That
flatness is what the redesign is meant to fix.

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
