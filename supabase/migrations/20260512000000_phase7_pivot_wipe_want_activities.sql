-- Phase 7 pivot cleanup: wipe accumulated want_activities rows.
--
-- Background: prior pivot iterations left duplicate seed rows for users —
-- each prior install that ran reconcile-before-pull pushed a fresh-UUID
-- 14-row set to the server, accumulating across reinstalls. After clean
-- install + login, sync pulls those duplicates back, producing 33+ rows
-- with subtly different unit strings ("cup" vs "cups", "min" vs "minutes").
--
-- Solo dev environment, no production users. Wiping is the cleanest reset.
-- want_logs is truncated again here — any logs the user added since the prior
-- pivot migration would FK-block want_activities deletion otherwise. Solo dev,
-- log loss is acceptable.
--
-- After this migration:
-- - Server has zero want_activities.
-- - Each user's next reconcile (post-pull, per code fix) inserts the canonical
--   14 seeds with fresh per-user UUIDs and pushes them on next sync.

truncate table want_logs;
delete from want_activities;
