# Claude Design follow-up — Update seeded HABITS + IDENTITIES list

## Action for Claude Design

Replace the seeded `IDENTITIES` and `HABIT_TEMPLATES` data in `shared.jsx` with the lists below. Update every artboard that displays habits, habit templates, or identities — onboarding, identity strip on Today, identity detail, habit list, habit detail, habit form, template picker — to use the new seed.

Each habit row carries an explicit Material Icons glyph. Do not auto-derive icons from the habit name.

## 13 identities

| Emoji | Identity | Description |
|---|---|---|
| 📚 | Reader | Build a reading habit to expand knowledge and vocabulary |
| 🔨 | Builder | Develop your craft as a software developer |
| 🏃 | Athlete | Build physical strength and endurance |
| ✍️ | Writer | Express yourself through consistent writing practice |
| 🎓 | Learner | Stay curious and keep learning every day |
| 🌿 | Minimalist | Simplify your space and digital life |
| 🙏 | Devotee | Deepen your spiritual practice |
| 💪 | Health-Conscious | Build healthy daily habits for long-term wellness |
| 🎨 | Creator | Make things — visual, audio, or written art |
| 💰 | Saver | Build wealth and break expensive habits |
| 🤝 | Connector | Invest in people who matter |
| 🧭 | Adventurer | Try new things on purpose |
| 🍳 | Chef | Cook your way to better food and lower cost |

## Habits per identity (91 templates total)

Each habit has: name, unit, `defaultThreshold` (quantity per 1 point), `defaultDailyTarget` (target points per day), Material Icons glyph.

### 📚 Reader

| # | Habit | Unit | Threshold | Target | Icon |
|---|---|---|---|---|---|
| 1 | Read book | pages | 3.0 | 3 | `menu_book` |
| 2 | Read on Kindle | minutes | 10.0 | 2 | `menu_book` |
| 3 | Read article | minutes | 5.0 | 2 | `article` |
| 4 | Read research paper | minutes | 10.0 | 1 | `description` |
| 5 | Audiobook listen | minutes | 15.0 | 2 | `headphones` |
| 6 | Re-read passage | minutes | 5.0 | 1 | `menu_book` |

### 🔨 Builder

| # | Habit | Unit | Threshold | Target | Icon |
|---|---|---|---|---|---|
| 1 | Code project | minutes | 15.0 | 3 | `code` |
| 2 | Write tests | minutes | 10.0 | 2 | `bug_report` |
| 3 | Review code | minutes | 10.0 | 1 | `rate_review` |
| 4 | Refactor code | minutes | 10.0 | 1 | `code` |
| 5 | Read documentation | minutes | 10.0 | 2 | `menu_book` |
| 6 | Pair programming | minutes | 30.0 | 1 | `groups` |
| 7 | OSS contribution | minutes | 30.0 | 1 | `code` |
| 8 | Solve coding puzzle | puzzles | 1.0 | 2 | `extension` |

### 🏃 Athlete

| # | Habit | Unit | Threshold | Target | Icon |
|---|---|---|---|---|---|
| 1 | Push up | reps | 15.0 | 3 | `fitness_center` |
| 2 | Squat | reps | 20.0 | 3 | `fitness_center` |
| 3 | Pull up | reps | 5.0 | 3 | `fitness_center` |
| 4 | Sit up | reps | 20.0 | 2 | `fitness_center` |
| 5 | Walk | minutes | 10.0 | 2 | `directions_walk` |
| 6 | Run | minutes | 10.0 | 2 | `directions_run` |
| 7 | Cycling | minutes | 10.0 | 2 | `directions_bike` |
| 8 | Swim | minutes | 10.0 | 2 | `pool` |
| 9 | Stretching | minutes | 5.0 | 2 | `accessibility_new` |
| 10 | Plank | seconds | 30.0 | 3 | `timer` |
| 11 | Yoga session | minutes | 20.0 | 1 | `self_improvement` |
| 12 | HIIT session | minutes | 15.0 | 1 | `whatshot` |

### ✍️ Writer

| # | Habit | Unit | Threshold | Target | Icon |
|---|---|---|---|---|---|
| 1 | Journaling | minutes | 5.0 | 2 | `edit_note` |
| 2 | Blog writing | minutes | 15.0 | 2 | `rss_feed` |
| 3 | Creative writing | minutes | 10.0 | 2 | `create` |
| 4 | Outline | minutes | 10.0 | 1 | `description` |
| 5 | Draft | minutes | 10.0 | 1 | `edit` |
| 6 | Edit / proofread | minutes | 10.0 | 1 | `spellcheck` |
| 7 | Morning pages | pages | 3.0 | 1 | `edit_note` |
| 8 | Newsletter | minutes | 30.0 | 1 | `email` |

### 🎓 Learner

| # | Habit | Unit | Threshold | Target | Icon |
|---|---|---|---|---|---|
| 1 | Watch educational video | minutes | 10.0 | 2 | `smart_display` |
| 2 | Take online course | minutes | 15.0 | 2 | `cast` |
| 3 | Practice language | minutes | 10.0 | 2 | `language` |
| 4 | Flashcard review | cards | 5.0 | 3 | `style` |
| 5 | Listen to podcast | minutes | 20.0 | 2 | `podcasts` |
| 6 | Take notes | minutes | 5.0 | 2 | `edit_note` |
| 7 | Solve practice problem | problems | 1.0 | 3 | `calculate` |
| 8 | Watch documentary | minutes | 30.0 | 1 | `movie` |

### 🌿 Minimalist

| # | Habit | Unit | Threshold | Target | Icon |
|---|---|---|---|---|---|
| 1 | Declutter space | minutes | 5.0 | 1 | `cleaning_services` |
| 2 | Organize items | minutes | 5.0 | 1 | `inventory` |
| 3 | Digital cleanup | minutes | 5.0 | 1 | `delete_sweep` |
| 4 | Donate item | items | 1.0 | 1 | `volunteer_activism` |
| 5 | Inbox zero | minutes | 5.0 | 1 | `inbox` |
| 6 | Unsubscribe email | items | 1.0 | 3 | `unsubscribe` |
| 7 | One-in-one-out | items | 1.0 | 1 | `swap_horiz` |

### 🙏 Devotee

| # | Habit | Unit | Threshold | Target | Icon |
|---|---|---|---|---|---|
| 1 | Pray | sessions | 1.0 | 3 | `self_improvement` |
| 2 | Meditate | minutes | 5.0 | 2 | `self_improvement` |
| 3 | Gratitude journal | entries | 3.0 | 1 | `spa` |
| 4 | Read scripture | minutes | 10.0 | 1 | `menu_book` |
| 5 | Reflection | minutes | 5.0 | 2 | `psychology` |
| 6 | Acts of service | acts | 1.0 | 1 | `volunteer_activism` |
| 7 | Sermon / lecture | minutes | 20.0 | 1 | `headphones` |

### 💪 Health-Conscious

| # | Habit | Unit | Threshold | Target | Icon |
|---|---|---|---|---|---|
| 1 | Drink water | ml | 250.0 | 8 | `water_drop` |
| 2 | Sleep on time | nights | 1.0 | 1 | `bedtime` |
| 3 | Meal prep | minutes | 10.0 | 1 | `kitchen` |
| 4 | No junk food day | days | 1.0 | 1 | `no_food` |
| 5 | Take vitamins | times | 1.0 | 1 | `medication` |
| 6 | Eat vegetables | servings | 1.0 | 3 | `eco` |
| 7 | Walk after meal | minutes | 10.0 | 2 | `directions_walk` |
| 8 | Track meals | meals | 1.0 | 3 | `restaurant` |

### 🎨 Creator

| # | Habit | Unit | Threshold | Target | Icon |
|---|---|---|---|---|---|
| 1 | Sketch / draw | minutes | 15.0 | 1 | `brush` |
| 2 | Music practice | minutes | 15.0 | 2 | `music_note` |
| 3 | Photography | photos | 1.0 | 5 | `photo_camera` |
| 4 | Edit creation | minutes | 15.0 | 1 | `edit` |
| 5 | Share work | posts | 1.0 | 1 | `share` |

### 💰 Saver

| # | Habit | Unit | Threshold | Target | Icon |
|---|---|---|---|---|---|
| 1 | No-spend day | days | 1.0 | 1 | `block` |
| 2 | Track expenses | minutes | 5.0 | 1 | `account_balance` |
| 3 | Cook at home | meals | 1.0 | 2 | `kitchen` |
| 4 | Compare prices | minutes | 5.0 | 1 | `price_check` |
| 5 | Cancel subscription | items | 1.0 | 1 | `money_off` |

### 🤝 Connector

| # | Habit | Unit | Threshold | Target | Icon |
|---|---|---|---|---|---|
| 1 | Call family | calls | 1.0 | 1 | `phone` |
| 2 | Message friend | messages | 1.0 | 3 | `chat` |
| 3 | Plan meetup | minutes | 10.0 | 1 | `event_note` |
| 4 | Send thank-you | notes | 1.0 | 1 | `mail_outline` |
| 5 | Active listen | conversations | 1.0 | 1 | `hearing` |

### 🧭 Adventurer

| # | Habit | Unit | Threshold | Target | Icon |
|---|---|---|---|---|---|
| 1 | New route walk | walks | 1.0 | 1 | `explore` |
| 2 | Try new food | meals | 1.0 | 1 | `restaurant` |
| 3 | Visit new place | places | 1.0 | 1 | `place` |
| 4 | Learn new skill | minutes | 15.0 | 1 | `lightbulb` |
| 5 | Outdoor time | minutes | 30.0 | 1 | `park` |

### 🍳 Chef

| # | Habit | Unit | Threshold | Target | Icon |
|---|---|---|---|---|---|
| 1 | Cook from scratch | meals | 1.0 | 2 | `kitchen` |
| 2 | Try new recipe | recipes | 1.0 | 1 | `menu_book` |
| 3 | Meal prep batch | minutes | 30.0 | 1 | `kitchen` |
| 4 | Use produce before spoiling | items | 1.0 | 3 | `eco` |

## Cross-identity templates

Some templates appear under multiple identities (template reused — same `id`, `name`, `unit`, `threshold`, `target`, `icon`). When the user picks one, all matching identity sections show "Already in your habits."

| Template | Appears under |
|---|---|
| Read book | Reader |
| Read article | Reader, Learner |
| Meal prep / Cook from scratch | Health-Conscious, Saver, Chef |
| Walk | Athlete, Health-Conscious (Walk after meal) |
| Read scripture | Devotee |

(Engineering note: `identityHabitMap` enumerates which template ids belong to which identity. The handful of cross-identity reuses are intentional.)

## Icon constraint

Each row above carries the exact Material Icons glyph to render. The custom-create habit form's icon picker shows a curated set of these glyphs plus a `more_horiz` fallback. User picks the icon when creating a custom habit; engine no longer name-derives.

Icons used across the seed (~40 unique):

```
menu_book, article, description, headphones, code, bug_report, rate_review,
groups, extension, fitness_center, directions_walk, directions_run,
directions_bike, pool, accessibility_new, timer, self_improvement, whatshot,
edit_note, rss_feed, create, edit, spellcheck, email, smart_display, cast,
language, style, podcasts, calculate, movie, cleaning_services, inventory,
delete_sweep, volunteer_activism, inbox, unsubscribe, swap_horiz, spa,
psychology, water_drop, bedtime, kitchen, no_food, medication, eco,
restaurant, brush, music_note, photo_camera, share, block, account_balance,
price_check, money_off, phone, chat, event_note, mail_outline, hearing,
explore, place, lightbulb, park, more_horiz
```

## Propagation

After applying:
- `OnboardStep1` (identity picker) shows 13 identity cards.
- `OnboardStep2` (habit picker) shows the per-identity templates above.
- `HabitList`, `HabitDetail`, `HabitForm` use the explicit per-template icon.
- `TemplateHabitPicker` (post-onboarding "add from template") shows the same set, grouped by identity.
- `IdentityList`, `IdentityDetail`, identity-strip on Today render the 13 identities with their emojis.

Verify by searching `shared.jsx`: `IDENTITIES` should hold exactly 13 entries; `HABIT_TEMPLATES` (or equivalent) should hold exactly 91 rows (some shared via cross-identity map).
