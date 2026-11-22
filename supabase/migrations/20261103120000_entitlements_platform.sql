-- PROPOSAL. Do not apply this migration from the Android repo or this pull request.
-- Apply it in the Supabase project only when sync-play-entitlements is ready to deploy.
-- Existing iOS rows stay platform = 'ios'.

alter table public.entitlements
  add column if not exists platform text not null default 'ios';

alter table public.entitlements
  drop constraint if exists entitlements_platform_check;

alter table public.entitlements
  add constraint entitlements_platform_check
  check (platform in ('ios', 'android'));
