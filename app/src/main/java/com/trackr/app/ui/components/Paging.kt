package com.trackr.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.trackr.app.domain.util.PageState

/**
 * The end of a paged list. While more pages exist it shows a spinner and asks for the next page as soon as it is
 * composed, which lazy lists do when it scrolls into view; after a failure it offers a retry instead.
 */
@Composable
fun PagingFooter(state: PageState, onLoadMore: () -> Unit, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    when {
        state.error != null -> Column(
            modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
        ) {
            Text(
                state.error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center, maxLines = 3, overflow = TextOverflow.Ellipsis,
            )
            TextButton(onClick = onRetry) { Text("Retry") }
        }
        !state.endReached -> {
            // Re-runs when a page lands, so a footer that is still visible keeps the list filling up.
            LaunchedEffect(state.pages, state.loading) { if (!state.loading) onLoadMore() }
            Box(modifier, contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
