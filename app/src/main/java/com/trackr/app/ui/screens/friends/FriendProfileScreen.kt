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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Casino
import androidx.compose.material.icons.outlined.Handshake
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.trackr.app.domain.model.ListEntry
import com.trackr.app.domain.model.ListStatus
import com.trackr.app.domain.model.Load
import com.trackr.app.domain.model.ProfileStats
import com.trackr.app.domain.util.GroupPick
import com.trackr.app.ui.components.EmptyState
import com.trackr.app.ui.components.ErrorState
import com.trackr.app.ui.components.GroupPickDialog
import com.trackr.app.ui.components.PosterImage
import com.trackr.app.ui.components.RatingBadge
import com.trackr.app.ui.components.ShimmerBox
import com.trackr.app.ui.components.StatTile
import com.trackr.app.ui.components.TrackrChip
import com.trackr.app.ui.components.TrackrTopBar
import com.trackr.app.ui.components.UserAvatar
import com.trackr.app.ui.theme.PillShape
import com.trackr.app.ui.theme.RatingAmber
import com.trackr.app.ui.theme.color

@Composable
fun FriendProfileScreen(onBack: () -> Unit, onOpenEntry: (ListEntry) -> Unit, vm: FriendProfileViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.removed) { if (state.removed) onBack() }
    FriendProfileContent(state, onBack, onOpenEntry, vm::load, vm::removeFriend)
}

@Composable
fun FriendProfileContent(
    state: FriendProfileUiState,
    onBack: () -> Unit,
    onOpenEntry: (ListEntry) -> Unit,
    onRetry: () -> Unit,
    onRemove: () -> Unit,
) {
    var status by rememberSaveable { mutableStateOf(ListStatus.WATCHING) }
    var confirm by rememberSaveable { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        TrackrTopBar(
            "Friend Profile", null, {}, onBack = onBack,
            trailing = {
                Box {
                    Icon(Icons.Filled.MoreVert, "More", Modifier.size(40.dp).clip(PillShape).clickable { menu = true }.padding(8.dp))
                    DropdownMenu(menu, { menu = false }) {
                        DropdownMenuItem(text = { Text("Remove friend", color = MaterialTheme.colorScheme.error) }, onClick = { menu = false; confirm = true })
                    }
                }
            },
        )
        when (val d = state.data) {
            Load.Loading -> Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ShimmerBox(Modifier.size(96.dp), PillShape); ShimmerBox(Modifier.fillMaxWidth().padding(end = 100.dp).size(width = 200.dp, height = 28.dp))
                ShimmerBox(Modifier.fillMaxWidth().size(width = 300.dp, height = 120.dp))
            }
            is Load.Failure -> ErrorState(d.message, onRetry)
            is Load.Success -> {
                val entries = d.data.entries.filter { it.status == status }.sortedByDescending { it.updatedAt }
                LazyVerticalGrid(
                    GridCells.Fixed(3), Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                UserAvatar(d.data.profile.avatarUrl, size = 88.dp)
                                Column {
                                    Text(d.data.profile.username, style = MaterialTheme.typography.displayMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("${d.data.entries.size} titles tracked", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            state.stats?.let { StatGrid(it) }
                            WatchTogether(state.together, onOpenEntry)
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(ListStatus.entries) { s ->
                                    TrackrChip(s.label, selected = status == s, count = d.data.entries.count { it.status == s }, onClick = { status = s })
                                }
                            }
                        }
                    }
                    if (entries.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
                        EmptyState(Icons.Outlined.Handshake, "Nothing here", "${d.data.profile.username} has no titles marked ${status.label.lowercase()}.")
                    }
                    items(entries, key = { it.key }) { e ->
                        Column(Modifier.clickable { onOpenEntry(e) }, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box {
                                PosterImage(e.posterUrl, Modifier.fillMaxWidth(), contentDescription = e.title)
                                e.rating?.let { RatingBadge(it.toDouble(), Modifier.align(Alignment.TopStart).padding(6.dp)) }
                            }
                            Text(e.title, style = MaterialTheme.typography.labelLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false },
        title = { Text("Remove friend?") },
        text = { Text("You'll stop seeing each other's lists and activity.") },
        confirmButton = { TextButton(onClick = { confirm = false; onRemove() }) { Text("Remove", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } },
    )
}

@Composable
private fun StatGrid(s: ProfileStats) {
    val completion = if (s.total == 0) 0 else (s.statusCounts[ListStatus.COMPLETED] ?: 0) * 100 / s.total
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            (s.statusCounts[ListStatus.COMPLETED] ?: 0).let { done -> StatTile("Total watched", done.toString(), Modifier.weight(1f), unit = if (done == 1) "title" else "titles") }
            StatTile("Time logged", s.hours.toString(), Modifier.weight(1f), unit = if (s.hours == 1) "hour" else "hours")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile("Avg score", s.averageRating?.let { "%.1f".format(it) } ?: "–", Modifier.weight(1f), unit = "/ 10", valueColor = RatingAmber)
            StatTile("Completion", "$completion%", Modifier.weight(1f), unit = "of list")
        }
    }
}

@Composable
private fun WatchTogether(shared: List<ListEntry>, onOpen: (ListEntry) -> Unit) {
    var pick by remember { mutableStateOf<ListEntry?>(null) }
    Column(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.surfaceContainer).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(40.dp).clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surfaceContainerHighest), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Handshake, null, tint = MaterialTheme.colorScheme.primary)
            }
            Column {
                Text("Watch Together", style = MaterialTheme.typography.titleMedium)
                Text("Based on your shared Plan to Watch", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (shared.isEmpty()) {
            Text("No overlap yet – add titles to Plan to Watch to find something to watch together.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Common in Plan to Watch (${shared.size} ${if (shared.size == 1) "title" else "titles"})",
                    Modifier.weight(1f), style = MaterialTheme.typography.labelMedium,
                )
                TextButton(onClick = { pick = GroupPick.pick(shared, pick) }) {
                    Icon(Icons.Outlined.Casino, null, Modifier.size(18.dp))
                    Text("Pick one", Modifier.padding(start = 6.dp))
                }
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(shared, key = { it.key }) { e ->
                    Column(Modifier.width(88.dp).clickable { onOpen(e) }, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        PosterImage(e.posterUrl, Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, contentDescription = e.title)
                        Text(e.title, style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
    pick?.let { p ->
        GroupPickDialog(
            title = p.title, posterUrl = p.posterUrl, subtitle = "On both your Plan to Watch lists", canPickAgain = shared.size > 1,
            onOpen = { pick = null; onOpen(p) }, onPickAgain = { pick = GroupPick.pick(shared, pick) }, onDismiss = { pick = null },
        )
    }
}
