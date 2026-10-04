package com.trackr.app.ui.screens.profile

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.trackr.app.domain.util.YearInReview
import com.trackr.app.ui.components.EmptyState
import com.trackr.app.ui.components.LocalProfile
import com.trackr.app.ui.components.TrackrChip
import com.trackr.app.ui.components.TrackrTopBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Month
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun WrappedScreen(onBack: () -> Unit, vm: WrappedViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val card = rememberGraphicsLayer()
    var sharing by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        TrackrTopBar("Year in review", LocalProfile.current?.avatarUrl, {}, onBack = onBack)
        WrappedContent(
            state = state,
            username = LocalProfile.current?.username,
            onSelectYear = vm::select,
            // Record the card into a layer as it draws, so Share can turn exactly what's on screen into an image.
            cardModifier = Modifier.drawWithContent {
                card.record { this@drawWithContent.drawContent() }
                drawLayer(card)
            },
            sharing = sharing,
            onShare = {
                val year = state.review?.year ?: return@WrappedContent
                sharing = true
                scope.launch {
                    runCatching { shareCard(context, card, year) }
                    sharing = false
                }
            },
        )
    }
}

/** Saves the recorded card as a PNG in the cache and opens the share sheet for it. */
private suspend fun shareCard(context: Context, card: GraphicsLayer, year: Int) {
    val bitmap = card.toImageBitmap().asAndroidBitmap()
    val file = withContext(Dispatchers.IO) {
        File(context.cacheDir, "shared").apply { mkdirs() }.resolve("trackr-$year-in-review.png").also { f ->
            f.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val send = Intent(Intent.ACTION_SEND)
        .setType("image/png")
        .putExtra(Intent.EXTRA_STREAM, uri)
        .putExtra(Intent.EXTRA_TEXT, "My $year on Trackr")
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(send, "Share your year"))
}

@Composable
fun WrappedContent(
    state: WrappedUiState,
    username: String?,
    onSelectYear: (Int) -> Unit,
    onShare: () -> Unit,
    cardModifier: Modifier = Modifier,
    sharing: Boolean = false,
) {
    val review = state.review
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (state.years.size > 1) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.years.forEach { y -> TrackrChip(y.toString(), selected = y == review?.year, onClick = { onSelectYear(y) }) }
            }
        }
        when {
            review == null -> Unit
            review.isEmpty -> EmptyState(
                Icons.Outlined.AutoAwesome, "Nothing finished in ${review.year} yet",
                "Mark titles as Completed and your year in review fills in here.",
            )
            else -> {
                WrappedCard(review, username, cardModifier)
                Button(onClick = onShare, enabled = !sharing, modifier = Modifier.fillMaxWidth()) {
                    Text(if (sharing) "Preparing image…" else "Share image")
                }
                if (review.usesEstimatedDates) {
                    Text(
                        "Some titles were completed before Trackr recorded completion dates, so their last edit is used instead.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** The shareable card: everything drawn here ends up in the image. */
@Composable
private fun WrappedCard(review: YearInReview, username: String?, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier.fillMaxWidth().clip(MaterialTheme.shapes.extraLarge)
            .background(Brush.verticalGradient(listOf(colors.primaryContainer, colors.surfaceContainerLowest)))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("TRACKR · ${review.year} IN REVIEW", style = MaterialTheme.typography.labelLarge, color = colors.onPrimaryContainer)
            username?.let { Text("@$it", style = MaterialTheme.typography.bodyMedium, color = colors.onPrimaryContainer) }
        }
        Column {
            Text(review.completed.toString(), style = MaterialTheme.typography.displayLarge, color = colors.onSurface)
            Text(
                (if (review.completed == 1) "title finished" else "titles finished") + " · about ${review.hours} hours",
                style = MaterialTheme.typography.titleMedium, color = colors.onSurface,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Figure(review.movies.toString(), "Movies", Modifier.weight(1f))
            Figure(review.shows.toString(), "Shows", Modifier.weight(1f))
            Figure(review.anime.toString(), "Anime", Modifier.weight(1f))
            Figure(review.episodes.toString(), "Episodes", Modifier.weight(1f))
        }
        if (review.topRated.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Top rated" + (review.averageRating?.let { " · avg ${"%.1f".format(Locale.US, it)}/10" } ?: ""),
                    style = MaterialTheme.typography.titleSmall, color = colors.onSurface,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    review.topRated.take(3).forEach { e ->
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            // Software bitmaps: hardware ones can't be read back when the card becomes an image.
                            AsyncImage(
                                ImageRequest.Builder(LocalContext.current).data(e.posterUrl).allowHardware(false).build(),
                                contentDescription = e.title,
                                modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(MaterialTheme.shapes.medium)
                                    .background(colors.surfaceContainerHigh),
                                contentScale = ContentScale.Crop,
                            )
                            Text(e.title, style = MaterialTheme.typography.labelMedium, color = colors.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${e.rating}/10", style = MaterialTheme.typography.labelSmall, color = colors.primary)
                        }
                    }
                    repeat(3 - review.topRated.take(3).size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
        review.busiestMonth?.let { month ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Busiest month: ${Month.of(month).getDisplayName(TextStyle.FULL, Locale.getDefault())}",
                    style = MaterialTheme.typography.titleSmall, color = colors.onSurface,
                )
                MonthBars(review.perMonth)
            }
        }
    }
}

@Composable
private fun Figure(value: String, label: String, modifier: Modifier) {
    Column(
        modifier.clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.7f)).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(value, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

/** Twelve bars, January to December, scaled to the busiest month. */
@Composable
private fun MonthBars(perMonth: List<Int>) {
    val max = perMonth.maxOrNull()?.takeIf { it > 0 } ?: 1
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth().height(56.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Bottom) {
            perMonth.forEach { n ->
                Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.BottomCenter) {
                    Box(
                        Modifier.fillMaxWidth().fillMaxHeight((n.toFloat() / max).coerceAtLeast(0.04f)).clip(MaterialTheme.shapes.extraSmall)
                            .background(if (n == max && n > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            (1..12).forEach { m ->
                Text(
                    Month.of(m).getDisplayName(TextStyle.NARROW, Locale.getDefault()), Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
    }
}
