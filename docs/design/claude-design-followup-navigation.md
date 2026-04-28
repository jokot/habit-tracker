# Habitto — Design Follow-up: Identity, CRUD entry points, Exchange rate, Navigation

Round-2 design brief. The first pass produced screens for onboarding, home, settings, streak history, habit/want CRUD, freezes, want-timer, widgets, and exchange-rate concept. Five gaps remain. Resolve them in this round.

This brief stands alone — no need to re-read the original. The product mechanics and tone you already designed for still apply.

---

## Gaps to fill

### Gap 1 — Identity has no presence post-onboarding

Identity is the foundation of the product ("become someone, by doing something") but currently only appears during the 4-step onboarding. After onboarding it's invisible. This breaks the core thesis — the user should feel their identity surfaced throughout daily use.

Design:
- Where on Home should the user's current identity be visible? (e.g. subhead under "habitto", chip near greeting, header card, etc.)
- A dedicated **Identity screen** that shows:
  - Current identity (name + description + icon)
  - Why these habits — link from each recommended habit back to the identity that motivated it
  - Option to **change identity** (with a confirmation explaining what happens to existing habits)
  - History of past identities the user committed to (motivational continuity)
- Identity selection during onboarding produces a commitment moment — does the same gravitas need to apply when changing identity later?

### Gap 2 — No entry point for habit / want / identity management

Users currently can't add, edit, or delete habits or wants after onboarding. They also can't change identity. The CRUD screens exist but nothing leads to them.

Design:
- How does the user reach the habit list? Want list? Identity screen?
- Are these surfaces top-level (always reachable) or deep-linked from contextual entry points (e.g. tap habit on Home → habit detail → manage all habits)?
- Make the difference between "log a habit today" (Home) and "manage what habits I track" (CRUD) visually clear so users don't confuse them.

### Gap 3 — No entry point for exchange-rate UI

The exchange-rate concept (rewards for sustained habit consistency by appreciating point value over time) was sketched but has no entry point. Where in the app does it live, and how does the user discover it?

### Gap 4 — Navigation architecture decision

The app now has at least these top-level destinations:
- Home (daily logging)
- Streak history (heatmap calendar)
- Identity (current identity + history)
- Manage (habits, wants — possibly tabbed)
- Settings
- Exchange rate

Plus contextual destinations: habit detail, want-timer, freezes inventory.

Pick a navigation pattern and justify it:
- **Bottom navigation bar** (Material recommendation for top-level destinations, max 5 items)
- **Navigation drawer** (off-canvas, holds more items, less discoverable)
- **Hub-and-spoke from Home** (single home with cards/links to everything else)
- **Hybrid** (bottom nav for 3-4 most-used, drawer/menu for the rest)

Constraints:
- Mobile-first (Android phone, ~360-430dp wide)
- Auth-optional — guest users should still see the same nav
- "Daily logging" is the dominant flow — nav must keep Home one tap away
- Respect Android conventions (back gesture, predictive back)

### Gap 5 — Cross-feature surfacing

Some features benefit from being surfaced outside their dedicated screen:
- **Freeze inventory** — visible somewhere on Home or Streak history? Always visible, or only when relevant (low streak, recent miss)?
- **Exchange rate change** — when the rate ticks up, how does the user notice? (notification? inline banner? celebration moment?)
- **Per-habit streak** — should it show on the Home habit card, on the habit detail screen, or both?

---

## Deliverables for this round

Return:

1. **Information architecture diagram** — top-level destinations + how secondary screens nest under them
2. **Navigation pattern recommendation** with reasoning (bottom nav / drawer / hub / hybrid)
3. **Identity surfacing on Home** — mockup showing where/how identity appears
4. **Identity screen mockup** — dedicated screen, light + dark, with all states
5. **Updated Home / Streak history / Settings** showing the navigation chrome (bottom nav or whatever you chose) integrated
6. **Entry-point mockups** — exact tap targets/paths for: identity screen, habit management, want management, exchange rate, freezes
7. **Cross-feature surface decisions** — annotations on Home (and other screens) showing where freeze inventory, exchange-rate signal, per-habit streak appear
8. **Microcopy** — labels for new nav items + identity surface

Tone and design system from Round 1 carry over. Don't re-derive tokens; reuse.

If any choice trades off against another (e.g. bottom nav vs hub-and-spoke), state the tradeoff explicitly and pick a stance.
