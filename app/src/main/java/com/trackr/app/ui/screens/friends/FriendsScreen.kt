package com.trackr.app.ui.screens.friends

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.PlaylistAdd
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.trackr.app.domain.model.ActivityEntry
import com.trackr.app.domain.model.Comment
import com.trackr.app.domain.model.EntrySocial
import com.trackr.app.domain.model.Friend
import com.trackr.app.domain.model.FriendRequest
import com.trackr.app.domain.model.ListStatus
import com.trackr.app.domain.model.Load
import com.trackr.app.domain.model.MediaItem
import com.trackr.app.domain.model.MediaType
import com.trackr.app.domain.model.Recommendation
import com.trackr.app.ui.components.CommentsSheet
import com.trackr.app.ui.components.CountBadge
import com.trackr.app.ui.components.EmptyState
import com.trackr.app.ui.components.ErrorState
import com.trackr.app.ui.components.ListRowSkeleton
import com.trackr.app.ui.components.LocalProfile
import com.trackr.app.ui.components.MediaTypePill
import com.trackr.app.ui.components.PosterImage
import com.trackr.app.ui.components.RatingBadge
import com.trackr.app.ui.components.ReactionBar
import com.trackr.app.ui.components.RecommendationCard
import com.trackr.app.ui.components.SegmentedControl
import com.trackr.app.ui.components.StatusBadge
import com.trackr.app.ui.components.TrackrProgressBar
import com.trackr.app.ui.components.TrackrTopBar
import com.trackr.app.ui.components.UserAvatar
import com.trackr.app.ui.components.relativeTime
import com.trackr.app.ui.components.FollowLiveUpdates
import com.trackr.app.ui.theme.PillShape
import com.trackr.app.ui.theme.RatingAmber

enum class FriendsTab(val label: String) { ACTIVITY("Activity Feed"), FRIENDS("Friends List") }

class FriendsActions(
    val refresh: () -> Unit,
    val respond: (FriendRequest, Boolean) -> Unit,
    val planToWatch: (ActivityEntry) -> Unit,
    val openAdd: () -> Unit,
    val closeAdd: () -> Unit,
    val setAddQuery: (String) -> Unit,
    val search: () -> Unit,
    val sendRequest: () -> Unit,
    val toastShown: () -> Unit,
    val toggleReaction: (entryId: String, emoji: String) -> Unit = { _, _ -> },
    val openComments: (ActivityEntry) -> Unit = {},
    val postComment: (String) -> Unit = {},
    val deleteComment: (Comment) -> Unit = {},
    val closeComments: () -> Unit = {},
    val openRecommendation: (Recommendation) -> Unit = {},
    val dismissRecommendation: (Recommendation) -> Unit = {},
)

@Composable
fun FriendsScreen(
    onOpenFriend: (String) -> Unit,
    onOpenDetail: (ActivityEntry) -> Unit,
    onOpenProfile: () -> Unit,
    onOpenTitle: (MediaItem) -> Unit = {},
    onOpenSharedLists: () -> Unit = {},
    vm: FriendsViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    FollowLiveUpdates { vm.followLiveUpdates() }
    FriendsContent(
        state, LocalProfile.current?.avatarUrl, onOpenProfile, onOpenFriend, onOpenDetail,
        FriendsActions(
            vm::refresh, vm::respond, vm::planToWatch, vm::openAdd, vm::closeAdd, vm::setAddQuery, vm::search, vm::sendRequest, vm::toastShown,
            toggleReaction = vm::toggleReaction, openComments = vm::openComments, postComment = vm::postComment,
            deleteComment = vm::deleteComment, closeComments = vm::closeComments,
            openRecommendation = vm::openRecommendation, dismissRecommendation = vm::dismissRecommendation,
        ),
        onOpenTitle = onOpenTitle,
        onOpenSharedLists = onOpenSharedLists,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendsContent(
    state: FriendsUiState,
    avatarUrl: String?,
    onOpenProfile: () -> Unit,
    onOpenFriend: (String) -> Unit,
    onOpenDetail: (ActivityEntry) -> Unit,
    actions: FriendsActions,
    onOpenTitle: (MediaItem) -> Unit = {},
    onOpenSharedLists: () -> Unit = {},
) {
    var tab by rememberSaveable { mutableStateOf(FriendsTab.ACTIVITY) }
    var pendingOpen by rememberSaveable { mutableStateOf(true) }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.toast) {
        state.toast?.let { snackbar.showSnackbar(it); actions.toastShown() }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            TrackrTopBar("Friends", avatarUrl, onOpenProfile)
            PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = actions.refresh, modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    item {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(Modifier.weight(1f)) {
                                Text("Community", style = MaterialTheme.typography.displayMedium)
                                Text("See what your cinephile circle is tracking", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Row(
                                Modifier.padding(start = 8.dp).height(44.dp).clip(PillShape).background(MaterialTheme.colorScheme.primaryContainer)
                                    .clickable(onClick = actions.openAdd).padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Icon(Icons.Filled.PersonAdd, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                                Text("Add", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                        }
                    }
                    item {
                        val friendCount = (state.friends as? Load.Success)?.data?.size
                        SegmentedControl(
                            FriendsTab.entries, tab,
                            label = { if (it == FriendsTab.FRIENDS && friendCount != null) "${it.label} ($friendCount)" else it.label },
                            onSelect = { tab = it },
                        )
                    }
                    if (state.pending.isNotEmpty()) item("pending") {
                        PendingCard(state.pending, pendingOpen, { pendingOpen = !pendingOpen }, actions.respond, onOpenFriend)
                    }
                    item("shared") { SharedListsEntry(onOpenSharedLists) }
                    if (tab == FriendsTab.ACTIVITY && state.inbox.isNotEmpty()) item("inbox") {
                        InboxRow(state.inbox, onOpen = { r -> actions.openRecommendation(r); onOpenTitle(r.item) }, onDismiss = actions.dismissRecommendation)
                    }
                    when (tab) {
                        FriendsTab.ACTIVITY -> activityItems(state, actions, onOpenDetail, onOpenFriend)
                        FriendsTab.FRIENDS -> friendItems(state, actions, onOpenFriend)
                    }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp))
    }

    if (state.add.open) AddFriendDialog(state.add, actions)
    state.comments?.let { CommentsSheet(it, actions.postComment, actions.deleteComment, actions.closeComments) }
}

@Composable
private fun SharedListsEntry(onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.surfaceContainer).clickable(onClick = onClick).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(Icons.AutoMirrored.Outlined.PlaylistAdd, null, tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f)) {
            Text("Shared watchlists", style = MaterialTheme.typography.titleSmall)
            Text("Build lists with friends and let Trackr pick", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun InboxRow(inbox: List<Recommendation>, onOpen: (Recommendation) -> Unit, onDismiss: (Recommendation) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Recommended to you", style = MaterialTheme.typography.titleMedium)
            val unseen = inbox.count { !it.seen }
            if (unseen > 0) CountBadge(unseen, container = MaterialTheme.colorScheme.primaryContainer)
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(inbox, key = { it.id }) { r -> RecommendationCard(r, onOpen = { onOpen(r) }, onDismiss = { onDismiss(r) }) }
        }
    }
}

@Composable
private fun PendingCard(
    pending: List<FriendRequest>,
    open: Boolean,
    onToggle: () -> Unit,
    onRespond: (FriendRequest, Boolean) -> Unit,
    onOpenFriend: (String) -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.surfaceContainer).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth().clickable(onClick = onToggle), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(RatingAmber))
                Text("Pending Friend Requests", style = MaterialTheme.typography.titleSmall)
                CountBadge(pending.size, container = RatingAmber)
            }
            Icon(if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, if (open) "Collapse" else "Expand")
        }
        if (open) pending.forEach { r ->
            Row(
                Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(10.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                UserAvatar(r.from.avatarUrl, size = 44.dp)
                Column(Modifier.weight(1f)) {
                    Text(r.from.username, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("wants to be friends", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                CircleButton(Icons.Filled.Close, "Decline", MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.colorScheme.onSurface) { onRespond(r, false) }
                CircleButton(Icons.Filled.Check, "Accept", MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onPrimary) { onRespond(r, true) }
            }
        }
    }
}

@Composable
private fun CircleButton(icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String, bg: androidx.compose.ui.graphics.Color, fg: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    Box(Modifier.size(40.dp).clip(CircleShape).background(bg).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, desc, Modifier.size(20.dp), tint = fg)
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.activityItems(
    state: FriendsUiState,
    actions: FriendsActions,
    onOpenDetail: (ActivityEntry) -> Unit,
    onOpenFriend: (String) -> Unit,
) {
    when (val a = state.activity) {
        Load.Loading -> items(3, key = { "sk$it" }) { ListRowSkeleton() }
        is Load.Failure -> item("err") { ErrorState(a.message, actions.refresh) }
        is Load.Success -> if (a.data.isEmpty()) item("empty") {
            EmptyState(
                Icons.Outlined.Timeline, "No activity yet",
                "When your friends add, rate or finish titles, it shows up here. Add friends with their username or invite code.",
                actionLabel = "Add a friend", onAction = actions.openAdd,
            )
        } else items(a.data, key = { it.id }) { e ->
            ActivityCard(
                e, inMyList = e.key in state.myKeys, onClick = { onOpenDetail(e) }, onUser = { onOpenFriend(e.userId) }, onPlan = { actions.planToWatch(e) },
                social = state.social[e.id] ?: EntrySocial(), onReact = { emoji -> actions.toggleReaction(e.id, emoji) }, onComments = { actions.openComments(e) },
            )
        }
    }
}

@Composable
fun ActivityCard(
    e: ActivityEntry, inMyList: Boolean, onClick: () -> Unit, onUser: () -> Unit, onPlan: () -> Unit,
    social: EntrySocial = EntrySocial(), onReact: (String) -> Unit = {}, onComments: () -> Unit = {},
) {
    Column(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.surfaceContainer).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            UserAvatar(e.avatarUrl, size = 44.dp, onClick = onUser)
            Column(Modifier.weight(1f)) {
                Text(e.username, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.clickable(onClick = onUser))
                Text(
                    "${e.verb}${e.rating?.takeIf { e.verb == "rated" }?.let { " $it/10" } ?: ""} • ${relativeTime(e.updatedAt)}",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (e.status == ListStatus.WATCHING) StatusBadge(ListStatus.WATCHING)
        }
        Row(
            Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surfaceContainerHigh).clickable(onClick = onClick).padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PosterImage(e.posterUrl, Modifier.width(64.dp), shape = MaterialTheme.shapes.small, contentDescription = e.title)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                MediaTypePill(e.mediaType)
                Text(e.title, style = MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                e.rating?.let { RatingBadge(it.toDouble(), outOfTen = true, scrim = false) }
                if (e.status == ListStatus.WATCHING && e.totalEpisodes != null) {
                    TrackrProgressBar((e.progress.toFloat() / e.totalEpisodes).coerceIn(0f, 1f))
                    Text("Ep ${e.progress}/${e.totalEpisodes}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else if (e.status == ListStatus.WATCHING && e.mediaType != MediaType.MOVIE) {
                    Text("Episode ${e.progress}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        ReactionBar(social, onToggle = onReact, onComments = onComments)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            if (inMyList) Text("In your list", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp))
            else Row(
                Modifier.height(40.dp).clip(PillShape).background(MaterialTheme.colorScheme.surfaceContainerHigh).clickable(onClick = onPlan).padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(Icons.Outlined.BookmarkAdd, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                Text(if (e.status == ListStatus.WATCHING) "Watch along" else "Plan to Watch", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.friendItems(
    state: FriendsUiState,
    actions: FriendsActions,
    onOpenFriend: (String) -> Unit,
) {
    when (val f = state.friends) {
        Load.Loading -> items(3, key = { "fsk$it" }) { ListRowSkeleton() }
        is Load.Failure -> item("ferr") { ErrorState(f.message, actions.refresh) }
        is Load.Success -> if (f.data.isEmpty()) item("fempty") {
            EmptyState(Icons.Outlined.Group, "No friends yet", "Add friends by username or invite code to see what they're watching.", actionLabel = "Add a friend", onAction = actions.openAdd)
        } else items(f.data, key = { it.friendshipId }) { friend -> FriendRow(friend) { onOpenFriend(friend.profile.id) } }
    }
}

@Composable
fun FriendRow(friend: Friend, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.surfaceContainer).clickable(onClick = onClick).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        UserAvatar(friend.profile.avatarUrl, size = 52.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(friend.profile.username, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                if (friend.watching.isEmpty()) "Not watching anything right now"
                else "Watching: " + friend.watching.take(2).joinToString(", ") { it.title },
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun AddFriendDialog(add: AddFriendState, actions: FriendsActions) {
    AlertDialog(
        onDismissRequest = actions.closeAdd,
        title = { Text("Add a friend") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Enter their username or invite code.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = add.query, onValueChange = actions.setAddQuery, singleLine = true, label = { Text("Username or invite code") },
                    shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth(),
                )
                add.result?.let { p ->
                    Row(
                        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        UserAvatar(p.avatarUrl, size = 40.dp)
                        Text(p.username, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                    }
                }
                add.message?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = if (add.sent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            when {
                add.sent -> TextButton(onClick = actions.closeAdd) { Text("Done") }
                add.result != null -> Button(onClick = actions.sendRequest, enabled = !add.busy) { Text("Send request") }
                else -> Button(onClick = actions.search, enabled = !add.busy && add.query.isNotBlank()) { Text(if (add.busy) "Searching…" else "Find") }
            }
        },
        dismissButton = { if (!add.sent) TextButton(onClick = actions.closeAdd) { Text("Cancel") } },
    )
}
