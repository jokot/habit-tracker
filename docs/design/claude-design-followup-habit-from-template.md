# Claude Design follow-up — Add habit from template

Today's `HabitList` only offers a "New habit" FAB that opens an empty `HabitForm`. Users who picked an identity at onboarding had access to a curated template list (`OnboardStep2` — habits recommended per identity). Adding a habit later loses that scaffolding — users start from a blank form even though the same templates are still relevant.

Goal: a post-onboarding "add from template" flow that surfaces the same recommended-habit set whenever the user wants to add new habits to an existing identity. The templates already exist in canvas (driven by the `IDENTITIES → recommended habits` map). This is purely a UI surface for an existing data source.

## Surface to design

### 1. Entry point on `HabitList`

Current FAB opens `HabitForm`. Replace with a **two-option chooser** when tapped:

- **From a template** — primary action. Opens `TemplateHabitPicker`.
- **Custom habit** — secondary action. Opens existing `HabitForm` blank.

Mock options:
- **A.** Tap FAB → bottom sheet with two large tap targets ("Pick from templates" + "Build custom"). Each has a one-line description.
- **B.** Two FABs side-by-side (less common in Material 3 — feels noisy).
- **C.** Single FAB tap → `TemplateHabitPicker` directly, with a "Skip — build custom" link inside it. Promotes templates as the default path.

Prefer **C** — most users want recommended templates; advanced users can fall through. Show the variant.

### 2. `TemplateHabitPicker` screen — main surface

A picker that shows recommended habits grouped by the user's active identities. Mirror `OnboardStep2`'s visual structure, adapted to this context.

Layout:
- App bar: back arrow + title "Add habits" + trailing app-bar button "Build custom" (text button → opens blank `HabitForm`).
- Subtitle: "Pick from your identities, or build your own."
- Body: per-identity sections.
  - Section header: identity icon + name + active-habit count ("2 of 5 added").
  - Rows: template name + recommended target/threshold + multi-select checkbox.
  - Already-added state: row checked + caption "Already in your habits" (read-only — disabled tap).
  - Tap row body → toggle selection (consistent with `OnboardStep2`'s habit chips).
- Sticky footer: count chip ("3 selected") + primary button "Add 3 habits".

Empty states:
- User has zero identities → empty state: "Pick an identity first to see templates" → CTA to identity picker.
- All identities already have all templates added → empty-success state: "You've added every recommended habit. Try a custom one." → CTA to blank `HabitForm`.

### 3. Cross-identity templates

A single template (e.g. "Walk outside") may appear under multiple identities (Healthy + Mindful). When the user selects it once, all matching identity sections show it as added.

Mock the row state when a template appears in two sections and the user checks it in one:
- Both sections show "Already in your habits" caption.
- Or: only the section where it was checked shows the active state; others show a discreet "(also matches Mindful)" tag.

Prefer first interpretation — simpler mental model.

### 4. Pre-save customization

After tapping "Add N habits", user lands either:
- **A.** Directly on `HabitList` with new habits inserted (fastest — defaults are already sensible).
- **B.** A confirm-and-edit screen: list of selected templates with inline editable target / threshold per habit, then "Save all". Lets users tweak defaults before commit.

Prefer **A** — defaults are already pre-tuned by the template author. Users who want to tweak can edit each habit individually post-add. **B** delays gratification + adds friction.

### 5. Empty-template fallback

If the templates JSON is missing or zero-length for an identity (data drift or new identity not yet covered), show a per-identity empty state inside the picker:
- "No templates for [identity name] yet. Build a custom habit." → row-level CTA → `HabitForm` blank with identity pre-selected.

## Visual + interaction notes

- Reuse `OnboardStep2`'s template card styling — same chip, same icon, same target line. Consistent visual = users who completed onboarding recognize the pattern instantly.
- Selection animation should match onboarding (subtle scale + tinted background) — don't introduce a new pattern.
- Bottom sheet entry (option A above for entry chooser, if picked over option C) should feel light — keep it short, large tap targets, no second screen.

## Out of scope

- Editing template definitions in-app (admin/data concern, not UI).
- Sharing a custom habit as a community template (deferred).
- Sort / filter the template list (current set is small enough that grouping by identity is the only structure needed).

## Open question for designer

Should we drop the existing `HabitForm` blank flow entirely and force everyone through the picker first (templates + "Build custom" button inside)? Or keep both as parallel entry points? Mock both — picking the cleaner one will be much easier with both side by side.
