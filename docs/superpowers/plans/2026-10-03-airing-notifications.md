# Airing Notifications Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Show when the next episode/movie of a tracked title drops and fire a local notification at that moment.

**Architecture:** A Room table `airing_schedule` (one "next drop" row per title) is refreshed by a WorkManager job from the existing `MediaRepository` (AniList `nextAiringEpisode`, TMDB `next_episode_to_air` / `release_date`). `AiringScheduler` mirrors the table into exact `AlarmManager` alarms through a small `AlarmClient` interface (testable with a fake); `AiringReceiver` posts the notification. A new `notify` flag on list entries (synced to Supabase) opts Plan-to-Watch titles in.

**Tech Stack:** Kotlin, Room, Hilt (+ HiltWorker), WorkManager, AlarmManager, NotificationManagerCompat, DataStore (`UserPrefs`), Compose M3, supabase-kt, JUnit + Robolectric/Roborazzi (existing).

**Spec:** `docs/superpowers/specs/2026-10-03-airing-notifications-design.md`

## Global Constraints

- Package `com.trackr.app`, minSdk 26, target/compile 35. Code goes under `app/src/main/java/com/trackr/app/{data,domain,ui}` following the existing layout.
- Eligible titles: status *Watching* automatically; *Plan to Watch* only when `notify == true`. Master switch (default ON) in Settings. Completed/Dropped are never eligible.
- Fire at air time only (no heads-up, no digest).
- AniList airings are exact (`precision = TIME`, `airingAt` is epoch **seconds**). TMDB TV episodes and movies are date-only (`precision = DATE`) and fire at **09:00 local**.
- Notification channel id `airing`, name "New episodes & releases".
- Copy: episode → "`<title>` · Episode `<n>` is out"; movie → "`<title>` is out today".
- `POST_NOTIFICATIONS` requested in context (first Watching track or bell tap), never at launch. If denied, dates still show but the bell toggle is hidden.
- Exact alarms via `setExactAndAllowWhileIdle`; fall back to `setAndAllowWhileIdle` when `canScheduleExactAlarms()` is false.
- Refresh: periodic 6h + one-off on list change; AniList calls keep the existing 80/min limiter; no new network dependencies.
- Screens never hardcode colours; reuse `ui/components` (TrackrChip etc.).
- Secrets are never printed or committed. Commit messages end with `Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>`.
- Run tests with `export ANDROID_HOME=/opt/android-sdk && ./gradlew testDebugUnitTest` (single class: `--tests 'com.trackr.app.<pkg>.<Class>'`).

## Review Focus

1. **Stale alarm after the show changes**: a title is removed, completed, dropped or the bell is turned off, and its alarm must be cancelled and its row deleted (Task 5, Task 6).
2. **Episode number shifts / refresh re-fires**: AniList moves the next airing (new time or a new episode number). The old alarm is replaced, and an already-`notified` episode never fires twice, but the following episode does (Task 4, Task 5).
3. **Device was off/offline when the drop passed**: a row whose `airAt` is already past fires immediately if less than 12h old, otherwise it is skipped silently (Task 5).
4. **Reboot and sign-out**: alarms vanish on reboot (restore from the table), and sign-out wipes the table and cancels every alarm (Task 6, Task 7).
5. **Permission denied / no data**: notifications denied means no crash and no bell; a finished or unannounced title (no next date) yields no row (Task 3, Task 4, Task 8).

---

### Task 1: Room v2 – `airing_schedule` table and `notify` column

**Files:**
- Create: `data/local/AiringEntity.kt`, `data/local/AiringDao.kt`
- Modify: `data/local/ListEntryEntity.kt`, `data/local/TrackrDatabase.kt`, `di/DatabaseModule.kt`
- Test: `app/src/test/java/com/trackr/app/data/AiringDaoTest.kt`

**Interfaces:**
- Produces:
  - `@Entity(tableName="airing_schedule", primaryKeys=["source","externalId"]) data class AiringEntity(source: String, externalId: String, mediaType: String, title: String, episode: Int?, airAt: Long /*epoch millis*/, precision: String /*"TIME"|"DATE"*/, notified: Boolean = false)`
  - `ListEntryEntity.notify: Boolean = false` (last constructor param, after `deleted`)
  - `AiringDao`: `suspend fun getAll(): List<AiringEntity>`, `fun observeAll(): Flow<List<AiringEntity>>`, `suspend fun get(source: String, id: String): AiringEntity?`, `suspend fun upsert(e: AiringEntity)`, `suspend fun delete(source: String, id: String)`, `suspend fun markNotified(source: String, id: String)`, `suspend fun clear()`
  - `TrackrDatabase.airingDao()`; `DatabaseModule` provides `AiringDao`.
  - `MIGRATION_1_2` (in `TrackrDatabase.kt` companion/top-level) adds `list_entries.notify INTEGER NOT NULL DEFAULT 0` and creates `airing_schedule`.

- [ ] **Step 1: Write failing test** `AiringDaoTest` (Robolectric in-memory Room, same setup style as `ListRepositoryTest`): `upsert_replaces_row_for_same_title`, `markNotified_sets_flag_only_on_that_row`, `delete_removes_only_that_title`, `clear_empties_table`.
- [ ] **Step 2: Run** `--tests 'com.trackr.app.data.AiringDaoTest'` → FAIL (classes missing).
- [ ] **Step 3: Implement** the entity, DAO, `notify` column, bump `@Database(version = 2)`, add both entities, register `MIGRATION_1_2` in `DatabaseModule` with `.addMigrations(MIGRATION_1_2)`. Keep `fallbackToDestructiveMigration()` as a last resort only for unknown versions, but the 1→2 path must be a real migration so unsynced `dirty` entries survive.
- [ ] **Step 4: Add** `Migration12Test` using `MigrationTestHelper` or a raw SQLite check: a v1 row with `dirty=1` survives migration with `notify=0`. Run both tests → PASS.
- [ ] **Step 5: Commit** `feat: room v2 with airing_schedule and notify column`.

### Task 2: `notify` flag through domain, mapper, DTO, sync and Supabase

**Files:**
- Modify: `domain/model/ListEntry.kt`, `data/mapper/ListEntryMapper.kt`, `data/remote/supabase/ListEntryDto.kt`, `data/repository/ListRepository.kt`, `data/sync/ListSyncer.kt` (only if merge builds entities field-by-field)
- Create: `supabase/migrations/20261003000003_list_entries_notify.sql`
- Test: extend `ListRepositoryTest.kt`, `ListSyncerTest.kt`, `MappersAndUtilTest.kt`

**Interfaces:**
- Produces: `ListEntry.notify: Boolean = false` (last param); `ListEntryDto.notify: Boolean = false` (`@SerialName("notify")`); `suspend fun ListRepository.setNotify(entry: ListEntry, on: Boolean)` (goes through `write`, so it bumps `updatedAt`, sets `dirty`, calls `syncNow`).
- SQL: `alter table public.list_entries add column notify boolean not null default false;` Apply with the Supabase MCP `apply_migration` after the user OKs touching the live project; keep the file in the repo regardless. Existing RLS covers the new column (owner-only), so no policy change; verify by re-running `supabase/tests/rls_test.sql` (it must still end with `RLS_TESTS_PASSED`).

- [ ] **Step 1: Write failing tests:** `setNotify_marks_entry_dirty_and_persists` (ListRepositoryTest), `mapper_roundtrips_notify` (entity↔domain↔dto), `syncer_pushes_and_pulls_notify` (ListSyncerTest: remote newer row with `notify=true` replaces local).
- [ ] **Step 2: Run** the three classes → FAIL.
- [ ] **Step 3: Implement** the fields, mapper lines (all four conversion functions) and `setNotify`. `ListRepository.save()` keeps `notify=false` on new entries; `update()` preserves it via `entry.copy`.
- [ ] **Step 4: Run** → PASS. Write the SQL file.
- [ ] **Step 5: Commit** `feat: sync per-entry notify flag`.

### Task 3: Next-airing data from TMDB and a precision flag

**Files:**
- Modify: `domain/model/Media.kt`, `data/remote/tmdb/TmdbDtos.kt`, `data/mapper/TmdbMapper.kt`, `data/mapper/AniListMapper.kt` (nothing to add if `airingAtEpoch` already flows; verify)
- Test: `MappersAndUtilTest.kt`

**Interfaces:**
- Produces:
  - `MediaItem.airingDateOnly: Boolean = false` (after `airingAtEpoch`). `airingAtEpoch` stays **epoch seconds**.
  - `TmdbDetail.nextEpisodeToAir: TmdbEpisodeStub?` with `@Serializable data class TmdbEpisodeStub(@SerialName("air_date") val airDate: String? = null, @SerialName("episode_number") val episodeNumber: Int? = null)` (`@SerialName("next_episode_to_air")`).
  - `TmdbMapper.localNineAm(date: String, zone: ZoneId = ZoneId.systemDefault()): Long?` → epoch seconds of 09:00 on that ISO date, null if unparsable.
  - `TmdbMapper.toDetail(d, type)` fills `item.airingEpisode/airingAtEpoch/airingDateOnly=true`: TV from `nextEpisodeToAir`; movie from `releaseDate` **only when that date is today or later** (no episode number).

- [ ] **Step 1: Write failing tests:** `nineAm_is_0900_in_given_zone` (e.g. `2026-10-24`, `Europe/Berlin` → `2026-10-24T09:00+02:00`), `unparsable_date_returns_null`, `tv_detail_maps_next_episode`, `movie_in_future_maps_release_date`, `movie_in_past_has_no_airing`, `tv_without_next_episode_has_no_airing` (Review Focus 5). Map "today" via an optional `today: LocalDate = LocalDate.now()` parameter on `toDetail`.
- [ ] **Step 2: Run** → FAIL. **Step 3: Implement.** **Step 4: Run** → PASS.
- [ ] **Step 5: Commit** `feat: map TMDB next episode and movie release dates`.

### Task 4: Eligibility policy and `AiringRepository.refresh()`

**Files:**
- Create: `domain/util/AiringPolicy.kt`, `data/repository/AiringRepository.kt`
- Test: `app/src/test/java/com/trackr/app/data/AiringRepositoryTest.kt`

**Interfaces:**
- Consumes: `AiringDao` (Task 1), `ListEntry.notify` (Task 2), `MediaItem.airing*` (Task 3), `MediaRepository.detail(source, id, type, force = true)`, `ListEntryDao.getAllRaw()`.
- Produces:
  - `object AiringPolicy { fun isEligible(status: ListStatus, notify: Boolean): Boolean }`: WATCHING → true; PLAN → `notify`; else false.
  - `class AiringRepository @Inject constructor(listDao, airingDao, media: MediaRepository, prefs: UserPrefs, scheduler: AiringScheduler)` with:
    - `val upcoming: Flow<List<AiringEntity>>` (from `airingDao.observeAll()`), and `fun forTitle(source: String, id: String): Flow<AiringEntity?>`.
    - `suspend fun refresh(now: Long = System.currentTimeMillis())`: if master switch off → treat nobody as eligible. For each non-deleted eligible entry fetch detail, derive row (`precision = DATE` when `airingDateOnly`, `airAt = airingAtEpoch*1000`). If the same title already has a row with the **same episode** → keep its `notified`; a different episode → `notified=false`. No airing data → delete any existing row. Rows of ineligible/removed titles are deleted. Per-title fetch failure keeps the old row (do not delete). Finally calls `scheduler.apply(upserted, removed)` (Task 5).
    - `suspend fun clearAll()` → cancels all alarms via scheduler and clears the table.

- [ ] **Step 1: Write failing tests** (fake `MediaRepository`/`AiringScheduler` fakes): `watching_title_gets_row`, `plan_without_bell_gets_no_row`, `plan_with_bell_gets_row`, `completed_title_row_deleted_and_alarm_cancelled`, `same_episode_keeps_notified_flag`, `new_episode_resets_notified`, `no_next_airing_deletes_row`, `fetch_failure_keeps_old_row`, `master_switch_off_removes_everything`, `anime_row_precision_is_time_tv_is_date`.
- [ ] **Step 2: Run** → FAIL. **Step 3: Implement** (add `airingEnabled: Flow<Boolean>` + `setAiringEnabled(Boolean)` to `UserPrefs`, default true, key `airing_enabled`). **Step 4: Run** → PASS.
- [ ] **Step 5: Commit** `feat: airing refresh and eligibility policy`.

### Task 5: `AlarmClient` and `AiringScheduler`

**Files:**
- Create: `data/airing/AlarmClient.kt`, `data/airing/AiringScheduler.kt`
- Modify: `di/` (new `AiringModule.kt` binding `AlarmClient` → `AndroidAlarmClient`)
- Test: `app/src/test/java/com/trackr/app/data/AiringSchedulerTest.kt`

**Interfaces:**
- Produces:
  - `interface AlarmClient { fun canScheduleExact(): Boolean; fun set(code: Int, atMillis: Long, exact: Boolean, source: String, externalId: String); fun cancel(code: Int) }`
  - `class AndroidAlarmClient @Inject constructor(@ApplicationContext ctx)` using `AlarmManager` + a `PendingIntent` (explicit `AiringReceiver`, extras `source`, `externalId`, `FLAG_IMMUTABLE | FLAG_UPDATE_CURRENT`, request code = `code`).
  - `class AiringScheduler @Inject constructor(client: AlarmClient, notifier: AiringNotifier?)`: **keep it constructible with only `client` + a `fireNow: (AiringEntity) -> Unit` lambda in the test** (provide via Hilt as the notifier's `post`).
  - `fun requestCode(source: String, externalId: String): Int = "$source:$externalId".hashCode()` (one alarm per title, so this is the stable id).
  - `fun apply(upserted: List<AiringEntity>, removed: List<AiringEntity>, now: Long = System.currentTimeMillis())`: cancel each removed; for each upserted with `notified == false`: if `airAt > now` → `client.set(code, airAt, exact = client.canScheduleExact(), …)`; else if `now - airAt <= 12h` → `fireNow(row)`; else skip. Upserted rows already `notified` → `client.cancel(code)`.
  - `fun cancelAll(rows: List<AiringEntity>)`.

- [ ] **Step 1: Write failing tests** with a `FakeAlarmClient` recording calls: `future_row_sets_exact_alarm`, `exact_not_granted_falls_back_to_inexact`, `moved_time_replaces_alarm_same_code`, `removed_row_cancels_alarm`, `notified_row_cancels_instead_of_setting`, `past_row_under_12h_fires_immediately`, `past_row_over_12h_is_skipped`, `request_code_stable_per_title`.
- [ ] **Step 2: Run** → FAIL. **Step 3: Implement.** **Step 4: Run** → PASS.
- [ ] **Step 5: Commit** `feat: alarm scheduler with fake-able AlarmClient`.

### Task 6: Notification, receiver, boot restore, manifest

**Files:**
- Create: `data/airing/AiringNotifier.kt`, `data/airing/AiringReceiver.kt`, `data/airing/BootReceiver.kt`
- Modify: `app/src/main/AndroidManifest.xml`, `TrackrApp.kt` (create the channel in `onCreate`), `MainActivity.kt` + `ui/navigation/TrackrNavHost.kt` (deep link)
- Test: `app/src/test/java/com/trackr/app/data/AiringNotifierTest.kt`

**Interfaces:**
- Consumes: `AiringDao`, `AiringRepository.refresh()`.
- Produces:
  - `AiringNotifier.createChannel()`, `AiringNotifier.post(row: AiringEntity)`, and `fun message(row: AiringEntity): Pair<String, String>` (title, text; pure, exact copy from Global Constraints).
  - `AiringReceiver` (`@AndroidEntryPoint`, not exported): reads extras, loads the row, **skips if missing, `notified`, or notifications disabled/denied**, posts, then `markNotified`. Uses `goAsync()`.
  - `BootReceiver` (`BOOT_COMPLETED`, plus `MY_PACKAGE_REPLACED`): enqueues `AiringRefreshWorker` one-off (Task 7) and re-applies alarms from the table.
  - Manifest: `<uses-permission POST_NOTIFICATIONS>`, `SCHEDULE_EXACT_ALARM`, `RECEIVE_BOOT_COMPLETED`; both receivers declared `exported="false"` (Boot needs its intent-filter, still `exported="false"`).
  - Tap action: launches `MainActivity` with extras `open_source`, `open_id`, `open_type`; the nav host navigates to the existing Detail route (see `ui/navigation/Routes.kt` for the route builder).

- [ ] **Step 1: Write failing test** `AiringNotifierTest`: `episode_message_copy` ("Frieren · Episode 12 is out"), `movie_message_copy` ("Dune: Part Three is out today"), `episode_null_means_movie_copy`.
- [ ] **Step 2: Run** → FAIL. **Step 3: Implement** everything above. Guard `post` with `NotificationManagerCompat.areNotificationsEnabled()` and a `POST_NOTIFICATIONS` check (Review Focus 5).
- [ ] **Step 4: Run** the test → PASS; `./gradlew assembleDebug` succeeds.
- [ ] **Step 5: Commit** `feat: airing notification, receivers and deep link`.

### Task 7: `AiringRefreshWorker`, triggers, sign-out cleanup

**Files:**
- Create: `data/airing/AiringRefreshWorker.kt`, `data/airing/AiringRefreshScheduler.kt`
- Modify: `TrackrApp.kt` (schedule periodic), `data/repository/ListRepository.kt` (call `refreshNow()` after `write`/`remove`), the sign-out path in `ProfileViewModel`/`AuthRepository` where `clearLocal()` is called
- Test: `app/src/test/java/com/trackr/app/data/AiringRefreshWorkerTest.kt` (use `TestListenableWorkerBuilder`)

**Interfaces:**
- Produces: `@HiltWorker class AiringRefreshWorker(ctx, params, repo: AiringRepository)` → `repo.refresh()`; `Result.retry()` up to 5 attempts, same as `SyncWorker`. `AiringRefreshScheduler.refreshNow()` (unique work `airing-refresh-now`, `ExistingWorkPolicy.REPLACE`, network constraint) and `schedulePeriodic()` (6h, unique `airing-refresh-periodic`, `KEEP`).
- Sign-out: wherever `ListRepository.clearLocal()` is called, also call `AiringRepository.clearAll()` (Review Focus 4).

- [ ] **Step 1: Write failing tests:** `worker_calls_refresh_and_succeeds`, `worker_retries_on_exception_until_5_attempts`, `sign_out_clears_airing_and_cancels_alarms` (fake scheduler asserts `cancelAll`).
- [ ] **Step 2: Run** → FAIL. **Step 3: Implement.** **Step 4: Run** → PASS.
- [ ] **Step 5: Commit** `feat: background airing refresh and sign-out cleanup`.

### Task 8: Detail screen – airing line, bell and permission

**Files:**
- Modify: `ui/screens/detail/DetailViewModel.kt`, `ui/screens/detail/DetailScreen.kt`, `ui/components/TrackSheet.kt` (bell for Plan to Watch), `ui/components/MediaCards.kt` (reuse/extend `airingLabel`)
- Create: `ui/components/NotificationPermission.kt` (a composable helper wrapping `rememberLauncherForActivityResult(RequestPermission)` that is a no-op below API 33)
- Test: `DetailViewModelTest` additions; `visual/VisualTest.kt` additions

**Interfaces:**
- Consumes: `AiringRepository.forTitle`, `ListRepository.setNotify`, `UserPrefs.airingEnabled`.
- Produces: `fun airingLine(item: MediaItem, nowMillis: Long): String?` → `"Next episode: Ep 12 · Fri 18:30 (in 2d 4h)"` for TIME precision, `"Next episode: Ep 12 · Fri, Oct 24"` for DATE, `"Releases Oct 24"` for movies, null with no data. DetailViewModel exposes `bellState: StateFlow<BellState>` where `BellState` is `Hidden | Off | On` (Hidden when status is not Watching/Plan, when permission denied, or master switch off; Watching shows `On` and is non-toggleable).
- Request `POST_NOTIFICATIONS` the first time status becomes Watching via the TrackSheet or the bell is tapped; on denial remember it (just do not re-ask in the same session) and hide the bell.

- [ ] **Step 1: Write failing tests:** `airingLine_time_precision`, `airingLine_date_precision`, `airingLine_movie`, `airingLine_null_without_data`, `bell_hidden_when_completed`, `bell_on_for_watching`, `bell_toggle_calls_setNotify_for_plan`, `bell_hidden_when_permission_denied`.
- [ ] **Step 2: Run** → FAIL. **Step 3: Implement** + a Roborazzi case rendering `DetailContent` with an airing line and the bell; record with `./gradlew testDebugUnitTest -Proborazzi.test.record=true` and eyeball the PNG against the existing detail screen language.
- [ ] **Step 4: Run** unit tests → PASS.
- [ ] **Step 5: Commit** `feat: airing line and notify bell on detail`.

### Task 9: My List chip/sort and Settings

**Files:**
- Modify: `ui/screens/mylist/MyListViewModel.kt`, `ui/screens/mylist/MyListScreen.kt`, `ui/screens/profile/SettingsScreens.kt`, `ui/screens/profile/ProfileViewModel.kt`
- Test: extend `MyListViewModelTest.kt`; visual test for the chip

**Interfaces:**
- Consumes: `AiringRepository.upcoming`, `UserPrefs.airingEnabled/setAiringEnabled`.
- Produces: `MyListViewModel` joins `upcoming` into each Watching card (`airsIn: String?`, e.g. "Airs in 2d" via the `airingLabel` helper) and adds a `SortOption.UPCOMING` (soonest `airAt` first, no-airing last). Settings gets a "New episode alerts" switch bound to `setAiringEnabled` (calls `AiringRefreshScheduler.refreshNow()` after toggling), a row opening `Settings.ACTION_APP_NOTIFICATION_SETTINGS`, and, when `!canScheduleExactAlarms()` on API 31+, a row opening `Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM` ("Allow exact timing").

- [ ] **Step 1: Write failing tests:** `watching_card_gets_airs_in_label`, `plan_card_has_no_label_without_row`, `upcoming_sort_orders_soonest_first_and_none_last`, `toggling_switch_persists_and_refreshes`.
- [ ] **Step 2: Run** → FAIL. **Step 3: Implement** (use `TrackrChip`, no hardcoded colours). **Step 4: Run** → PASS; re-record visual PNG for the chip.
- [ ] **Step 5: Commit** `feat: upcoming chip, sort and notification settings`.

### Task 10: Debug trigger, docs, release checks

**Files:**
- Create: `app/src/debug/java/com/trackr/app/debug/DebugAiring.kt` (debug-only entry that inserts an `AiringEntity` due in 20s for a tracked title and calls `AiringScheduler.apply`; expose as a Settings row only in the debug source set)
- Modify: `CLAUDE.md` (Progress: add M8 line and the airing files to Layout notes), `README.md` (feature note), `app/proguard-rules.pro` only if the release build shows receiver/worker stripping

- [ ] **Step 1:** Add the debug trigger. `./gradlew assembleDebug` succeeds.
- [ ] **Step 2:** `./gradlew testDebugUnitTest lintDebug` → all green (report counts).
- [ ] **Step 3:** `./gradlew assembleRelease` succeeds and the APK is still renamed `trackr-release.apk`; confirm `AiringReceiver`, `BootReceiver` and `AiringRefreshWorker` survive R8 (check dex or manifest merge).
- [ ] **Step 4 (user, on device):** install debug build, track a Watching anime, trigger the 20s alarm, confirm the notification appears and a tap opens that title; deny permission and confirm no crash and no bell; reboot and confirm alarms are restored.
- [ ] **Step 5: Commit** `docs: airing notifications progress` and ask the user before applying `20261003000003_list_entries_notify.sql` to the live Supabase project (if not already done in Task 2).

### Task 11 (optional, stretch): Home "Dropping this week" row

**Files:** Modify `ui/screens/home/HomeViewModel.kt`, `ui/screens/home/HomeScreen.kt`; test in a new `HomeViewModelAiringTest`.

- [ ] Show a horizontal `PosterCard` row from `AiringRepository.upcoming` filtered to the next 7 days and sorted by `airAt`; hide the row when empty. Test `row_empty_when_nothing_in_7_days` and `row_sorted_by_air_time`. Commit `feat: home dropping this week row`.

---

## Self-review notes
- **Spec coverage:** data model → T1/T2/T3; refresh/eligibility → T4; scheduler → T5; receiver/channel/boot/deep link → T6; worker/triggers/sign-out → T7; Detail UI/bell/permissions → T8; My List chip/sort + Settings (master switch, system settings link, exact-alarm prompt) → T9; testing + manual smoke → T10; Home stretch → T11.
- **Deliberate deviation from the spec:** the spec says "row id as request code"; the table has one row per title (key `source+externalId`), so the request code is `hashCode()` of that key.
- **Existing code already helps:** `MediaItem.airingEpisode/airingAtEpoch` and AniList `nextAiringEpisode` already exist, so no GraphQL change is needed.
- **Room:** the DB currently uses `fallbackToDestructiveMigration` at v1, so the plan adds a real 1→2 migration, otherwise upgrading would wipe unsynced local edits.
