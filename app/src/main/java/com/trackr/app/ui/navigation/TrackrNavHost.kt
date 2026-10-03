package com.trackr.app.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.trackr.app.data.repository.SearchFilter
import com.trackr.app.domain.model.MediaItem
import com.trackr.app.domain.model.MediaType
import com.trackr.app.ui.screens.detail.DetailScreen
import com.trackr.app.ui.screens.home.HomeScreen
import com.trackr.app.ui.screens.mylist.MyListScreen
import com.trackr.app.ui.screens.search.SearchScreen

private fun NavHostController.goTop(route: String) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}

@Composable
fun TrackrNavHost(onSignOut: () -> Unit = {}) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val dest = entry?.destination
    val showBar = TopLevel.entries.any { t -> dest?.hierarchy?.any { it.route == t.pattern } == true }

    fun openDetail(item: MediaItem) = nav.navigate(Routes.detail(item.source.key, item.type.key, item.externalId))

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (showBar) NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest, tonalElevation = Dp.Hairline) {
                TopLevel.entries.forEach { item ->
                    val selected = dest?.hierarchy?.any { it.route == item.pattern } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = { nav.goTop(item.route) },
                        icon = { Icon(item.icon, item.label) },
                        label = { Text(item.label) },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.onBackground,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    )
                }
            }
        },
    ) { padding ->
        NavHost(nav, startDestination = Routes.HOME, modifier = Modifier.padding(padding)) {
            composable(Routes.HOME) {
                HomeScreen(
                    onOpenDetail = ::openDetail,
                    onOpenEntry = { s, t, id -> nav.navigate(Routes.detail(s, t, id)) },
                    onSeeAllWatching = { nav.goTop(Routes.myList("watching")) },
                    onExplore = { type ->
                        nav.goTop(Routes.search(when (type) { MediaType.MOVIE -> SearchFilter.MOVIES; MediaType.TV -> SearchFilter.TV; MediaType.ANIME -> SearchFilter.ANIME; null -> SearchFilter.ALL }.name))
                    },
                    onOpenProfile = { nav.goTop(Routes.PROFILE) },
                )
            }
            composable(
                Routes.SEARCH_PATTERN,
                arguments = listOf(navArgument("filter") { type = NavType.StringType; nullable = true; defaultValue = null }),
            ) { be ->
                val f = be.arguments?.getString("filter")?.let { runCatching { SearchFilter.valueOf(it) }.getOrNull() }
                SearchScreen(initialFilter = f, onOpenDetail = ::openDetail, onOpenProfile = { nav.goTop(Routes.PROFILE) })
            }
            composable(
                Routes.DETAIL,
                arguments = listOf(
                    navArgument("source") { type = NavType.StringType },
                    navArgument("type") { type = NavType.StringType },
                    navArgument("id") { type = NavType.StringType },
                ),
            ) { DetailScreen(onBack = { nav.popBackStack() }) }
            composable(
                Routes.MY_LIST,
                arguments = listOf(navArgument("status") { type = NavType.StringType; nullable = true; defaultValue = null }),
            ) {
                MyListScreen(
                    onOpenEntry = { e -> nav.navigate(Routes.detail(e.source.key, e.mediaType.key, e.externalId)) },
                    onOpenProfile = { nav.goTop(Routes.PROFILE) },
                    onDiscover = { nav.goTop(Routes.SEARCH) },
                )
            }
            composable(Routes.FRIENDS) { Box(Modifier.fillMaxSize(), Alignment.Center) { Text("Friends") } }
            composable(Routes.PROFILE) { Box(Modifier.fillMaxSize(), Alignment.Center) { Text("Profile") } }
        }
    }
}
