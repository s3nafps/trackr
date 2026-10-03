# Trackr – CLAUDE.md

Android app (Kotlin/Compose/M3) for tracking movies, TV and anime with friends. Package `com.trackr.app`, minSdk 26, target/compile 35.

## Environment (headless VPS)
- JDK 17, Android SDK at `/opt/android-sdk` (platform 35, build-tools 35.0.0 + 34), Gradle wrapper 8.10.2.
- `export ANDROID_HOME=/opt/android-sdk` (also in ~/.bashrc). `local.properties` has `sdk.dir` + secrets.
- Secrets live in `local.properties` (gitignored): TMDB_READ_TOKEN, SUPABASE_URL, SUPABASE_ANON_KEY, GOOGLE_WEB_CLIENT_ID → BuildConfig. Never print/commit them.
- Single keystore `trackr.keystore` + `keystore.properties` (both gitignored) signs debug AND release so SHA-1 is stable.
- Stitch design export is unzipped in `stitch/` (gitignored); tokens in `stitch/**/cinematic_dark/DESIGN.md`.
- AniList needs no auth (public GraphQL); the AniList client id/secret are not used.

## Stack
Compose + M3, single activity, MVVM + Hilt, Flow, Navigation Compose, supabase-kt (auth, postgrest), Retrofit+kotlinx.serialization (TMDB v3, Bearer v4 token), Apollo Kotlin (AniList), Coil, Room, WorkManager, Credential Manager.

## Layout
`app/src/main/java/com/trackr/app/{data(remote,local,repository),domain,ui(theme,navigation,components,screens)}`

## Decisions
- Dark default (Cinematic Dark palette), light + system supported via `ThemeMode`.
- Inter variable font bundled in res/font (no GMS font provider).
- Release APK renamed to `trackr-release.apk`.

## Supabase
- Tables: profiles (extra col `username_set` = false until user picks a username), list_entries, friendships; view `friend_activity` (security_invoker). `are_friends(a,b)` security-definer helper only answers for the caller.
- Signup trigger builds profile from Google name/avatar. Anon role has no table access.

## Progress
- [x] M1 SDK + skeleton + theme + bottom-nav navigation builds
- [x] M2 Supabase schema + RLS + tests (project ref xxmosmbtgojzhvzgnxuf, applied via MCP; SQL in supabase/migrations, test in supabase/tests/rls_test.sql — passes, ends with intentional exception RLS_TESTS_PASSED to roll back)
- [~] M3 Google login: code done (Credential Manager + hashed nonce -> Supabase signInWithIdToken, session persisted/auto-refresh, username setup, sign-out hook). BLOCKED on user: GOOGLE_WEB_CLIENT_ID + Google Cloud Android OAuth client (SHA-1 DA:E5:D0:52:C0:24:25:DF:A8:14:DD:B9:E8:FC:45:78:8F:5F:E1:40) + Supabase Google provider. End-to-end can't be verified on this headless VPS; verify on device.
- [ ] M4 TMDB + AniList, Home, Search, Detail
- [ ] M5 My List + Room + sync
- [ ] M6 Friends, activity, profile, settings
- [ ] M7 Polish, tests, release
