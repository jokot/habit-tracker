# Claude Design follow-up — Update seeded WANTS list

## Action for Claude Design

Update the seeded WANTS list in `shared.jsx` and propagate the change through every Want artboard (`WantList`, `WantDetail`, `WantForm`, comparison rows in `ExchangeRateScreen`, `SeededWantsReference` table). Remove the existing 8-item `WANTS` array entirely and replace with the 14 items below.

Each item has a fixed Material Icons glyph — do NOT auto-derive from name. The icon is part of the seed and must be displayed exactly as specified.

## New WANTS list (14 items)

| # | id | name | unit | cost | icon (Material Icons) |
|---|---|---|---|---|---|
| 1 | tiktok | TikTok | minutes | 1.0 | `play_circle` |
| 2 | yt-shorts | YouTube Shorts | minutes | 1.0 | `play_circle` |
| 3 | youtube | YouTube | minutes | 0.1 | `smart_display` |
| 4 | netflix | Netflix | minutes | 0.1 | `local_movies` |
| 5 | twitter | Twitter/X | minutes | 0.5 | `chat_bubble` |
| 6 | instagram | Instagram | minutes | 0.5 | `photo_camera` |
| 7 | reddit | Reddit | minutes | 0.5 | `forum` |
| 8 | gaming | Gaming | minutes | 0.5 | `sports_esports` |
| 9 | shopping | Online shopping | minutes | 0.5 | `shopping_bag` |
| 10 | junkfood | Junk food | meals | 5 | `restaurant` |
| 11 | snacks | Snacks | servings | 2 | `restaurant` |
| 12 | sweets | Sweets | pieces | 2 | `cake` |
| 13 | sugary | Sugary drinks | drinks | 2 | `local_drink` |
| 14 | coffee | Coffee | cups | 1 | `local_cafe` |

Cost values are exact. Render them in `WantList` as `−{cost} pt / {unit}` matching production formatting: `1.0` not `1`, `0.5` not `.5`, integer values displayed as integers (`5` not `5.0`).

## Icon constraint

Each row uses the exact Material Icons glyph in the table. `play_circle` is shared by TikTok + YouTube Shorts (same category — short-form video). `restaurant` is shared by Junk food + Snacks. All other icons are unique to a single item.

The custom-create form's icon picker shows ONLY these 12 unique glyphs, plus a `more_horiz` fallback for "other":

```
play_circle, smart_display, local_movies, chat_bubble, photo_camera,
forum, sports_esports, shopping_bag, restaurant, cake, local_drink,
local_cafe, more_horiz
```

User picks any of these when creating a custom want.

## Propagation

Update every artboard that displays a want row to use the new list + icons + costs:

- **WantList** (light, dark, empty) — use full 14 items with the per-item icons. All seeded; no custom shown by default.
- **WantDetail** (default `wantId`) — pick an item from the new list. Use `tiktok` or `instagram` as a representative seeded want.
- **WantForm** (new + edit modes) — the `units` chip row stays as `minutes, servings, match, episode, session, item`. Add `drinks, cups, pieces, meals` to that chip row so all 14 unit values are represented as picker options.
- **ExchangeRateScreen** comparison rows — render the new 14 items, sorted alphabetically by default. Display per-tap deduction (locked in prior pass).
- **SeededWantsReference** table — replace contents with the new 14-row table. Drop the old 8-item canvas `WANTS` reference.

After applying, the canvas should show only the 14 items above. No remnants of the previous list.

## Cost rationale (for designer reference)

| Tier (pt/unit) | Items | Why |
|---|---|---|
| 0.1 | YouTube, Netflix | Passive video — low disengagement, mostly background watching |
| 0.5 | Twitter/X, Instagram, Reddit, Gaming, Online shopping | Active scroll / engagement / decision-laden time |
| 1.0 | TikTok, YouTube Shorts, Coffee | Short-form video addiction loop; mild physical vice |
| 2 | Snacks, Sweets, Sugary drinks | Mid-impact physical vices |
| 5 | Junk food | Full-meal impact — strongest discouragement in the seeded set |
