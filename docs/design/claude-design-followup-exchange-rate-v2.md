# Claude Design follow-up — Unmount ExchangeRateV2

## Action for Claude Design

Unmount the `ExchangeRateV2` artboards from `canvas.html`. Two mounts to remove (light + dark variants currently displayed in the "Exchange rate · all 5 tiers" or adjacent section):

```
<ExchangeRateV2 />
<ExchangeRateV2 dark />
```

Keep the `function ExchangeRateV2({ dark })` definition in `screens.jsx` for future revival. Just no canvas reference.

## Why

Per-identity rates are deferred. Only the global rate ladder ships. With both `ExchangeRateScreen` (global) and `ExchangeRateV2` (per-identity) on canvas, the implementer can't tell which is in scope.

The `ExchangeRateV2` design also has an internal contradiction: hero card says "Geometric mean of identity rates" while the bottom info banner says want spending uses "worst-performing identity's rate". These describe different rate models. Cannot ship without picking one — and per-identity isn't shipping now anyway.

## After applying

Canvas should show only the global tier ladder under the Exchange rate section:

```
<ExchangeRateScreen rate={1.0} tier={1} ... />   // tier 1 light
<ExchangeRateScreen rate={1.2} tier={2} ... />   // tier 2 light
<ExchangeRateScreen rate={1.4} tier={3} ... />   // tier 3 light
<ExchangeRateScreen rate={1.6} tier={4} ... />   // tier 4 light
<ExchangeRateScreen rate={2.0} tier={5} topTier ... />   // tier 5 light
<ExchangeRateScreen dark rate={1.4} tier={3} ... />   // tier 3 dark
<ExchangeRateScreen dark rate={2.0} tier={5} topTier ... />   // tier 5 dark
```

No `ExchangeRateV2` references remain on canvas.
