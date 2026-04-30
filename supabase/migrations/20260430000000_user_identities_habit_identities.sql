-- Phase 5b: multi-identity foundation
-- Two many-to-many join tables connecting users ↔ identities and habits ↔ identities.
-- RLS scopes by ownership.

CREATE TABLE IF NOT EXISTS public.user_identities (
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    identity_id TEXT NOT NULL,
    added_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (user_id, identity_id)
);

CREATE TABLE IF NOT EXISTS public.habit_identities (
    habit_id UUID NOT NULL REFERENCES public.habits(id) ON DELETE CASCADE,
    identity_id TEXT NOT NULL,
    added_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (habit_id, identity_id)
);

CREATE INDEX IF NOT EXISTS idx_user_identities_user ON public.user_identities(user_id);
CREATE INDEX IF NOT EXISTS idx_habit_identities_habit ON public.habit_identities(habit_id);

ALTER TABLE public.user_identities ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.habit_identities ENABLE ROW LEVEL SECURITY;

-- user_identities: any operation only on rows owned by current user.
CREATE POLICY "user_identities_select_own"
    ON public.user_identities FOR SELECT
    USING (auth.uid() = user_id);

CREATE POLICY "user_identities_insert_own"
    ON public.user_identities FOR INSERT
    WITH CHECK (auth.uid() = user_id);

CREATE POLICY "user_identities_update_own"
    ON public.user_identities FOR UPDATE
    USING (auth.uid() = user_id);

CREATE POLICY "user_identities_delete_own"
    ON public.user_identities FOR DELETE
    USING (auth.uid() = user_id);

-- habit_identities: scoped via the habits table.
CREATE POLICY "habit_identities_select_own"
    ON public.habit_identities FOR SELECT
    USING (EXISTS (
        SELECT 1 FROM public.habits h
        WHERE h.id = habit_identities.habit_id AND h.user_id = auth.uid()
    ));

CREATE POLICY "habit_identities_insert_own"
    ON public.habit_identities FOR INSERT
    WITH CHECK (EXISTS (
        SELECT 1 FROM public.habits h
        WHERE h.id = habit_identities.habit_id AND h.user_id = auth.uid()
    ));

CREATE POLICY "habit_identities_update_own"
    ON public.habit_identities FOR UPDATE
    USING (EXISTS (
        SELECT 1 FROM public.habits h
        WHERE h.id = habit_identities.habit_id AND h.user_id = auth.uid()
    ));

CREATE POLICY "habit_identities_delete_own"
    ON public.habit_identities FOR DELETE
    USING (EXISTS (
        SELECT 1 FROM public.habits h
        WHERE h.id = habit_identities.habit_id AND h.user_id = auth.uid()
    ));
