package com.trackr.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.trackr.app.data.repository.SocialRepository
import com.trackr.app.domain.model.Comment
import com.trackr.app.domain.model.EntrySocial
import com.trackr.app.domain.model.Load
import com.trackr.app.domain.model.REACTIONS
import com.trackr.app.domain.model.Recommendation
import com.trackr.app.ui.screens.social.CommentsState
import com.trackr.app.ui.screens.social.RecommendState
import com.trackr.app.ui.theme.PillShape

/** The five reaction pills (count shown once someone reacted, highlighted when you did) and the comments pill. */
@Composable
fun ReactionBar(social: EntrySocial, onToggle: (String) -> Unit, onComments: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        REACTIONS.forEach { emoji ->
            val mine = emoji in social.myReactions
            val count = social.reactions[emoji] ?: 0
            Row(
                Modifier.height(32.dp).clip(PillShape)
                    .background(if (mine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh)
                    .clickable { onToggle(emoji) }
                    .semantics {
                        contentDescription = "React $emoji" + if (count > 0) ", $count" else ""
                        selected = mine
                    }
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(emoji, style = MaterialTheme.typography.labelLarge)
                if (count > 0) {
                    Text(
                        count.toString(), style = MaterialTheme.typography.labelMedium,
                        color = if (mine) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Row(
            Modifier.height(32.dp).clip(PillShape).background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .clickable(onClick = onComments).padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(Icons.Outlined.ChatBubbleOutline, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                if (social.commentCount > 0) social.commentCount.toString() else "Comment",
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommentsSheet(state: CommentsState, onPost: (String) -> Unit, onDelete: (Comment) -> Unit, onDismiss: () -> Unit) {
    var draft by rememberSaveable(state.entryId) { mutableStateOf("") }
    LaunchedEffect(state.posted) { if (state.posted > 0) draft = "" }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 16.dp).imePadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column {
                Text("Comments", style = MaterialTheme.typography.headlineSmall)
                Text(state.title, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            when (val c = state.comments) {
                Load.Loading -> Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(24.dp)) }
                is Load.Failure -> Text(c.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                is Load.Success -> if (c.data.isEmpty()) {
                    Text("No comments yet. Start the conversation.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    LazyColumn(Modifier.heightIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        items(c.data, key = { it.id }) { CommentRow(it, onDelete) }
                    }
                }
            }
            state.error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    draft, { if (it.length <= SocialRepository.MAX_COMMENT_LENGTH) draft = it }, Modifier.weight(1f),
                    placeholder = { Text("Add a comment") }, maxLines = 4, enabled = !state.posting,
                )
                if (state.posting) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                } else {
                    IconButton(onClick = { onPost(draft) }, enabled = draft.isNotBlank()) {
                        Icon(Icons.AutoMirrored.Filled.Send, "Post comment", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

@Composable
private fun CommentRow(c: Comment, onDelete: (Comment) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        UserAvatar(c.avatarUrl, size = 32.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(c.username, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(relativeTime(c.createdAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(c.body, style = MaterialTheme.typography.bodyMedium)
        }
        if (c.canDelete) {
            IconButton(onClick = { onDelete(c) }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Outlined.DeleteOutline, "Delete comment", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecommendSheet(title: String, state: RecommendState, onSend: (friendId: String, note: String) -> Unit, onDismiss: () -> Unit) {
    var note by rememberSaveable { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 16.dp).imePadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column {
                Text("Recommend to a friend", style = MaterialTheme.typography.headlineSmall)
                Text(title, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            OutlinedTextField(
                note, { if (it.length <= SocialRepository.MAX_NOTE_LENGTH) note = it }, Modifier.fillMaxWidth(),
                placeholder = { Text("Add a note (optional)") }, maxLines = 3,
            )
            state.error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            when (val f = state.friends) {
                Load.Loading -> Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(24.dp)) }
                is Load.Failure -> Text(f.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                is Load.Success -> if (f.data.isEmpty()) {
                    Text("Add friends to recommend titles to them.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    LazyColumn(Modifier.heightIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(f.data, key = { it.id }) { friend ->
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                UserAvatar(friend.avatarUrl, size = 40.dp)
                                Text(friend.username, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                when {
                                    friend.id in state.sent -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Icon(Icons.Filled.Check, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                                        Text("Sent", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                                    }
                                    state.sending == friend.id -> CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                                    else -> FilledTonalButton(onClick = { onSend(friend.id, note) }, enabled = state.sending == null) { Text("Send") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** A title a friend sent you, for the inbox row on the Friends screen. */
@Composable
fun RecommendationCard(r: Recommendation, onOpen: () -> Unit, onDismiss: () -> Unit) {
    Column(
        Modifier.width(150.dp).clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.surfaceContainer).clickable(onClick = onOpen).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box {
            PosterImage(r.item.posterUrl, Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, contentDescription = r.item.title)
            if (!r.seen) Box(Modifier.align(Alignment.TopStart).padding(6.dp).size(10.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
            Box(
                Modifier.align(Alignment.TopEnd).padding(4.dp).size(28.dp).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.8f)).clickable(onClick = onDismiss),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.Close, "Dismiss recommendation", Modifier.size(16.dp)) }
        }
        Text(r.item.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("from @${r.from.username}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        r.note?.let { Text("“$it”", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis) }
    }
}
