# Airing notifications – design

Date: 2026-10-03

## Goal
Show when the next episode (TV/anime) or release (movie) of a tracked title drops, and send a local notification at that moment.

## Decisions (agreed)
- **Who gets notified:** every title with status *Watching* automatically; *Plan to Watch* only if the per-entry bell (`notify`) is on. Master on/off switch in Settings.
- **When:** at air time only. No heads-up, no digest.
- **Approach:** local-only. WorkManager refreshes air dates, AlarmManager fires exact alarms, a BroadcastReceiver posts the notification. No server, no FCM. Server push can be added later without UI changes.
- **Time precision:** AniList gives exact timestamps (`precision = TIME`). TMDB gives dates only, so fire at 09:00 local (`precision = DATE`). Movies use the release date the same way.

## Data
- New Room table `airing_schedule`: `mediaId`, `source`, `episode` (null for movies), `airAt` (epoch ms), `precision` (TIME|DATE), `notified`.
- `list_entries` gains boolean `notify` (default false) in Room and Supabase (new migration), synced with the existing LWW logic.
- AniList query adds `nextAiringEpisode { airingAt episode }`. TMDB mapper reads `next_episode_to_air.air_date` (TV) and `release_date` (movies). Both map to a `NextAiring` domain model.
- Eligibility is computed from list status + `notify`, never stored.

## Components
- `AiringRefreshWorker`: periodic (6h) plus one-off on list change. Fetches next airing for eligible titles, upserts `airing_schedule`, deletes rows for ineligible titles. Uses the existing AniList rate limiter and TMDB `TtlCache`.
- `AiringScheduler`: reconciles the table with `AlarmManager`, one `setExactAndAllowWhileIdle` alarm per upcoming row, row id as request code, so it is idempotent (moved = replaced, ineligible = cancelled). Falls back to inexact `setAndAllowWhileIdle` when exact alarms are not granted.
- `AiringReceiver`: posts the notification ("Frieren · Episode 12 is out" / "<Movie> is out today"), deep-links to Detail, sets `notified` so refreshes never re-fire. Also handles `BOOT_COMPLETED` to re-run the scheduler.
- One notification channel: "New episodes & releases".

## UI
- **Detail:** "Next episode: Ep 12 · Fri 18:30 (in 2d 4h)" / "Releases Oct 24". Bell toggle for Plan to Watch; Watching shows the bell on.
- **My List:** "Airs in 2d" chip on Watching cards; optional "Upcoming" sort.
- **Home (stretch):** "Dropping this week" row from `airing_schedule`.
- **Settings:** master switch, link to system notification settings, exact-alarm permission prompt when needed.
- **Permissions:** `POST_NOTIFICATIONS` requested in context (first Watching track or bell tap), not at launch. If denied, dates still show but the toggle is hidden.

## Error handling
- Network/API failure in the worker: keep existing rows, retry on the next run (WorkManager backoff).
- Schedule shifts: corrected by the next refresh (up to 6h lag), the old alarm is replaced.
- Missing air date (finished/unannounced): no row, no alarm.

## Testing
- Unit: AniList/TMDB mappers, eligibility rules, scheduler diff (new / moved / cancelled / already notified) behind a fake `AlarmManager` wrapper.
- Visual (Roborazzi): Detail airing line, My List chip.
- Manual on device: debug-only short-fuse alarm trigger.

## Out of scope
Server push/FCM, daily digest, heads-up reminders, per-episode history, calendar view.
