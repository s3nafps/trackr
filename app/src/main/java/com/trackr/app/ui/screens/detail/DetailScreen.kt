package com.trackr.app.ui.screens.detail

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.trackr.app.domain.model.CastMember
import com.trackr.app.domain.model.ListStatus
import com.trackr.app.domain.model.Load
import com.trackr.app.domain.model.MediaDetail
import com.trackr.app.domain.model.MediaSource
import com.trackr.app.domain.model.MediaType
import com.trackr.app.ui.components.ErrorState
import com.trackr.app.ui.components.PosterImage
import com.trackr.app.ui.components.ScorePill
import com.trackr.app.ui.components.ShimmerBox
import com.trackr.app.ui.components.StatusBadge
import com.trackr.app.ui.components.TrackSheet
import com.trackr.app.ui.components.UserAvatar
import com.trackr.app.ui.components.formatRuntime
import com.trackr.app.ui.theme.PillShape
import com.trackr.app.ui.theme.color

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DetailScreen(onBack: () -> Unit, vm: DetailViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    var showSheet by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current

    Box(Modifier.fillMaxSize()) {
        when (val d = state.detail) {
            Load.Loading -> DetailSkeleton()
            is Load.Failure -> Column(Modifier.fillMaxSize().statusBarsPadding(), verticalArrangement = Arrangement.Center) {
                ErrorState(d.message, onRetry = { vm.load(force = true) })
            }
            is Load.Success -> DetailContent(
                detail = d.data, state = state, showSheetClick = { showSheet = true },
                onShare = {
                    val url = when (d.data.item.source) {
                        MediaSource.TMDB -> "https://www.themoviedb.org/${if (d.data.item.type == MediaType.MOVIE) "movie" else "tv"}/${d.data.item.externalId}"
                        MediaSource.ANILIST -> "https://anilist.co/anime/${d.data.item.externalId}"
                    }
                    context.startActivity(
                        Intent.createChooser(
                            Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, "${d.data.item.title} – $url") },
                            "Share",
                        ),
                    )
                },
            )
        }
        // Floating back button
        Box(
            Modifier.statusBarsPadding().padding(12.dp).size(40.dp).clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.7f)).clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
    }

    val detail = (state.detail as? Load.Success)?.data
    if (showSheet && detail != null) {
        TrackSheet(
            title = detail.item.title, initial = state.entry, totalEpisodes = detail.item.totalEpisodes,
            showProgress = detail.item.type != MediaType.MOVIE,
            onDismiss = { showSheet = false },
            onSave = { s, r, p -> vm.save(s, r, p); showSheet = false },
            onRemove = state.entry?.let { { vm.remove(); showSheet = false } },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DetailContent(detail: MediaDetail, state: DetailUiState, showSheetClick: () -> Unit, onShare: () -> Unit) {
    val item = detail.item
    var expanded by rememberSaveable { mutableStateOf(false) }
    val entry = state.entry

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
      Box(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(280.dp)) {
            AsyncImage(
                model = item.backdropUrl ?: item.posterUrl, contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(listOf(MaterialTheme.colorScheme.background.copy(alpha = 0.4f), Color.Transparent, MaterialTheme.colorScheme.background)),
                ),
            )
        }

        Column(Modifier.padding(top = 184.dp)) {
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Bottom) {
                PosterImage(item.posterUrl, Modifier.width(112.dp), shape = MaterialTheme.shapes.medium, contentDescription = item.title)
                Column(Modifier.weight(1f).padding(bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        detail.certification?.let {
                            Text(it, Modifier.clip(PillShape).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(horizontal = 8.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        val meta = listOfNotNull(
                            item.runtimeMinutes?.let { if (item.type == MediaType.MOVIE) formatRuntime(it) else "${it}m/ep" },
                            item.year?.toString(),
                        ).joinToString(" • ")
                        Text(meta, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(item.title, style = MaterialTheme.typography.headlineLarge, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item.score?.let { ScorePill(it) }
                        detail.voteCount?.takeIf { it > 0 }?.let {
                            Text(compactCount(it) + " votes", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(
                            if (item.source == MediaSource.TMDB) "TMDB" else "ANILIST",
                            Modifier.clip(MaterialTheme.shapes.extraSmall).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)).padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            if (item.genres.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    item.genres.forEach {
                        Text(it, Modifier.clip(PillShape).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(horizontal = 12.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(
                    Modifier.weight(1f).height(48.dp).clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.primaryContainer)
                        .clickable(onClick = showSheetClick).padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.Bookmark, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text(
                            entry?.let { it.status.label + (it.rating?.let { r -> " · $r/10" } ?: "") } ?: "Add to List",
                            style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                    Icon(Icons.Filled.ExpandMore, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                Box(
                    Modifier.size(48.dp).clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surfaceContainerHigh).clickable(onClick = onShare),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Filled.Share, "Share", Modifier.size(20.dp)) }
            }
            if (entry != null && item.type != MediaType.MOVIE) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    StatusBadge(entry.status)
                    Text(
                        entry.totalEpisodes?.let { "Episode ${entry.progress} of $it" } ?: "Episode ${entry.progress}",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Column(Modifier.padding(top = 24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            item.overview?.let { text ->
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Storyline", style = MaterialTheme.typography.headlineSmall)
                    detail.tagline?.let { Text("“$it”", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary) }
                    Text(
                        text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = if (expanded) Int.MAX_VALUE else 4, overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        if (expanded) "Show less" else "Read more", Modifier.clickable { expanded = !expanded },
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            InfoCard(detail)

            if (state.friends.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Friends who watched this", Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.headlineSmall)
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(state.friends, key = { it.id }) { f ->
                            val status = ListStatus.fromKey(f.status)
                            Row(
                                Modifier.clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.surfaceContainer).padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                UserAvatar(f.avatarUrl, size = 40.dp)
                                Column {
                                    Text(f.username, style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        status.label + (f.rating?.let { " · $it/10" } ?: ""),
                                        style = MaterialTheme.typography.labelSmall, color = status.color(),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (detail.seasons.isNotEmpty()) {
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Seasons", style = MaterialTheme.typography.headlineSmall)
                    detail.seasons.forEach { s ->
                        Row(
                            Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surfaceContainer).padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
                        ) {
                            PosterImage(s.posterUrl, Modifier.width(44.dp), shape = MaterialTheme.shapes.small)
                            Column(Modifier.weight(1f)) {
                                Text(s.name, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    listOfNotNull(s.year?.toString(), "${s.episodeCount} episodes").joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }

            if (detail.cast.isNotEmpty()) CastRow(detail.cast, if (item.source == MediaSource.ANILIST) "Characters" else "Cast")
        }
        }
      }
    }
}

@Composable
private fun InfoCard(detail: MediaDetail) {
    val item = detail.item
    val rows = listOfNotNull(
        detail.status?.let { "Status" to it },
        item.totalEpisodes?.takeIf { item.type != MediaType.MOVIE }?.let { "Episodes" to it.toString() },
        detail.seasonCount?.let { "Seasons" to it.toString() },
        item.runtimeMinutes?.let { "Runtime" to (if (item.type == MediaType.MOVIE) formatRuntime(it) else "${it} min / episode") },
        detail.studios.takeIf { it.isNotEmpty() }?.let { (if (item.source == MediaSource.ANILIST) "Studio" else "Network") to it.take(3).joinToString(", ") },
    )
    if (rows.isEmpty()) return
    Column(
        Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.surfaceContainer).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        rows.forEach { (k, v) ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(k, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(v, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 16.dp))
            }
        }
    }
}

@Composable
private fun CastRow(cast: List<CastMember>, title: String) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.headlineSmall)
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            items(cast, key = { it.name + it.role }) { c ->
                Column(Modifier.width(76.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    UserAvatar(c.imageUrl, size = 72.dp)
                    Text(c.name, style = MaterialTheme.typography.labelMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    c.role?.let { Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }
    }
}

@Composable
private fun DetailSkeleton() {
    Column(Modifier.fillMaxSize()) {
        ShimmerBox(Modifier.fillMaxWidth().height(280.dp), androidx.compose.ui.graphics.RectangleShape)
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Bottom) {
                ShimmerBox(Modifier.width(112.dp).height(168.dp), MaterialTheme.shapes.medium)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ShimmerBox(Modifier.width(120.dp).height(14.dp), MaterialTheme.shapes.small)
                    ShimmerBox(Modifier.width(180.dp).height(28.dp), MaterialTheme.shapes.small)
                }
            }
            ShimmerBox(Modifier.fillMaxWidth().height(48.dp), MaterialTheme.shapes.medium)
            ShimmerBox(Modifier.fillMaxWidth().height(80.dp), MaterialTheme.shapes.medium)
        }
    }
}

private fun compactCount(n: Int): String = when {
    n >= 1_000_000 -> "%.1fM".format(n / 1_000_000.0)
    n >= 1_000 -> "${n / 1000}k"
    else -> n.toString()
}
