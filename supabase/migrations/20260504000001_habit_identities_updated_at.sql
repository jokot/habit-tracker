-- Add updated_at to habit_identities for cross-device sync watermark.
-- Soft-unlinks (effective_to) need a watermark column so other devices can pull
-- them via WHERE updated_at > since. added_at does not advance on UPDATE.

ALTER TABLE public.habit_identities
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT now();

-- Backfill existing rows: updated_at = added_at
UPDATE public.habit_identities SET updated_at = added_at WHERE updated_at = added_at OR updated_at IS NULL;

-- Auto-stamp on row modification using the existing touch_updated_at()
-- function defined in 20260423000000_sync_hardening.sql.
DROP TRIGGER IF EXISTS habit_identities_touch_updated_at ON public.habit_identities;
CREATE TRIGGER habit_identities_touch_updated_at
    BEFORE UPDATE ON public.habit_identities
    FOR EACH ROW EXECUTE FUNCTION touch_updated_at();
