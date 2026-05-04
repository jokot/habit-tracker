# Habitto — Design Follow-up: Add Habit to Existing Identity

Phase 5e-3 (habit CRUD: form + delete + custom-habit) just shipped. The free-form `HabitForm` is reachable from three entry points:

1. **HabitList FAB "New habit"** — opens form, no pre-fill, custom create
2. **HabitDetail edit icon** — opens form, pre-filled with existing habit data
3. **IdentityDetail "+ Add habit" dashed row** — opens form with the current identity pre-selected

Entry point #3 was inferred — the existing canvas (`screens.jsx:2383-2392`) shows only the dashed row, not what happens after the tap. The current implementation jumps straight to the free-form `HabitForm`. **That skips templated suggestions specific to the identity.**

Phase 5e-4 will add a templated-add flow scoped to the tapped identity. **We need a design for the intermediate screen/sheet that lists this identity's recommended habit templates plus a custom-habit fallback.**

Tone, design system, navigation pattern stay as already defined.

---

## The flow we need designed

```
IdentityDetail
   │
   │ tap "+ Add habit" (existing dashed row, screens.jsx:2383)
   ▼
[NEW: AddHabitToIdentity screen/sheet]   ← design this
   │
   ├── tap recommended template ──► save link (or save habit + link) ──► back to IdentityDetail
   │
   └── tap "Define a custom habit" ──► HabitFormScreen (existing) ──► save ──► back to IdentityDetail
```

Existing canvas reference: `AddIdentityFlow` step 2 (`screens.jsx:2534+`). That screen does the same shape — recommended habits list + "Define a custom habit" button — but in the context of *adding a brand-new identity*. The new screen for 5e-4 is **the same shape, scoped to a single existing identity**.

---

## Screen requirements

### `AddHabitToIdentity` (new)

**Entry:** tap "+ Add habit" on IdentityDetail of any active identity (the user already has this identity).

**Header:**
- Title or hero: identity name + brief prompt. Examples:
  - "What do you do as a Reader?"
  - "More habits for Athlete"
- Optional: identity glyph/avatar in hero
- Close icon (top-left) — pops back to IdentityDetail without changes

**Body — recommended templates list:**
- Templates curated for this identity (excluding ones already linked to ANY of the user's habits)
- Each row matches the existing `AddIdentityFlow` step 2 row style:
  - Habit glyph (identity hue)
  - Name + "{target} × {threshold} {unit}" subtitle
  - Tap to add — single-tap commit (no multi-select needed in this flow)
- "Already tracking" badge per row if the template's habit already exists for the user under a *different* identity. Tapping that row should **link the existing habit to this identity** (additive), not create a new one. Match canvas badge style: `screens.jsx:2590-2602` ("Already tracking · will associate")

**Custom CTA (footer):**
- Text button: "+ Define a custom habit"
- Tap → opens `HabitFormScreen` with this identity pre-selected (existing 5e-3 behavior, no change needed there)

**Empty state** (rare — user already has every recommended template linked):
- "Nothing left to recommend for {identityName}."
- Custom CTA still visible.

**No multi-select / no commit step.** Each tap = 1 habit added. User stays on the screen and can tap multiple rows in sequence. Optional: small toast "Added {habit} to {identity}" per add.

---

## Reuse opportunity — AddIdentityFlow step 2 "+ Define a custom habit"

Canvas `screens.jsx:2608` shows a "+ Define a custom habit" text button at the bottom of `AddIdentityFlow` step 2. The flow this button opens isn't designed yet. **The same custom CTA should open the same `HabitFormScreen`** — pre-selecting the new identity being added.

So the design output should be:

1. **`AddHabitToIdentity`** — new screen for adding a habit to an *existing* identity (5e-4 primary deliverable)
2. **Spec the custom-CTA tap target on `AddIdentityFlow` step 2** — clarify that the existing button opens `HabitFormScreen` with the new identity pre-selected. No new screen, just confirm the link.

---

## Constraints

- **Don't redesign `IdentityDetail`, `HabitFormScreen`, or `AddIdentityFlow` step 2.** They're shipped. Just describe the new screen and its entry/exit relationships.
- **Match existing visual language:**
  - Identity hue + glyph for hero
  - Habit-row layout from `AddIdentityFlow` step 2 (`screens.jsx:2562-2606`)
  - "+ Define a custom habit" button matches the existing one at `screens.jsx:2608`
  - Sticky footer pattern from canvas (`screens.jsx:2613-2625`) if you choose footer over inline CTA
- **Modal sheet vs full screen:** designer's call. Full screen feels right (the rec list can be long). Sheet works if compact. Pick one and justify briefly.
- **No reorder, no editing of recommended templates.** That's `HabitFormScreen` territory.

---

## What to deliver

Add to the canvas (next to or replacing the deferred placeholder for this flow):

1. `AddHabitToIdentity` mockup — light + dark
2. One callout / annotation explaining how it ties into:
   - IdentityDetail "+ Add habit" entry
   - `HabitFormScreen` exit (custom CTA path)
   - "Already tracking" link-only path

If you change anything else (existing screens, IA), call it out explicitly so we can review the diff.
