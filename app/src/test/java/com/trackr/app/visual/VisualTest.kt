package com.trackr.app.visual

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.trackr.app.data.repository.SearchFilter
import com.trackr.app.domain.model.CastMember
import com.trackr.app.domain.model.ListEntry
import com.trackr.app.domain.model.ListStatus
import com.trackr.app.domain.model.Load
import com.trackr.app.domain.model.MediaDetail
import com.trackr.app.domain.model.MediaItem
import com.trackr.app.domain.model.MediaSource
import com.trackr.app.domain.model.MediaType
import com.trackr.app.domain.model.SeasonInfo
import com.trackr.app.ui.screens.auth.AuthUiState
import com.trackr.app.ui.screens.auth.LoginScreen
import com.trackr.app.ui.screens.detail.DetailContent
import com.trackr.app.ui.screens.detail.DetailUiState
import com.trackr.app.ui.screens.home.HomeContent
import com.trackr.app.ui.screens.home.HomeSection
import com.trackr.app.ui.screens.home.HomeUiState
import com.trackr.app.domain.model.ActivityEntry
import com.trackr.app.domain.model.Friend
import com.trackr.app.domain.model.FriendRequest
import com.trackr.app.domain.model.Profile
import com.trackr.app.domain.model.StatsCalculator
import com.trackr.app.ui.screens.friends.FriendData
import com.trackr.app.ui.screens.friends.FriendProfileContent
import com.trackr.app.ui.screens.friends.FriendProfileUiState
import com.trackr.app.ui.screens.friends.FriendsActions
import com.trackr.app.ui.screens.friends.FriendsContent
import com.trackr.app.ui.screens.friends.FriendsUiState
import com.trackr.app.ui.screens.profile.ProfileContent
import com.trackr.app.ui.screens.profile.ProfileUiState
import com.trackr.app.ui.screens.mylist.ListSort
import com.trackr.app.ui.screens.mylist.MyListContent
import com.trackr.app.ui.screens.mylist.MyListUiState
import com.trackr.app.ui.screens.search.SearchActions
import com.trackr.app.ui.screens.search.SearchContent
import com.trackr.app.ui.screens.search.SearchUiState
import com.trackr.app.ui.theme.TrackrTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Renders screens headlessly to PNG (app/build/outputs/roborazzi) to compare against the Stitch screenshots. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class VisualTest {
    @get:Rule val rule = createComposeRule()

    private fun shot(name: String, content: @Composable () -> Unit) {
        rule.setContent {
            TrackrTheme { Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { content() } }
        }
        rule.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    private fun item(id: String, title: String, type: MediaType, year: Int, score: Double, genre: String, eps: Int? = null, sub: String? = null) =
        MediaItem(if (type == MediaType.ANIME) MediaSource.ANILIST else MediaSource.TMDB, id, type, title, null, year = year, score = score,
            genres = listOf(genre), totalEpisodes = eps, runtimeMinutes = if (type == MediaType.MOVIE) 166 else null, subtitle = sub,
            overview = "Set in a world where consciousness is transferable, a former soldier is revived.")

    private val movies = listOf(item("1", "Oppenheimer", MediaType.MOVIE, 2023, 8.6, "Biopic"), item("2", "Dune: Part Two", MediaType.MOVIE, 2024, 8.4, "Sci-Fi"), item("3", "Across the Spider-Verse", MediaType.MOVIE, 2023, 8.8, "Animation"))
    private val tv = listOf(item("4", "The Last of Us", MediaType.TV, 2023, 8.8, "Drama", 9), item("5", "Shōgun", MediaType.TV, 2024, 8.7, "Drama", 10))
    private val anime = listOf(item("6", "Chainsaw Man", MediaType.ANIME, 2022, 8.5, "Action", 12, "MAPPA · TV"), item("7", "Solo Leveling", MediaType.ANIME, 2024, 8.3, "Fantasy", 12))
    private val airing = anime.map { it.copy(airingEpisode = 11, airingAtEpoch = System.currentTimeMillis() / 1000 + 7200) }

    private fun entry(id: String, title: String, type: MediaType, status: ListStatus, progress: Int, total: Int?) =
        ListEntry(MediaSource.TMDB, id, type, title, null, null, status, 9, progress, total, System.currentTimeMillis())

    @Test fun login() = shot("login") { LoginScreen(AuthUiState(), onGoogle = {}) }

    @Test fun home() = shot("home") {
        HomeContent(
            HomeUiState(
                sections = mapOf(HomeSection.MOVIES to Load.Success(movies), HomeSection.TV to Load.Success(tv), HomeSection.ANIME to Load.Success(anime), HomeSection.AIRING to Load.Success(airing)),
                continueWatching = listOf(entry("4", "Severance", MediaType.TV, ListStatus.WATCHING, 4, 10), entry("5", "Frieren", MediaType.ANIME, ListStatus.WATCHING, 22, 28)),
                listKeys = setOf("tmdb:1"), streak = 14,
            ),
            avatarUrl = null, username = "Alex", onRefresh = {}, onRetry = {}, onQuickAdd = {}, onPlusOne = {}, onOpenDetail = {},
            onOpenEntry = { _, _, _ -> }, onSeeAllWatching = {}, onExplore = {}, onOpenProfile = {},
        )
    }

    @Test fun search() = shot("search") {
        val results = listOf(item("1", "Arcane: League of Legends", MediaType.TV, 2021, 9.0, "Animation", 9), item("2", "Cyberpunk: Edgerunners", MediaType.ANIME, 2022, 8.6, "Sci-Fi", 10, "Studio Trigger"), item("3", "Blade Runner 2049", MediaType.MOVIE, 2017, 8.0, "Sci-Fi"))
        SearchContent(
            SearchUiState(query = "Arcane", filter = SearchFilter.ALL, results = Load.Success(results), entries = mapOf("tmdb:1" to entry("1", "Arcane", MediaType.TV, ListStatus.WATCHING, 4, 9))),
            SearchActions({}, {}, {}, {}, {}, {}, { _, _ -> }, {}), onOpenDetail = {}, onOpenProfile = {}, avatarUrl = null,
        )
    }

    @Test fun searchBlank() = shot("search_blank") {
        SearchContent(
            SearchUiState(query = "", suggestions = Load.Success(movies + tv), recents = listOf("Interstellar", "Arcane", "Attack on Titan", "Hayao Miyazaki")),
            SearchActions({}, {}, {}, {}, {}, {}, { _, _ -> }, {}), onOpenDetail = {}, onOpenProfile = {}, avatarUrl = null,
        )
    }

    @Test fun detail() = shot("detail") {
        val d = MediaDetail(
            item("2", "Dune: Part Two", MediaType.MOVIE, 2024, 8.6, "Sci-Fi").copy(genres = listOf("Sci-Fi", "Adventure", "Drama", "Action"),
                overview = "Paul Atreides unites with Chani and the Fremen while seeking revenge against the conspirators who destroyed his family. Facing a choice between the love of his life and the fate of the known universe, he endeavors to prevent a terrible future only he can foresee."),
            tagline = "Long live the fighters.", status = "Released", voteCount = 420000, certification = "PG-13",
            cast = listOf(CastMember("Timothée Chalamet", "Paul Atreides", null), CastMember("Zendaya", "Chani", null), CastMember("Rebecca Ferguson", "Jessica", null)),
            seasons = listOf(SeasonInfo(1, "Season 1", 10, 2019, null)),
        )
        DetailContent(d, DetailUiState(Load.Success(d), entry("2", "Dune", MediaType.MOVIE, ListStatus.PLAN_TO_WATCH, 0, 1), emptyList()), {}, {})
    }

    @Test fun detailAiring() = shot("detail_airing") {
        val now = 1_792_000_000_000L
        val d = MediaDetail(
            item("3", "Frieren: Beyond Journey's End", MediaType.ANIME, 2023, 9.1, "Fantasy").copy(
                genres = listOf("Fantasy", "Adventure"), airingEpisode = 12, airingAtEpoch = now / 1000 + 2 * 86_400 + 4 * 3_600,
                overview = "The adventure is over but life goes on for an elf mage."),
            status = "Releasing",
        )
        DetailContent(
            d, DetailUiState(Load.Success(d), entry("3", "Frieren", MediaType.ANIME, ListStatus.WATCHING, 11, 28), emptyList()), {}, {},
            bell = com.trackr.app.ui.screens.detail.BellState.On, nowMillis = now,
        )
    }

    private fun myList(grid: Boolean, status: ListStatus = ListStatus.WATCHING) {
        val items = listOf(
            entry("1", "Arcane", MediaType.TV, ListStatus.WATCHING, 4, 9), entry("2", "Severance", MediaType.TV, ListStatus.WATCHING, 4, 10),
            entry("3", "Frieren: Beyond Journey's End", MediaType.ANIME, ListStatus.WATCHING, 22, 28), entry("4", "Dune: Prophecy", MediaType.TV, ListStatus.WATCHING, 5, 6),
        )
        shot(if (grid) "mylist_grid" else "mylist") {
            MyListContent(
                MyListUiState(status = status, grid = grid, items = items, total = 142, sort = ListSort.RECENT,
                    airsIn = mapOf("tmdb:2" to "Airs in 5h", "tmdb:3" to "Airs in 2d"),
                    statusCounts = mapOf(ListStatus.WATCHING to 8, ListStatus.COMPLETED to 84, ListStatus.PLAN_TO_WATCH to 30, ListStatus.DROPPED to 20),
                    typeCounts = mapOf(null to 8, MediaType.MOVIE to 2, MediaType.TV to 3, MediaType.ANIME to 3)),
                null, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, { _, _, _, _ -> },
            )
        }
    }

    @Test fun myListList() = myList(false)
    @Test fun myListGrid() = myList(true)
    @Test fun myListEmpty() = shot("mylist_empty") {
        MyListContent(MyListUiState(status = ListStatus.DROPPED, total = 0), null, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, { _, _, _, _ -> })
    }

    private fun act(id: String, user: String, title: String, type: MediaType, status: ListStatus, rating: Int?, p: Int = 0, total: Int? = null, minutesAgo: Int = 15) =
        ActivityEntry(id, "u$user", user, null, MediaSource.TMDB, id, type, title, null, status, rating, p, total, System.currentTimeMillis() - minutesAgo * 60_000L)

    private val noActions = FriendsActions({}, { _, _ -> }, {}, {}, {}, {}, {}, {}, {})

    @Test fun friends() = shot("friends") {
        val sara = Profile("us", "sara"); val julian = Profile("uj", "julian_films")
        FriendsContent(
            FriendsUiState(
                activity = Load.Success(listOf(
                    act("1", "sara", "Attack on Titan: The Final Chapters", MediaType.ANIME, ListStatus.COMPLETED, 10),
                    act("2", "marcus", "Severance", MediaType.TV, ListStatus.WATCHING, null, 1, 10, 240),
                )),
                friends = Load.Success(listOf(Friend("f1", sara, listOf(act("2", "sara", "Shōgun", MediaType.TV, ListStatus.WATCHING, null, 3, 10))))),
                pending = listOf(FriendRequest("r1", julian), FriendRequest("r2", Profile("um", "minht"))),
            ),
            null, {}, {}, {}, noActions,
        )
    }

    @Test fun friendProfile() = shot("friend_profile") {
        val theirs = listOf(
            entry("1", "Interstellar", MediaType.MOVIE, ListStatus.WATCHING, 0, 1), entry("2", "Spirited Away", MediaType.MOVIE, ListStatus.COMPLETED, 1, 1),
            entry("3", "Pluto", MediaType.ANIME, ListStatus.PLAN_TO_WATCH, 0, 8),
        )
        val mine = listOf(entry("3", "Pluto", MediaType.ANIME, ListStatus.PLAN_TO_WATCH, 0, 8))
        FriendProfileContent(
            FriendProfileUiState(Load.Success(FriendData(Profile("us", "sara"), theirs, "f1")), StatsCalculator.compute(theirs), StatsCalculator.overlap(mine, theirs)),
            {}, {}, {}, {},
        )
    }

    @Test fun profile() = shot("profile") {
        val entries = listOf(
            entry("1", "A", MediaType.MOVIE, ListStatus.COMPLETED, 1, 1), entry("2", "B", MediaType.TV, ListStatus.WATCHING, 8, 10),
            entry("3", "C", MediaType.ANIME, ListStatus.COMPLETED, 12, 12), entry("4", "D", MediaType.ANIME, ListStatus.PLAN_TO_WATCH, 0, 12),
        ).mapIndexed { i, e -> e.copy(rating = listOf(8, 9, 9, 10)[i]) }
        ProfileContent(
            ProfileUiState(me = Profile("me", "alexrivera", true, null, "TRACKR-ALEX"), stats = StatsCalculator.compute(entries)),
            {}, {}, {}, {}, {},
        )
    }
}
