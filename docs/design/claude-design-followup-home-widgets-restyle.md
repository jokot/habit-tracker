# Habitto — Design Follow-up: Home-screen widgets, restyle to match the design system

`screens-v5.jsx` gets the four-widget structure right — the sizes, states, and Glance constraints all landed. The **visual style** drifted, though. It reads as a separate product from the rest of the canvas.

This is a restyle pass only. Keep every widget, size, and state that's already there. Change how they look.

---

## The problem

v5 introduces a new visual language instead of using the app's. Concretely, it added: a hand-rolled 12-value hex palette, filled color chips behind every icon, error-red as a category color, left-edge tick bars, colored value captions, and uppercase letterspaced section headers. None of that exists anywhere else in Habitto.

The previous widget design — the original `Widgets({ dark, size })` in `screens.jsx` — was restrained in a way that matched the app: neutral surfaces, **one** accent color (flame), tint applied only to the icon glyph itself, and type from the `t-*` scale. Go back to that.

## Reference the existing components, don't invent

Two already-shipped treatments are the answer to most of the questions v5 solved from scratch:

**Habit tile — from the previous `Widgets` in `screens.jsx`:**
```
background: 'var(--m-surface-1)', borderRadius: 10, padding: 8, textAlign: 'center'
  <Icon name={h.icon} size={20} color={<identity tint>} />   ← bare icon, no chip behind it
  '+1' in 500 10px, color: 'var(--m-on-surface-var)'          ← neutral caption, not colored
```

**Want row — from `WantList` in `screens-v2.jsx`:**
```
width: 40, height: 40, borderRadius: 12,
background: 'var(--m-surface-1)', color: 'var(--m-on-surface-var)'
  <Icon name={w.icon} size={20} />
```

Note what this means: **wants are neutral in this app.** They are not red anywhere — not in `WantList`, not in Want detail, not in the seed screens. `--m-error` is reserved for destructive actions (the Cancel zone on the timer banner). Spending points is normal behavior, not an error.

## Specific changes

1. **Delete `W5_HABIT_TINT` and `w5WantTint`.** Twelve hardcoded hex values replacing a token system is the root of the drift. Habit icons take an identity tint (a fixed-hex palette is fine — that constraint was real), but as **icon color only**, on a plain `--m-surface-1` tile. Want icons are `--m-on-surface-var` on `--m-surface-1`, per `WantList`.

2. **Remove the filled chip backgrounds** (`W5Glyph`'s `background: tint.bg`). The previous design put a bare tinted icon directly on the tile. A colored chip nested inside a tile inside a card is three boxes deep.

3. **Drop red for wants entirely.** Direction is already carried by the sign and the value text — `+1` versus `−2/min`. That's unambiguous and it's how the rest of the app reads. If you want one more signal, use weight or the existing `--m-on-surface-var`/`--m-on-surface` contrast step, not hue.

4. **Delete the left-edge tick bars** on grid tiles (the absolutely-positioned 3px `--m-error` / `#2E7D32` strips). Pure invention, and `#2E7D32` is a raw hex green that appears nowhere else.

5. **Neutral captions.** `+1` and `−cost` go back to `--m-on-surface-var`, not `t.fg`. In the previous design exactly one thing was colored: the flame. Keep it that way.

6. **Drop the uppercase letterspaced section headers.** `700 9px` + `letterSpacing 0.6` + `textTransform: uppercase` isn't in the app's type system. Use `t-label-s` in sentence case ("Habits", "Wants"), or drop the headers and let the divider separate the groups.

7. **Drop the circle-vs-square glyph shape coding.** Invented language, and at 22px nobody parses it. The app uses radius 12 rounded squares for want icons and the habit glyph for habits — that difference already exists.

8. **Restore the dense streak grid.** This is the biggest regression. Previous: 30 days at 15 columns, `gap: 2`, `borderRadius: 2`, small cells — a proper heatmap, and the signature look of the streak feature. v5 shows 14 days at 7 columns with 20–22px cells, `gap: 4`, `radius: 4`. It reads as a calendar week, not a streak history. Go back to ~30 cells at min size and ~60 expanded, small and tight. (Keep the explicit dp sizing — that constraint stands. Just make the cells small.)

9. **Use the type scale.** v5 has `9px`, `10px`, `11px`, `12px`, `14px` ad-hoc sizes. Use `t-label-s`, `t-body-s`, `t-title-s`, `t-numeral` as the previous design did.

10. **Remove the `W5SizeTag` floating labels** from the rendered widgets. Put the size annotation outside the mockup or in the section caption — it's currently overlapping the widget surfaces on the board.

11. **`color-mix(in srgb, …)` in the timer row border** — this can't render in Glance either, same category as `oklch`. Use `--m-outline-var` or a fixed hex.

12. **Soften the timer row.** A full `--m-primary-cont` fill is a large saturated block against otherwise neutral surfaces. The Home timer banner is the reference for how a running timer is presented in this app — match its restraint.

## The principle

The previous widget design had **one accent** — the flame — and everything else was neutral surface with a single tinted glyph. That restraint is what made it feel like Habitto. v5 colors nearly every element, and the result is loud. Strip the color back out; keep the structure.

## Deliverables

1. `screens-v5.jsx` restyled per the above. Same four components, same props, same states, same sizes.
2. Habit and want treatments visibly consistent with `Widgets` (old) and `WantList` respectively.
3. Streak grid back to a dense heatmap.
4. Light and dark for all four.
5. A short note on how habit-vs-want now reads without color coding — confirm the sign and value text carry it at both row and tile size.
