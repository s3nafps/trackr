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

## Design system (Stitch = visual source of truth)
- Tokens: the real design uses the **frontmatter/Tailwind M3 tokens** (surface #111317, primary #CABEFF, primary-container #947DFF, ...) in `ui/theme/Color.kt`; DESIGN.md prose gives status colours (watching sky, completed emerald, plan violet, dropped rose), rating amber #FFB547 and brand violet #7C5CFF. Type = Inter (`Type.kt`), radii in `Shape.kt` (cards 16, sheets 24, pills full). Screens never hardcode colours.
- Reusable components in `ui/components`: TrackrChip, MediaTypePill, StatusBadge, RatingBadge/ScorePill, TrackrProgressBar, PosterImage/PosterCard, ShimmerBox + skeletons, EmptyState/ErrorState, TrackrTopBar, UserAvatar, SectionHeader, SegmentedControl, StatTile, BrandLogo.
- Bottom nav: container surface-container-lowest, 20% primary indicator pill.

## Stitch screen → Compose mapping
| Stitch folder | Compose screen | Status |
|---|---|---|
| trackr_login | ui/screens/auth/LoginScreen (+ UsernameScreen, same language) | [x] (no fake stats/email/guest: not real features) |
| trackr_home_discover | ui/screens/home/HomeScreen | [x] (notification bell omitted: no feature) |
| trackr_search | ui/screens/search/SearchScreen | [x] ("People" chip + niche-syntax promo omitted) |
| trackr_media_details_rating_sheet | ui/screens/detail/DetailScreen + components/TrackSheet | [x] (favourite/custom-collection buttons omitted; "Rewatch count" replaced by episode stepper per brief) |
| trackr_my_list | ui/screens/mylist/MyListScreen | [x] ("Import Watchlists" promo omitted: not a feature) |
| trackr_friends_activity | ui/screens/friends/FriendsScreen | [x] (likes/comments/reactions omitted: no backing feature) |
| trackr_friend_profile | ui/screens/friends/FriendProfileScreen | [x] (followers/bio/location/'Plan Watch Party' omitted) |
| trackr_profile_stats | ui/screens/profile/ProfileScreen (+ SettingsScreens.kt) | [x] (genre chart → status breakdown, since genres aren't stored; notifications row omitted) |
| trackr_app_icon | res/drawable/ic_launcher_foreground + BrandLogo | [x] |
Screens absent from export (Settings, About, username setup, add-friend dialog) follow the same language.

## Visual verification (no emulator)
`app/src/test/.../visual/VisualTest.kt` renders stateless `*Content` composables with fake data via Robolectric+Roborazzi to `app/build/outputs/roborazzi/*.png`:
`./gradlew testDebugUnitTest -Proborazzi.test.record=true`, then compare PNGs with `stitch/**/screen.png`. Network images show as placeholders there.

## Progress
- [x] M1 SDK + skeleton + theme + bottom-nav navigation builds
- [x] M2 Supabase schema + RLS + tests (project ref xxmosmbtgojzhvzgnxuf, applied via MCP; SQL in supabase/migrations, test in supabase/tests/rls_test.sql — passes, ends with intentional exception RLS_TESTS_PASSED to roll back)
- [~] M3 Google login: code done (Credential Manager + hashed nonce -> Supabase signInWithIdToken, session persisted/auto-refresh, username setup, sign-out hook). BLOCKED on user: GOOGLE_WEB_CLIENT_ID + Google Cloud Android OAuth client (SHA-1 DA:E5:D0:52:C0:24:25:DF:A8:14:DD:B9:E8:FC:45:78:8F:5F:E1:40) + Supabase Google provider. End-to-end can't be verified on this headless VPS; verify on device.
- [x] M4 TMDB (Retrofit) + AniList (Apollo, 80/min rate limiter, 429 retry) clients, mappers → unified MediaItem, MediaRepository (5-min TtlCache + stale fallback, partial search results), Home, Search (debounce, recents in DataStore, trending when blank), Detail + TrackSheet. Room ListRepository + ListSyncer (LWW) + WorkManager already in place (needed by the sheet). 16 unit tests.
- [x] M5 My List (tabs w/ counts, type filter, sort, grid/list, +1 Ep, edit sheet, remove, empty states, pull-to-refresh sync). Offline-first Room → Supabase, LWW by updated_at, tombstones, WorkManager (on-change + 6h periodic). 34 unit tests incl. sync merge rules.
- [x] M6 Friends (add by username/invite code, pending accept/decline, friends list w/ what they watch, activity feed from friend_activity, friend profile + Watch Together = Plan-to-Watch overlap), Profile (stats, status breakdown + rating distribution charts, edit username, invite code copy/share, theme, sign out), Settings, About (TMDB + AniList attribution). Sign-out syncs, signs out, wipes local Room. `ProfileRepository.me` is the single source for the signed-in profile.
- [x] M7 R8 rules (serializers/Retrofit/Apollo kept; verified in dex), release APK `app/build/outputs/apk/release/trackr-release.apk` signed with trackr.keystore (SHA-1 verified), 52 unit tests, lint clean, nav arg fix (Explore/See all), README written. REMAINING for user: GOOGLE_WEB_CLIENT_ID + Google/Supabase provider setup, then on-device smoke test.
