package com.trackr.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.ui.graphics.vector.ImageVector

object Routes {
    const val HOME = "home"
    const val SEARCH = "search"
    const val SEARCH_PATTERN = "search?filter={filter}"
    const val MY_LIST = "mylist?status={status}"
    const val MY_LIST_BASE = "mylist"
    const val FRIENDS = "friends"
    const val PROFILE = "profile"
    const val DETAIL = "detail/{source}/{type}/{id}"
    const val FRIEND_PROFILE = "friend/{userId}"
    const val SETTINGS = "settings"
    const val ABOUT = "about"
    const val IMPORT_EXPORT = "import_export"
    const val WRAPPED = "wrapped"
    const val SHARED_LISTS = "shared_lists"
    const val SHARED_LIST = "shared_list/{id}"

    fun detail(source: String, type: String, id: String) = "detail/$source/$type/$id"
    fun search(filter: String? = null) = if (filter == null) SEARCH_PATTERN.replace("?filter={filter}", "") else "search?filter=$filter"
    fun myList(status: String? = null) = if (status == null) MY_LIST_BASE else "mylist?status=$status"
    fun friend(userId: String) = "friend/$userId"
    fun sharedList(id: String) = "shared_list/$id"
}

enum class TopLevel(val route: String, val pattern: String, val label: String, val icon: ImageVector) {
    Home(Routes.HOME, Routes.HOME, "Home", Icons.Outlined.Explore),
    Search(Routes.SEARCH, Routes.SEARCH_PATTERN, "Search", Icons.Outlined.Search),
    MyList(Routes.MY_LIST_BASE, Routes.MY_LIST, "My List", Icons.Outlined.VideoLibrary),
    Friends(Routes.FRIENDS, Routes.FRIENDS, "Friends", Icons.Outlined.Group),
    Profile(Routes.PROFILE, Routes.PROFILE, "Profile", Icons.Outlined.Person),
}
