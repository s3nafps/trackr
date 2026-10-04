package com.trackr.app.ui.screens.social

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Casino
import androidx.compose.material.icons.automirrored.outlined.PlaylistAdd
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import com.trackr.app.data.repository.SharedListsRepository
import com.trackr.app.domain.model.Load
import com.trackr.app.domain.model.MediaItem
import com.trackr.app.domain.model.Profile
import com.trackr.app.domain.model.SharedList
import com.trackr.app.domain.model.SharedListItem
import com.trackr.app.ui.components.EmptyState
import com.trackr.app.ui.components.ErrorState
import com.trackr.app.ui.components.GroupPickDialog
import com.trackr.app.ui.components.ListRowSkeleton
import com.trackr.app.ui.components.LocalProfile
import com.trackr.app.ui.components.PosterImage
import com.trackr.app.ui.components.TrackrTopBar
import com.trackr.app.ui.components.UserAvatar

// ---------------------------------------------------------------- all lists

@Composable
fun SharedListsScreen(onBack: () -> Unit, onOpenList: (String) -> Unit, vm: SharedListsViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.openList) { state.openList?.let { onOpenList(it); vm.listOpened() } }
    SharedListsContent(state, onBack, onOpenList, vm::refresh, vm::openCreate, vm::closeCreate, vm::create, vm::toastShown)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharedListsContent(
    state: SharedListsUiState,
    onBack: () -> Unit,
    onOpenList: (String) -> Unit,
    onRefresh: () -> Unit,
    onNew: () -> Unit,
    onCloseCreate: () -> Unit,
    onCreate: (String, Set<String>) -> Unit,
    onToastShown: () -> Unit,
) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.toast) { state.toast?.let { snackbar.showSnackbar(it); onToastShown() } }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            TrackrTopBar("Shared lists", LocalProfile.current?.avatarUrl, {}, onBack = onBack, trailing = {
                IconButton(onClick = onNew) { Icon(Icons.Filled.Add, "New shared list") }
            })
            PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item {
                        Text(
                            "Watchlists you build with friends. Add titles together, then let Trackr pick one for movie night.",
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    when (val l = state.lists) {
                        Load.Loading -> items(3, key = { "sk$it" }) { ListRowSkeleton() }
                        is Load.Failure -> item("err") { ErrorState(l.message, onRefresh) }
                        is Load.Success -> if (l.data.isEmpty()) item("empty") {
                            EmptyState(
                                Icons.AutoMirrored.Outlined.PlaylistAdd, "No shared lists yet",
                                "Start one with your friends and add titles from any title's page.",
                                actionLabel = "New shared list", onAction = onNew,
                            )
                        } else items(l.data, key = { it.id }) { list -> SharedListCard(list) { onOpenList(list.id) } }
                    }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp))
    }
    state.creating?.let { CreateListDialog(it, onCreate, onCloseCreate) }
}

@Composable
private fun SharedListCard(list: SharedList, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.surfaceContainer).clickable(onClick = onClick).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        PosterImage(list.posters.firstOrNull(), Modifier.width(64.dp), shape = MaterialTheme.shapes.medium)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(list.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${list.itemCount} ${if (list.itemCount == 1) "title" else "titles"}",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            MemberFaces(list.members)
        }
    }
}

/** Up to five overlapping avatars, then "+n". */
@Composable
private fun MemberFaces(members: List<Profile>) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        members.take(5).forEachIndexed { i, p -> UserAvatar(p.avatarUrl, Modifier.offset(x = (-8 * i).dp), size = 26.dp) }
        if (members.size > 5) {
            Text("+${members.size - 5}", Modifier.offset(x = (-8 * 5 + 4).dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CreateListDialog(state: CreateListState, onCreate: (String, Set<String>) -> Unit, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var picked by rememberSaveable { mutableStateOf(setOf<String>()) }
    AlertDialog(
        onDismissRequest = { if (!state.busy) onDismiss() },
        title = { Text("New shared list") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    name, { if (it.length <= SharedListsRepository.MAX_NAME_LENGTH) name = it }, Modifier.fillMaxWidth(),
                    singleLine = true, placeholder = { Text("Movie night") }, enabled = !state.busy,
                )
                Text("Invite friends", style = MaterialTheme.typography.titleSmall)
                when (val f = state.friends) {
                    Load.Loading -> CircularProgressIndicator(Modifier.size(24.dp))
                    is Load.Failure -> Text(f.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    is Load.Success -> if (f.data.isEmpty()) {
                        Text("Add friends first, or create the list now and invite them later.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        Column(Modifier.heightIn(max = 240.dp).verticalScroll(rememberScrollState())) {
                            f.data.forEach { p ->
                                val checked = p.id in picked
                                Row(
                                    Modifier.fillMaxWidth().clickable(enabled = !state.busy) { picked = if (checked) picked - p.id else picked + p.id },
                                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Checkbox(checked, { picked = if (checked) picked - p.id else picked + p.id }, enabled = !state.busy)
                                    UserAvatar(p.avatarUrl, size = 28.dp)
                                    Text(p.username, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
                state.error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = { onCreate(name, picked) }, enabled = name.isNotBlank() && !state.busy) { Text(if (state.busy) "Creating…" else "Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !state.busy) { Text("Cancel") } },
    )
}

// ---------------------------------------------------------------- one list

class SharedListActions(
    val refresh: () -> Unit = {},
    val removeItem: (SharedListItem) -> Unit = {},
    val pickForUs: () -> Unit = {},
    val closePick: () -> Unit = {},
    val openMembers: () -> Unit = {},
    val closeMembers: () -> Unit = {},
    val addMember: (Profile) -> Unit = {},
    val removeMember: (Profile) -> Unit = {},
    val leave: () -> Unit = {},
    val delete: () -> Unit = {},
    val toastShown: () -> Unit = {},
)

@Composable
fun SharedListScreen(onBack: () -> Unit, onOpenTitle: (MediaItem) -> Unit, vm: SharedListViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.closed) { if (state.closed) onBack() }
    SharedListContent(
        state, onBack, onOpenTitle,
        SharedListActions(
            vm::refresh, vm::removeItem, vm::pickForUs, vm::closePick, vm::openMembers, vm::closeMembers,
            vm::addMember, vm::removeMember, vm::leave, vm::delete, vm::toastShown,
        ),
    )
}

@Composable
fun SharedListContent(state: SharedListUiState, onBack: () -> Unit, onOpenTitle: (MediaItem) -> Unit, actions: SharedListActions) {
    val list = (state.list as? Load.Success)?.data
    val items = (state.items as? Load.Success)?.data.orEmpty()
    var menu by remember { mutableStateOf(false) }
    var confirm by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.toast) { state.toast?.let { snackbar.showSnackbar(it); actions.toastShown() } }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            TrackrTopBar(list?.name ?: "Shared list", LocalProfile.current?.avatarUrl, {}, onBack = onBack, trailing = {
                if (list != null) Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "More") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Members") }, onClick = { menu = false; actions.openMembers() })
                        DropdownMenuItem(
                            text = { Text(if (state.isOwner) "Delete list" else "Leave list", color = MaterialTheme.colorScheme.error) },
                            onClick = { menu = false; confirm = true },
                        )
                    }
                }
            })
            when (val l = state.list) {
                Load.Loading -> Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { repeat(3) { ListRowSkeleton() } }
                is Load.Failure -> ErrorState(l.message, actions.refresh)
                is Load.Success -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item("head") {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(
                                Modifier.clip(MaterialTheme.shapes.medium).clickable(onClick = actions.openMembers).padding(4.dp),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                MemberFaces(l.data.members)
                                Text(
                                    "${l.data.members.size} ${if (l.data.members.size == 1) "member" else "members"}",
                                    style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
                                )
                            }
                            Button(onClick = actions.pickForUs, enabled = items.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Outlined.Casino, null, Modifier.size(18.dp))
                                Text("Pick for us", Modifier.padding(start = 8.dp))
                            }
                        }
                    }
                    when (val i = state.items) {
                        Load.Loading -> items(3, key = { "isk$it" }) { ListRowSkeleton() }
                        is Load.Failure -> item("ierr") { ErrorState(i.message, actions.refresh) }
                        is Load.Success -> if (i.data.isEmpty()) item("iempty") {
                            EmptyState(
                                Icons.AutoMirrored.Outlined.PlaylistAdd, "Nothing here yet",
                                "Open any title and choose “Add to a shared list” to start this list.",
                            )
                        } else items(i.data, key = { it.item.key }) { row -> SharedItemRow(row, { onOpenTitle(row.item) }, { actions.removeItem(row) }) }
                    }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp))
    }

    state.pick?.let { p ->
        GroupPickDialog(
            title = p.item.title, posterUrl = p.item.posterUrl, subtitle = p.addedBy?.let { "Added by @${it.username}" },
            canPickAgain = items.size > 1,
            onOpen = { actions.closePick(); onOpenTitle(p.item) }, onPickAgain = actions.pickForUs, onDismiss = actions.closePick,
        )
    }
    if (list != null) state.members?.let { MembersSheet(list, it, state.me, state.isOwner, actions) }
    if (confirm && list != null) AlertDialog(
        onDismissRequest = { confirm = false },
        title = { Text(if (state.isOwner) "Delete “${list.name}”?" else "Leave “${list.name}”?") },
        text = {
            Text(
                if (state.isOwner) "The list and its titles are removed for everyone." else "You can be added back by the list's owner.",
            )
        },
        confirmButton = {
            TextButton(onClick = { confirm = false; if (state.isOwner) actions.delete() else actions.leave() }) {
                Text(if (state.isOwner) "Delete" else "Leave", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } },
    )
}

@Composable
private fun SharedItemRow(item: SharedListItem, onOpen: () -> Unit, onRemove: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.surfaceContainer).clickable(onClick = onOpen).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PosterImage(item.item.posterUrl, Modifier.width(52.dp), shape = MaterialTheme.shapes.small, contentDescription = item.item.title)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(item.item.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(item.item.type.label, item.addedBy?.let { "added by @${it.username}" }).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onRemove) {
            Icon(Icons.Outlined.RemoveCircleOutline, "Remove ${item.item.title} from the list", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MembersSheet(list: SharedList, state: MembersState, me: String?, isOwner: Boolean, actions: SharedListActions) {
    ModalBottomSheet(onDismissRequest = actions.closeMembers) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Members", style = MaterialTheme.typography.headlineSmall)
            list.members.forEach { p ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    UserAvatar(p.avatarUrl, size = 36.dp)
                    Text(
                        p.username + if (p.id == list.ownerId) " · owner" else "", Modifier.weight(1f),
                        style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    when {
                        state.busy == p.id -> CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                        isOwner && p.id != me -> IconButton(onClick = { actions.removeMember(p) }) {
                            Icon(Icons.Filled.Close, "Remove ${p.username}", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            if (isOwner) {
                Text("Add friends", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
                when (val f = state.friends) {
                    Load.Loading -> CircularProgressIndicator(Modifier.size(24.dp))
                    is Load.Failure -> Text(f.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    is Load.Success -> {
                        val memberIds = list.members.map { it.id }.toSet()
                        val addable = f.data.filter { it.id !in memberIds }
                        if (addable.isEmpty()) {
                            Text("All your friends are already here.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            LazyColumn(Modifier.heightIn(max = 280.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(addable, key = { it.id }) { p ->
                                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        UserAvatar(p.avatarUrl, size = 36.dp)
                                        Text(p.username, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        if (state.busy == p.id) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                                        else FilledTonalButton(onClick = { actions.addMember(p) }, enabled = state.busy == null) { Text("Add") }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                Text("Only the list's owner can add or remove people.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
