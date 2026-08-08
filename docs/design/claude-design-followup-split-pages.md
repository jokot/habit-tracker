# Habitto — Design Follow-up: Split the canvas into per-feature pages

`canvas.html` is one page holding 21 `DCSection`s and loading eight JSX files on every visit. It has outgrown a single board. Split it so each feature has its own page.

This is a restructure, not a redesign. Every artboard that exists today should still exist afterward, unchanged, on some page.

---

## Why

- One board with 21 sections means scrolling past onboarding, auth, settings, and notifications to look at one widget.
- Every page load parses all eight JSX files through in-browser Babel — including `screens.jsx` — regardless of which section is being reviewed.
- Review and feedback happen per feature, but the artifact is monolithic. A link to "the widgets design" is currently a link to everything.

## Current structure

`canvas.html` loads, in this order:

```
frames/design-canvas.jsx
shared.jsx
screens-v5.jsx
home.jsx
screens.jsx
screens-v2.jsx
screens-v3.jsx
screens-v4.jsx
```

(`frames/android-frame.jsx` and `frames/tweaks-panel.jsx` are in the project but not loaded here.)

Then one `<App>` renders a `<DesignCanvas>` with 21 `DCSection`s, grouped by comment banners into three bands: **meta/reference**, **redesign of existing surfaces**, **future surfaces**.

## The hard part: the version files are chronological, not feature-based

`screens.jsx` and `screens-v2/v3/v4/v5.jsx` are design *rounds*, layered over time. A single feature is therefore scattered across several of them. The `want` section alone pulls from three:

- `WantList` → `screens-v2.jsx`
- `WantListMenuOpen`, `WantListWithHidden` → `screens-v3.jsx`
- `WantDetail`, `WantForm`, `SeededWantsReference` → `screens-v4.jsx`

Splitting by feature means transposing the codebase from a time axis to a feature axis. **That is the actual work.** Splitting only the HTML while every page still loads all five `screens-*.jsx` files gets the navigation benefit but none of the load benefit, and leaves the underlying tangle in place.

Do both, in the staged order below.

## Stage 1 — Split the pages

One page per feature. Each gets the same boilerplate head (Material Symbols font link, `tokens.css`, the React / ReactDOM / Babel UMD scripts **with their existing integrity hashes**, `frames/design-canvas.jsx`, `shared.jsx`), then only the JSX that page needs, then its own `<App>` with just that feature's sections.

**Preserve every existing `DCSection` and `DCArtboard` `id` verbatim** so existing links and anchors keep working.

| Page | Sections it takes | Band |
|---|---|---|
| `index.html` | — hub | — |
| `design-system.html` | `ds`, `ia` | meta |
| `onboarding.html` | `onboard` | redesign |
| `auth.html` | `auth` | redesign |
| `today.html` | `today`, `today-filter`, `today-refresh` | redesign |
| `streak.html` | `streak-history` | redesign |
| `you-hub.html` | `you-hub`, `settings` | redesign |
| `identity.html` | `identity-list`, `identity-detail`, `identity-add` | future |
| `habit.html` | `habit`, `habit-add-template` | future |
| `want.html` | `want` | future |
| `want-timer.html` | `want-timer` | future |
| `exchange-rate.html` | `exchange-v3-tiers` | future |
| `freezes.html` | `freezes` | future |
| `widgets.html` | `widgets` | future |
| `notifications.html` | `notifications` | future |

`index.html` should be a real hub, not a bare link list: group by those three bands, carry each page's readiness tag, and give each entry a one-line description of what's on it.

Keep `canvas.html` working as-is during this stage, or make it redirect to `index.html`. Don't leave a broken entry point at the URL people already have.

## Stage 2 — Split the JSX by feature

Once the pages exist, consolidate each feature's components out of the chronological files into one file per feature — `components/want.jsx`, `components/widgets.jsx`, `components/today.jsx`, and so on — so each page loads only what it renders.

Constraints:

- `shared.jsx` stays as-is and loads first on every page. It's the genuine common layer: `HABITS`, `WANTS`, `Icon`, `Frame`, `HabitGlyph`, `HabitRing`, `HeatCell`, `HeatRow`, `buildHistory`, `classForDay`, `habitHue`, plus the token and type classes.
- `frames/design-canvas.jsx` stays as-is and loads before any feature file — it provides `DesignCanvas`, `DCSection`, `DCArtboard`, `ReadinessTag`.
- The `window` global-assignment pattern stays. It works; changing module systems is a separate fight.
- Where a later round genuinely supersedes an earlier component, keep the superseding one and delete the dead one — but **say which ones you dropped**. Don't silently discard artboards.
- Where two features share a component that isn't in `shared.jsx`, move it into `shared.jsx` rather than duplicating it.

Retire `screens-v2/v3/v4/v5.jsx` only when every component in them has a home. If stragglers resist categorization, leave a `screens-misc.jsx` and list what's in it rather than forcing a bad fit.

## Fix while you're in there

The `today` section's `subtitle` attribute is broken — leftover text from an earlier edit:

```
… scrolls with the list, edge-to-edge full width.">nner · no per-habit streak chip on Home.">
```

The trailing `nner · no per-habit streak chip on Home.">` renders as literal text on the board. Repair the sentence (presumably "…no exchange-rate banner · no per-habit streak chip on Home").

Also: `SectionHead` in `canvas.html` accepts `title` and `subtitle` but renders only `ReadinessTag`, and nothing calls it. Delete it.

## Deliverables

1. The pages above, each self-contained and working standalone.
2. `index.html` hub, grouped by band, with readiness tags and one-line descriptions.
3. Stage-2 per-feature JSX files, with `screens-v*.jsx` retired or reduced to a documented remainder.
4. A short summary listing: which sections landed on which page, which components moved from which version file, and anything dropped as superseded.
5. Confirmation that every existing section and artboard `id` survived the move.
