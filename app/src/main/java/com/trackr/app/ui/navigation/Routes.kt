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
    const val MY_LIST = "mylist"
    const val FRIENDS = "friends"
    const val PROFILE = "profile"
}

enum class TopLevel(val route: String, val label: String, val icon: ImageVector) {
    Home(Routes.HOME, "Home", Icons.Outlined.Explore),
    Search(Routes.SEARCH, "Search", Icons.Outlined.Search),
    MyList(Routes.MY_LIST, "My List", Icons.Outlined.VideoLibrary),
    Friends(Routes.FRIENDS, "Friends", Icons.Outlined.Group),
    Profile(Routes.PROFILE, "Profile", Icons.Outlined.Person),
}
