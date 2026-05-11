# Want CRUD — locked decisions before spec writing

This doc captures decisions made after reviewing the latest canvas (`screens-v3.jsx` + `canvas.html`). Engineering spec writes against these. Designer pass uses the action list at the bottom.

## Locked decisions

### 1. Rate ladder

Adopt the canvas's new ladder: **1.0× / 1.2× / 1.4× / 1.6× / 2.0×** (5 tiers).

Engineering tasks:
- Update `ExchangeRateCalculator.tiers` to the new multipliers.
- Recalibrate balance projections on existing user accounts — past want spends will recompute under the new rates on next sync.
- Add a one-time migration banner on `Today` after upgrade so users aren't surprised by the higher tier costs ("Spend rates updated — see Exchange rate.").
- Update existing unit tests + comparison-row rendering math.

### 2. Rate model: global only

Original "global rate per user" decision stands. **Drop per-identity rates entirely.**

Designer task:
- Remove `ExchangeRateV2` artboard mounts from `canvas.html`. Keep the JSX function in `screens-v3.jsx` for future revival but unmount it.
- No "Backlog" label needed — clean removal preferred over mixed signals.

If per-identity revives later, it gets its own brainstorm pass + phase.

### 3. Want CRUD scope = mounted canvas artboards only

The canvas mounts:
- `WantList` + `WantList dark` + `WantList empty`
- `WantDetail` (with Start timer button — non-functional placeholder until timer ships)
- `WantForm` new / edit / dark
- `WantListMenuOpen` (overflow menu) + `WantListWithHidden` (hidden section expanded)
- `SeededWantsReference` (table)

Designer-defined-but-unmounted JSX (`WantDetailNoTimer`, `WantFormStepper`, `WantFormCostEdit`, `TodayLongPressWant`, `WantListAnnotated`) is **not in scope.** Engineering builds against canvas mounts only.

Implications:
- WantDetail's "Start timer" button is a visual placeholder; tapping does nothing or surfaces a toast "Coming soon" until timer phase ships.
- WantForm has no cost-edit warning + no stepper increment annotation. Engineering picks sensible default — `0.1` step, long-press for `1.0` jumps, tap big numeral for keyboard input — without canvas mockup.
- Today's long-press-to-Detail gesture is implementer-derived from existing habit pattern (consistent with `Habit row long-press → Habit detail`). Not blocked on visual mock.

### 4. Seeded WANTS list

Use production `SeedData.wantActivities` (15 items). Canvas's 8-item `WANTS` array in `shared.jsx` is informational only.

Production list (canonical):
| id | name | unit | costPerUnit |
|---|---|---|---|
| 20...001 | Scroll (reel/TikTok/short) | minutes | 1.0 |
| 20...002 | Browse Twitter/X | minutes | 0.5 |
| 20...003 | Browse Instagram feed | minutes | 0.5 |
| 20...004 | YouTube long-form | minutes | 0.1 |
| 20...005 | YouTube shorts | minutes | 1.0 |
| 20...006 | Netflix / streaming | minutes | 0.067 |
| 20...007 | Casual mobile game | minutes | 0.2 |
| 20...008 | Valorant Deathmatch | matches | 1.0 |
| 20...009 | Valorant Ranked | matches | 3.0 |
| 20...010 | PC gaming session | minutes | 0.1 |
| 20...011 | Online shopping browse | minutes | 0.2 |
| 20...012 | Purchase session | sessions | 2.0 |
| 20...013 | Junk food / fast food | meals | 2.0 |
| 20...014 | Sugary drinks | drinks | 1.0 |
| 20...015 | Donut / dessert | pieces | 1.0 |

Engineering tasks:
- Audit upgrade path: existing users may have old (8-want) seeded set or differ; reconcile per-user via sync without overwriting custom-edited cost values.
- New users: `SetupUserWantActivitiesUseCase` already seeds this list.

### 5. Cost stepper + display rules

Stepper UI follows canvas idiom (+/- buttons with live preview).

Behavior (engineering-derived; no canvas mockup):
- Step size: `0.1` per tap.
- Long-press +/-: `1.0` jumps.
- Tap the big numeral: free-form decimal entry via keyboard.
- Min `0.0` (FREE — already supported).
- Max `999`.

Display rules — already shipped:
- Per-tap deduction (`pointsSpentWithRate(1, cost, rate)` integer with min-1 floor).
- `FREE` label when `costPerUnit == 0.0` exactly.
- Hidden wants excluded from `Today` + `ExchangeRate` comparison list.

### 6. Cost-edit retroactive behavior

**Recompute** is canonical (matches current production: `GetPointBalanceUseCase` reads `activity.costPerUnit` × stored quantity at compute time).

UX implication for `WantForm` edit mode:
- Show warning banner when editing cost on a want that has past logs: "Editing this cost rewrites your spend history."
- Engineering implements the banner; canvas didn't mount the warning variant but the copy is locked here.

No schema change needed. Snapshot semantics rejected — would require `WantLog.costPerUnitAtLog` migration.

### 7. Today → WantDetail navigation

**Long-press** the want row to open `WantDetail`. Mirrors `Habit row long-press → Habit detail` existing behavior.

Tap (single press) remains "spend (3-sec commit)" — unchanged.

Engineering builds without explicit canvas mockup; pattern is consistent with habit row.

### 8. WantDetail "Start timer" button

Render the button per canvas. On tap, show a toast: **"Timer coming soon."** Don't navigate, don't show a non-functional timer screen. Remove this stub when the timer surface ships.

## Action list for next Claude Design pass

Send this list to Claude Design:

1. **Remove `ExchangeRateV2` artboard mounts from `canvas.html`.** Keep JSX. Ship global rate only.
2. **Replace canvas mount of `WantDetail` with `WantDetailNoTimer`** OR add a "(stub)" caption next to the Start timer button on the existing `WantDetail` mount, so implementers see it's intentional.
3. **Mount `WantFormCostEdit`** (recompute variant) so the cost-edit warning banner is visualized.
4. **Mount `TodayLongPressWant`** so Today's long-press gesture has a visual reference.
5. **Update `SeededWantsReference` to display production `SeedData.wantActivities`** (15 items) instead of canvas `WANTS` (8 items). Reconcile names + units + costs.
6. **Confirm rate ladder copy on `ExchangeRateScreen` matches `1.0× / 1.2× / 1.4× / 1.6× / 2.0×`** + recompute days-to-next math accordingly.

## Out of scope for this phase

- Want timer surface (separate phase).
- Per-identity rates (deferred — reconsider later).
- Cost-snapshot schema (engineering decision: stay with recompute).
- Migration banner localization (single-language for now).
