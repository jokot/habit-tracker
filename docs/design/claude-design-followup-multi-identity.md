# Habitto — Design Follow-up: Multi-Identity Model

The earlier design treated identity as singular ("current identity", "change identity"). That was wrong. **A user can hold multiple identities at once.** Update the design in place — replace single-identity surfaces with multi-identity equivalents. Don't keep both.

Tone, design system, and navigation pattern stay as already defined.

---

## What changes

A user is **simultaneously** several identities. Examples:
- "Healthy person" + "Reader" + "Disciplined sleeper"
- "Patient parent" + "Lifelong learner" + "Athlete"

Each identity is its own commitment. They aren't ranked or primary/secondary by default — equal citizens.

### Mechanics

- **Onboarding identity step:** multi-select, at least 1 required, no upper limit (recommend 1–4 for new users to avoid overwhelm — guidance only, not enforced).
- **Habits → identities:** every habit is associated with ≥ 1 identity. A habit can support multiple identities (e.g. "Walk 30 min" supports both "Healthy person" and "Patient parent"). The data model is many-to-many.
- **Per-identity progress:** each identity gets its own progress signal — derived from the habits associated with it. Possible signals (designer picks visualization):
  - Active habits count
  - Days-with-≥1-log-for-this-identity in last N days
  - Per-identity heat / streak
- **Adding identities later:** opens recommended habits for that identity, user picks which to adopt (some may overlap with existing habits — show that overlap clearly).
- **Removing an identity:** deactivates the identity but does NOT delete habits (habits persist if they're still associated with at least one remaining identity, or stand alone if the user wants).
- **No "primary" identity** — but the user may want to feature one. Optional: allow pinning one identity to the top of the Identity screen / Home surface.

---

## Screens to revise

### Onboarding — Identity step
- Multi-select grid; at least 1 required to proceed.
- Light suggestion (hint, not gate): "Pick 1–3 to start. You can add more later."
- After identity selection → habits step shows habits grouped or filtered by chosen identities. If a habit is recommended by multiple chosen identities, surface that (e.g. "Recommended by: Healthy person, Reader").

### Identity surfacing on Home
- Replace any single "current identity" chip with a multi-identity surface:
  - A row/strip of identity chips/cards (avatar/icon + name)
  - Or an aggregate: "I am: a Healthy person, a Reader, a Disciplined sleeper"
  - Tap a single identity → drill into its detail view
- Decide: do identities scroll horizontally if many, wrap to multi-line, or collapse to a single "Identities" entry that opens the full list?

### Identity screen (replaces single-identity screen)
- List of all active identities, each as a card showing:
  - Name + description + icon
  - Per-identity progress signal (your pick from the mechanics list above)
  - Linked habits count + tap → list of associated habits
  - Manage entry (rename / change icon / remove)
- "Add identity" CTA — opens identity browser with seeded options + ability to define custom identity
- Inactive / past identities — accessible via secondary tab or expander, optional re-activation

### Identity detail / detail card
- Single identity zoomed in
- Shows: name, description, icon, all habits associated with it, per-identity heat / streak / log activity
- Edit + delete actions
- "Why this identity" — optional reflection field user can write to themselves

### Add identity flow
- From Identity screen "Add identity" CTA
- Pick from seeded list OR create custom (name, description, icon)
- After picking: show recommended habits, user multi-selects which to adopt — for any habit that already exists in the user's set, show "Already tracking" badge with option to associate it with the new identity

### Habit form (CRUD) — updated
- Habit form now has multi-select identity field (at least 1 required)
- When editing an existing habit, show all identities it's associated with
- If a habit is associated with multiple identities, deleting one identity association doesn't delete the habit

---

## Cross-feature implications

- **Per-habit streak**: still per-habit, not per-identity. But identity detail aggregates across habits.
- **Per-identity heat / streak**: optional new derived metric. Designer decides if Identity detail screen should show its own heatmap, or just stats. If you add per-identity heat, define the metric clearly.
- **Notifications**: do any notifications mention identity? E.g. "Your Reader streak is at risk" vs the current generic "Streak at risk"? Recommend exploring an option for per-identity notification copy when only one identity is active that day.
- **Exchange-rate UI**: previously tied to overall consistency. With multi-identity, does the exchange rate compound per-identity, globally, or both? Designer picks; explain reasoning.
- **Onboarding flow** must accommodate the multi-select with no upper bound while preventing the user from getting stuck picking forever.

---

## Open design questions

For each, list your stance:

1. Should identities be **rankable** by the user (drag to reorder, pin a primary) or always equal?
2. When a habit is associated with multiple identities and the user logs it, does it count toward each identity's per-identity progress? (Recommend yes.)
3. On Home, if the user has 5+ identities, do they all surface, or does the UI compress (e.g. "+2 more")?
4. Identity icons — designer-curated set, user-uploadable, or text initial avatar fallback?
5. Onboarding: if the user picks 4 identities, the recommended-habits list could be huge. How do you keep it scannable?

---

## Deliverables

1. **Updated information-architecture diagram** showing Identity screen + Identity detail nested correctly under the existing navigation pattern
2. **Onboarding identity step (revised)** — multi-select with hint
3. **Home identity surface (revised)** — multi-identity treatment
4. **Identity screen** — list of all active identities + Add CTA
5. **Identity detail screen** — single identity zoomed in + linked habits + per-identity progress
6. **Add identity flow** — picker + recommended habits + already-tracking handling
7. **Habit form (revised)** — with multi-select identity field
8. **Notification copy revision** (if you choose per-identity)
9. **Exchange-rate revision** (if you choose to scope it per-identity)
10. **Microcopy** — labels for "Add identity", "Remove identity", "Already tracking", multi-identity Home strip, etc.

Stances on the 5 open questions, called out explicitly.
