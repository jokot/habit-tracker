# Habitto — Product & UX Design Brief

You are designing the complete UI/UX for **Habitto**, a habit-tracking mobile app for Android (Compose Material 3, KMP shared module — iOS later). Pick the design system yourself: colors, typography, spacing, components, motion, iconography, layout patterns. The brief below tells you what the product is, what features exist, and how each feature behaves. You decide how it looks and feels.

This brief is self-contained — no external files referenced.

---

## 1. Product Identity

**Name:** habitto (lowercase as the display label)
**Tagline:** "Become someone, by doing something."
**Audience:** consumers, 18–45, mixed technical literacy.
**Tone:** calm, motivating, non-gamified-feeling. Adult, not childish. Productivity-app warm — not enterprise sterile, not kids-app playful.

**Core thesis:** identity-driven habit tracking.
1. User picks an **identity** (e.g. "Healthy person", "Reader") — proof of who they want to become.
2. App recommends **habits** that support that identity (e.g. Healthy → Sleep early, Drink water, Exercise).
3. Logging habits **earns points**.
4. **Wants** = low-value behaviors (social media, gaming, snacks). Each costs points to do.
5. **Streak** rewards consistency. One missed day is forgiven. Two consecutive misses break it.
6. **Auth is optional** — the app is fully usable without sign-in. Sign-in adds cross-device sync.

---

## 2. Point System

**Earned points per habit per day** = `floor(quantity / thresholdPerPoint)`, capped at the habit's `dailyTarget`. Logging past the target is recorded but contributes 0 points (so the bar isn't infinitely gameable).

**Spent points per want** = `ceil(quantity * costPerUnit)`, minimum 1 per session.

**Balance** = `max(0, earned − spent)`. Cannot go negative — wants whose cost would overdraw are blocked with a "Not enough points" message.

**Daily earn cap (per user)** = sum of all habits' `dailyTarget`. **Rollover cap** = `dailyEarnCap × 2` — leftover balance carries into next day, clipped at this ceiling at midnight.

**Week reset:** every Monday 00:00 (local time). All carry-over wiped.

**UX implication you must design for:**
- Tap-to-commit pattern: tapping a habit/want bumps a count badge (`×N`) with a 3-second countdown + Cancel before the log writes. Visualize the pending state clearly. After 3 seconds the log commits.
- Insufficient-points feedback when a want tap would overdraw the balance.

---

## 3. Streak System

**Rule:** logging at least one habit per day keeps the streak alive.

**Day states:**

| State | Meaning |
|---|---|
| Active | User logged ≥ 1 habit. Sub-intensity: how many distinct habits got logged that day. |
| Frozen | Missed day, but isolated — streak survives (one-miss forgiveness). |
| Broken | Second consecutive miss — streak resets on this day. |
| Today pending | Today, no log yet. |
| Future | After today (only visible in calendar views). |
| Empty | Before user's first log. |

**Heatmap:** active days are rendered as a 5-level intensity ramp (GitHub contribution-graph style). Heat metric = `distinctHabitsLogged / activeHabitCount` per day, bucketed into 5 levels (level 0 = no logs; levels 1–4 split the active range).

**Status overrides:** Frozen and Broken use distinct status colors (not on the heat ramp). Today-pending uses level-0 fill plus a visible ring. Future uses level-0 fill at reduced visual emphasis.

**Streak number** = consecutive Active days ending at today (or yesterday if today is pending), anchored at the last Broken day or the user's first-ever-log date. Frozen days do NOT add to the count, but do NOT reset it either.

**Anchor:** date of the user's first log (NOT signup). Days before the anchor render as Empty. Auth-optional means streak data exists for guests too.

---

## 4. Want Activities

Wants are negative behaviors with associated point costs. App ships a pre-seeded list; user picks a subset during onboarding plus may add custom ones. Sample seeded list with example `costPerUnit`:

- Twitter / X — 0.5 pt/min
- Instagram — 0.5 pt/min
- TikTok — 1.0 pt/min
- YouTube — 0.1 pt/min
- Reddit — 0.5 pt/min
- Mobile gaming — 1.0 pt/match
- Snacks (junk food) — 5 pt/serving
- Smoking — 10 pt/cig

UI must show cost-per-unit clearly so the user understands the spend before tapping.

**Want logging:** tap a want activity row → 3-second `×N` countdown → on commit, writes N separate logs of `quantity = 1` each (1 pt minimum each via `ceil`). Every tap costs at least 1 pt. Pre-tap balance guard rejects overdraw before the countdown starts.

---

## 5. Sync + Auth

- **Local SQLite is source of truth.** Cloud is backup + cross-device sync. App is fully usable offline and without sign-in.
- **Guest user** has a generated local user ID. Logging works immediately, no account needed.
- **Sign-in flow:** email/password or Google ID-token. After sign-in, local guest data migrates to the authenticated user, then push-pulls cloud rows.
- **Sync states (visible somewhere prominent in the main UI):** Idle / Running / Synced / Error. Errors are tappable to retry.
- **Pull-to-refresh on Home:** triggers manual sync. Visual indicator should appear ONLY for manual sync, not for automatic background sync (which fires after every log and on app foreground).
- **Sign-out flow:** confirm dialog showing count of unsynced rows → push-then-clear local DB → return to guest. Dialog has a processing state during sign-out.

**Friendly error labels** (categorized from exception type, not raw stack traces):
- "Session expired"
- "Network timeout"
- "No network"
- "Sync rejected by server"
- "Sync failed"

---

## 6. Notifications

App fires system notifications via background work. Five types:

1. **Daily reminder** — fires at user-chosen time (default 9:00 AM) if no habit logged yet today.
2. **Streak at risk** — fires at user-chosen time (default 9:00 PM) if streak is active and nothing logged today.
3. **Streak frozen** — fires the day after a missed day where the streak survived.
4. **Streak reset** — fires the day after a streak broke (two consecutive misses).
5. **Want-timer end** — fires when user finishes a tracked want session, with cost summary.

**Permission flow:** Android 13+ requires runtime POST_NOTIFICATIONS. App must guide the user through granting it; if denied, show in-context help to open system settings and re-grant.

**Settings:**
- Master toggle "All notifications" — overrides individual switches when off.
- Permission banner only when notifications are blocked at the system level.
- Per-type switches (5 rows). Time pickers for daily reminder + streak at risk only (the others are event-driven).

---

## 7. Screens to Design

For each screen below: provide **mockups** in light + dark mode, **layout spec** (component anatomy with concrete values for whatever design system you choose), **interaction notes**, **states** (empty / loading / error / offline / permission-denied / success), and **accessibility annotations** (focus order, content descriptions, dynamic-type behavior).

### 7.1 Onboarding (4 steps)
- Step 1 — Identity picker: list/grid of identity options (name, description, optional icon)
- Step 2 — Habits picker: recommended habits for the chosen identity, multi-select with `dailyTarget` and `thresholdPerPoint` shown
- Step 3 — Wants picker: multi-select from the seeded want activities, show `costPerUnit`
- Step 4 — Sign-in CTA / "I'll do it later" (auth optional)
- Progress indication; Back / Next controls; Next disabled until selection is valid

### 7.2 Auth screens
- Sign up
- Sign in
- Google sign-in
- "Check your email" confirmation-pending state
- Email + password fields with visible labels, password show/hide toggle, autofill support
- Error display below fields

### 7.3 Home — main screen
- Top bar: app title, sync status indicator, settings entry point
- **Daily status hero** combining:
  - Current streak count + flame motif
  - 7-day streak heatmap
  - Today's earned / spent / balance numbers
  - Link to streak history
- **Today's habits** section: list of habit cards with name, optional icon, progress against daily target, tap to log (3-second pending commit pattern). Inline per-habit streak indicator.
- Pending log state: count badge `×N` with countdown + Cancel
- **Wants** section: list of want activity rows with name + cost-per-unit, tap to log (same 3-second pending pattern)
- Empty state if user has no habits
- Pull-to-refresh wrapper (only triggers indicator on manual sync)

### 7.4 Settings
- Top bar: back, "Settings"
- Permission banner (compact, only when notifications denied at system level): tappable to open system settings
- **Notifications** section:
  - Master "All notifications" toggle
  - Daily reminder (toggle + time picker)
  - Streak at risk (toggle + time picker)
  - Streak frozen alerts (toggle only)
  - Streak reset alerts (toggle only)
  - Want-timer end (toggle only)
- **Account** section:
  - Email row (only when signed in + email exists)
  - Sign out (destructive, opens confirm dialog)
  - OR Sign in CTA (when guest)
- **About** section:
  - Version
  - Privacy policy
- **Logout dialog:** confirm with unsynced row count + override option. Processing state with progress feedback. Cancel hidden during processing.

### 7.5 Streak history
- Top bar: back, "Streak History"
- Summary card: Current / Longest / Total days
- Scrollable monthly calendar list (newest first), lazy-loading older months on reaching the end
- Each month: month label + 7-column weekday-aligned heatmap grid
- Per-month loading + error states
- Stop condition when reaching the user's first-log date
- Tap a day cell → bottom sheet showing date, habits logged that day with counts, points earned/spent, streak state. Read-only.

### 7.6 Habit detail
- Reachable by tapping a habit card on Home
- Shows: habit name + icon, current per-habit streak number, mini heatmap (7 or 30 days), aggregate stats (total logs, longest streak, total points earned)
- Edit / delete affordances

### 7.7 Habit management (CRUD)
- List of all user habits with edit/delete affordance + "Add habit" CTA
- Habit form: name, identity assignment, threshold-per-point, daily-target, optional icon
- Confirm-delete dialog (warns existing logs are kept but the habit is hidden from the active list)
- Empty state when no habits exist

### 7.8 Want activity management (CRUD)
- Same pattern as habit management
- Custom-vs-seeded distinction: seeded want activities can't be deleted, only hidden
- Form: name, unit (e.g. "min" / "match" / "serving"), `costPerUnit`

### 7.9 Streak freezes (earn + spend)
- Visible inventory somewhere persistent: "{X} freezes available"
- Earn flow: streak-milestone unlocks (e.g. every 7 consecutive days) — design a brief celebration moment
- Spend flow: when the streak would otherwise reset (Broken today), prompt "Use a freeze to save your streak?" with available count + visual showing what's saved
- Freeze history: log of when each freeze was earned + spent

### 7.10 Want-timer
- Full-screen timer view with cost ticking up live, prominent Stop control
- Ongoing notification while active (with Stop action)
- Final summary notification when stopped: "You spent {N} points on {activity}."

### 7.11 Home-screen widgets (Android)

Concept sketch only — single frame each, no full state matrix.

- Widget 1: Point balance + quick log — current balance + today's incomplete habits as tap targets
- Widget 2: 30-day streak grid — heatmap mini + current streak count, tap → opens streak history

### 7.12 Exchange-rate UI

Concept sketch only.

Visualizes a "rate increase" reward — costs of wants increase over time as the user maintains their habit consistency. Conceptually a comparison view (timeline / curve / bar) showing how the user's effective spending power has compounded.

---

## 8. States to Address Per Screen

For every screen include:
- **Empty** — no data, helpful CTA
- **Loading** — skeleton or spinner
- **Error** — inline or full-screen, with retry
- **Offline** — banner or chip indication
- **Permission-denied** — guidance to fix
- **Success / done** — confirmation feedback
- **Dark mode** parity for everything

---

## 9. Microcopy Tone

Calm, direct, respectful. Not preachy, not gamified.

✅ Good:
- "Log your habits to keep your streak alive."
- "{N}-day streak at risk. Log a habit before midnight."
- "Missed yesterday. Don't miss today, or your streak resets."
- "Streak reset. Start fresh today."
- "Local data on this device will be cleared. Cloud data stays."
- "Notifications blocked. Tap to open system settings."

❌ Avoid:
- "Wow! You're crushing it!"
- "Don't break your streak!! 🔥🔥"
- "Failure is not an option."
- Anything with shame, mascot voice, or aggressive caps.

Generally: present-tense, second-person, observational. Never shame. Never gamify with mascot energy.

---

## 10. Deliverables

Return:

1. **Design system** — the system you chose: color tokens (light + dark), typography, spacing, radius, elevation, motion, border treatments, accessibility notes (contrast ratios verified)
2. **Component primitives library** — visual specs with state matrices for whatever components you use (buttons, cards, list items, switches, dialogs, snackbars, chips, top bars, empty states, loading states, error states, calendar/heatmap cells, bottom sheets, timer view, etc.)
3. **Screen mockups** — every screen in §7, light AND dark, plus all states from §8
4. **Concept sketches** for §7.11 + §7.12 (single frame each)
5. **Notification visual examples** — for each of the 5 types in §6
6. **Microcopy library** — exact strings for visible labels, buttons, dialogs, empty states, errors, notifications
7. **Engineering handoff notes** — Compose-ready: dp values, color hex, font sizes, anything needed to implement without guessing

If anything in the brief is ambiguous, list your assumptions explicitly before generating mockups.
