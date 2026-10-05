# Trackr

[![CI](https://github.com/s3nafps/trackr/actions/workflows/ci.yml/badge.svg)](https://github.com/s3nafps/trackr/actions/workflows/ci.yml)
[![Latest release](https://img.shields.io/github/v/release/s3nafps/trackr?label=download)](https://github.com/s3nafps/trackr/releases/latest)

Trackr is an Android app for keeping track of the movies, TV shows and anime you watch, together with a small group of
friends. Log what you're watching, rate it, tick off episodes, and see what your friends are up to. Movies and TV come from
TMDB, anime from AniList, and everything syncs through Supabase, so your list works offline and follows you across devices.

Kotlin · Jetpack Compose (Material 3, "Cinematic Dark" from the Stitch design) · Hilt · Room · WorkManager ·
Supabase (Auth + Postgres/RLS) · TMDB (REST) · AniList (GraphQL).

## Download
Get the latest APK from **[Releases](https://github.com/s3nafps/trackr/releases/latest)** (Android 8.0+), open it on your
phone and allow the install when Android asks. Sign-in uses Google; while the OAuth consent screen is in *Testing* mode
only accounts added as test users can sign in.

## Screenshots
<p>
  <img src="docs/screenshots/login.png" width="200" alt="Login">
  <img src="docs/screenshots/home.png" width="200" alt="Home / Discover">
  <img src="docs/screenshots/search.png" width="200" alt="Search">
  <img src="docs/screenshots/detail.png" width="200" alt="Details">
</p>
<p>
  <img src="docs/screenshots/detail_airing.png" width="200" alt="Details with airing info">
  <img src="docs/screenshots/mylist.png" width="200" alt="My List">
  <img src="docs/screenshots/friends.png" width="200" alt="Friends activity">
  <img src="docs/screenshots/friend_profile.png" width="200" alt="Friend profile">
  <img src="docs/screenshots/profile.png" width="200" alt="Profile and stats">
</p>

_Rendered headlessly with fake sample data (Robolectric + Roborazzi), so poster images appear as placeholders._

## Features
- **Discover & search**: continue watching, trending movies/TV/anime, airing this week; search TMDB + AniList with
  filters, debounce and recent searches.
- **Title pages**: trailer, where to watch in your country (stream / free / rent / buy, via JustWatch), cast, seasons,
  related anime (prequels, sequels…), more like this, friends who watched, and a rating/progress sheet.
- **My List**: Watching / Completed / Plan to Watch / Dropped tabs, grid or list, sort and filter, +1 episode;
  offline-first with last-write-wins sync; new-episode alerts.
- **Import & export**: AniList (username), MyAnimeList (export file), Letterboxd (export zip), Trackr JSON backup;
  export to JSON or CSV.
- **Friends**: add by username or invite code, activity feed with reactions and comments, friend profiles with
  Watch Together (+ "Pick one"), recommend titles to friends and a "Recommended to you" inbox.
- **Shared watchlists**: build lists with friends and let "Pick for us" choose tonight's title.
- **Year in review**: titles finished, hours, top rated and busiest month, shareable as an image.
- **"Up next" widget**: airing this week and continue watching on the home screen, with +1.
- **Links**: shared TMDB/AniList title links open in Trackr.
- **Profile & settings**: stats and charts, invite code, theme, sign out, delete account; About with TMDB, AniList and
  JustWatch attribution.

## Setup
Requirements: JDK 17, Android SDK (platform 35, build-tools 35). `export ANDROID_HOME=/path/to/sdk`.

1. Create `local.properties` (gitignored) next to `settings.gradle.kts`:
   ```properties
   sdk.dir=/path/to/sdk
   TMDB_READ_TOKEN=<TMDB v4 read access token>
   SUPABASE_URL=https://<project-ref>.supabase.co
   SUPABASE_ANON_KEY=<anon/publishable key>
   GOOGLE_WEB_CLIENT_ID=<Google *Web* OAuth client id>
   ```
2. Supabase: apply `supabase/migrations/*.sql` in order, enable the **Google** provider (web client id + secret; leave
   *Skip nonce checks* off). `supabase/tests/rls_test.sql`, `social_test.sql` and `shared_lists_test.sql` verify RLS; each
   ends with an intentional error (`…_TESTS_PASSED`) so everything it creates is rolled back.
3. Google Cloud: OAuth consent screen (Testing) with your friends' Gmail addresses as test users; a **Web** client
   (redirect `https://<project-ref>.supabase.co/auth/v1/callback`) and an **Android** client for package `com.trackr.app`
   with the SHA-1 of `trackr.keystore`:
   `keytool -list -v -keystore trackr.keystore -alias trackr` (current: `E0:8D:BF:F4:3E:6D:FE:B6:75:47:FD:A9:16:1C:9A:8A:22:FA:9D:4E`).
4. Signing: `trackr.keystore` + `keystore.properties` (both gitignored) sign **debug and release** so the SHA-1 never changes.
   Keep a backup of the keystore – a new one means a new SHA-1 and an uninstall/reinstall.

## Build
```bash
./gradlew assembleDebug                 # debug APK
./gradlew testDebugUnitTest lintDebug   # unit tests + lint
./gradlew assembleRelease               # app/build/outputs/apk/release/trackr-release.apk (R8 + resource shrinking)
adb install -r app/build/outputs/apk/release/trackr-release.apk   # or copy the APK to the phone and sideload
```
CI (`.github/workflows/ci.yml`) runs the unit tests, lint and a debug build on every push and pull request.

Visual check without a device: `./gradlew testDebugUnitTest -Proborazzi.test.record=true` renders the screens to
`app/build/outputs/roborazzi/*.png` (Robolectric) to compare with the Stitch screenshots.

## Releasing
`.github/workflows/release.yml` builds the signed APK and publishes a GitHub release with it attached. One-time setup, under
**Settings → Secrets and variables → Actions → New repository secret**:

| Secret | Value |
|---|---|
| `TMDB_READ_TOKEN`, `SUPABASE_URL`, `SUPABASE_ANON_KEY`, `GOOGLE_WEB_CLIENT_ID` | the same values as in `local.properties` |
| `KEYSTORE_BASE64` | `base64 -w0 trackr.keystore` (macOS: `base64 -i trackr.keystore`) |
| `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` | the values from `keystore.properties` |

Then bump `versionCode`/`versionName` in `app/build.gradle.kts`, add `docs/release-notes/vX.Y.Z.md`, and either push a tag
(`git tag vX.Y.Z && git push origin vX.Y.Z`) or run **Actions → Release → Run workflow** with the tag. The log prints the
APK's signing SHA-1; it must match the one registered for the Android OAuth client.

## Known limitations
- Built and tested headlessly (CI and a VPS), never on an emulator: smoke-test Google sign-in, the R8 release build, the
  widget and network images on a phone before sharing a release.
- Google sign-in needs `GOOGLE_WEB_CLIENT_ID` baked in at build time; without it the button reports "not configured".
- The TMDB token and Supabase anon key are embedded in the APK (normal for client apps; the anon key is protected by RLS). Only
  sideload to people you trust, and rotate the TMDB token if it leaks.
- Only Google accounts listed as OAuth test users can sign in while the consent screen is in *Testing* mode.
- Genre breakdown isn't shown (genres aren't stored in the list); the profile shows a status breakdown and rating distribution.
  Screen time is an estimate (movie 2h, TV 45 min/ep, anime 24 min/ep).
- Sync is pull-on-open/refresh + push after changes (WorkManager, needs network); there is no realtime, so friends' reactions,
  comments and recommendations appear on refresh. Followers, push notifications for social activity and email login are not
  implemented.
- Shared TMDB/AniList links aren't verified App Links (those domains aren't ours): Android 12+ opens them in Trackr only after
  Settings → *Open links in Trackr*.
- Trakt import isn't supported (no standard export; its API needs a registered client id).
- AniList is limited to ~80 requests/minute client-side; heavy browsing may briefly wait.
- Single-device-at-a-time editing is assumed; concurrent edits resolve last-write-wins by `updated_at` (device clocks matter).
