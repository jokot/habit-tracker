# Claude Design follow-up — Notifications system redesign

## Action for Claude Design

**Remove all existing notification artboards from the canvas.** Replace them with a single, standardized notification system covering every type listed below. Each type gets its own row in the artboard with a unique icon, contextual title, body copy, and deep-link target. Do not preserve the old single-icon "Habitto" framing — start clean.

After removal + redesign, the canvas should contain:

- One **`NotificationCatalog`** artboard listing every notification type as a Material 3 notification card preview (light + dark variants).
- One **`NotificationsSettings`** artboard showing the user-facing toggle list, grouped by category, with per-notification icon previews.
- One **`NotificationPermissionPrompt`** artboard for the system permission request flow.

## Design principles

1. **Unique icon per notification type.** No shared `ic_notification`. Each type has a distinct Material Icons glyph (filled when active, outlined when muted) so users can identify the source from the lock screen / notification shade without reading.
2. **Contextual title per notification.** Replace the generic "Habitto" title with a type-specific title (e.g., "Log your habits", "Streak at risk", "Streak reset"). Keep "Habitto" only as the app-name label that Android renders separately.
3. **Standardized body voice.** Second person, present tense, ≤80 chars. Action-oriented when actionable; informative when status-only. Avoid exclamation marks except in milestone celebrations.
4. **Tap-target deep-link.** Every notification specifies which screen tapping opens.
5. **Category mapping.** Each type maps to one of three categories — **Reminder**, **Alert**, **Status** — visualized with a colored side accent on the notification card.

## Notification types

Mock all of these as cards in `NotificationCatalog`. Use the table as the spec; designer fills in the icon glyph and visual treatment.

### Category: Reminder (low urgency, scheduled, default-importance channel)

| ID | Title | Body | Icon glyph | Deep-link |
|---|---|---|---|---|
| `daily_reminder` | "Log today's habits" | "Tap to keep your streak alive." | `notifications_active` (suggested) | Today screen |
| `daily_reminder_per_identity` | "{Identity} hasn't shown up today" | "Log a {identity} habit when you have a moment." | `account_circle` | Today screen, identity strip pinned to that identity |

### Category: Alert (high urgency, late-day or time-critical, high-importance channel)

| ID | Title | Body | Icon glyph | Deep-link |
|---|---|---|---|---|
| `streak_risk` | "Your {N}-day streak is at risk" | "Log a habit before midnight to keep it alive." | `warning_amber` | Today screen |
| `want_timer_end` | "{Want} timer finished" | "{N} {unit} logged · {cost} pt spent." | `timer_off` | Want detail for that activity |

### Category: Status (low urgency, after-the-fact info, low-importance channel)

| ID | Title | Body | Icon glyph | Deep-link |
|---|---|---|---|---|
| `streak_frozen` | "Streak frozen — don't miss today" | "Yesterday was an off day. One more miss resets you." | `ac_unit` | Streak history |
| `streak_reset` | "Streak reset" | "Start a new one today. Yesterday is yesterday." | `restart_alt` | Today screen |
| `tier_advanced` | "Tier {N} unlocked — {rate}× spending" | "Wants now cost {rate}× more. Earn the same; spend less." | `trending_up` | Exchange rate screen |
| `bar_raised` | "You're crushing {Habit}" | "Daily target raised to {newTarget} {unit}." | `trending_up` | Habit detail |
| `bar_dropped` | "Rough week on {Habit}" | "Target lowered to {newTarget} {unit}. Get back to it." | `trending_down` | Habit detail |
| `milestone_streak` | "{N}-day streak" | "Real progress. Keep going." | `local_fire_department` | Streak history |

(Designer: vary glyph for each milestone tier — 7/14/30/100 — if visual differentiation feels valuable. Otherwise share `local_fire_department` and use the dynamic title to differentiate.)

### Category: System (sign-in, account, sync — informational; minimum importance)

| ID | Title | Body | Icon glyph | Deep-link |
|---|---|---|---|---|
| `session_expired` | "Sign in again to sync" | "Your session timed out. Tap to sign back in." | `lock` | Auth screen |
| `cloud_restore_complete` | "Backup restored" | "{N} habits and {M} logs synced from cloud." | `cloud_done` | Today screen |
| `sync_failed_persistent` | "Sync paused" | "Couldn't reach cloud after multiple tries. Tap to retry." | `cloud_off` | Settings → sync section |

## `NotificationsSettings` requirements

User-facing toggle list, grouped by Category. Each row shows:
- Per-notification icon (matching the catalog).
- Title.
- Tap target — toggle on/off.
- For Reminder + Alert categories that have configurable times: trailing time picker.

Add a master toggle at top — disabling kills all categories; per-category sub-master also allowed.

Group order: Reminder → Alert → Status → System.

Empty/permission states:
- If the OS permission isn't granted, show a banner at top: "Notifications are blocked — turn on in system settings to receive these." with a button that opens the Android app-info notification page.
- If permission granted but master is off, show a softer banner: "Notifications are paused — turn on master switch to enable."

## `NotificationPermissionPrompt` requirements

Shown once after onboarding completes (or first-launch for upgrade users). Sheet style:
- Title: "Get the most out of Habitto"
- Body: 3 short bullet rows, one per top-tier notification benefit (with the same icons as in the catalog):
  - `notifications_active` — "Daily nudge so you don't forget"
  - `warning_amber` — "Late-day rescue when streak is at risk"
  - `local_fire_department` — "Milestones when you hit 7, 14, 30, 100"
- Primary button: "Allow notifications" → triggers OS prompt.
- Secondary button: "Maybe later" → dismisses; persistable so it doesn't keep nagging.

## Visual standards

- Card body: standard Material 3 notification layout (small icon left, title bold, body line, app-name footer).
- Side accent stripe: 4px wide, full height, color-coded by category.
  - Reminder: `--m-primary`
  - Alert: `--m-error`
  - Status: `--m-on-surface-var`
  - System: `--m-tertiary`
- Action area: large icon (24dp) placeholder for the OS-rendered app glyph plus the per-notification icon (16dp inline before the title).

## Open question for designer

Should the System category share a single channel with the rest (sub-grouped in settings) or get its own Android channel? Mock the Settings UX both ways — engineering will pick the channel topology after seeing both.

## Out of scope

- Animation / motion of the notification arriving in-app (Android handles).
- Smart notification grouping (Android auto-bundles same channel — we accept defaults).
- Localization variants (single-language for first design pass).
- Push notification infrastructure (these are all local OS notifications scheduled via WorkManager-style background jobs).
