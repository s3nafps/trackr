package com.trackr.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.trackr.app.domain.model.Genre
import com.trackr.app.domain.model.MediaType

/** "Any genre" plus the genres [type] has; tapping the selected genre clears it. */
@Composable
fun GenreChips(
    type: MediaType?,
    selected: Genre?,
    onSelect: (Genre?) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp),
) {
    LazyRow(modifier, contentPadding = contentPadding, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item("any") { TrackrChip("Any genre", selected = selected == null, onClick = { onSelect(null) }) }
        items(Genre.forType(type), key = { it.name }) { g ->
            TrackrChip(g.label, selected = selected == g, onClick = { onSelect(if (selected == g) null else g) })
        }
    }
}
