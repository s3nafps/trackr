package com.trackr.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.trackr.app.domain.model.ListStatus
import com.trackr.app.domain.util.GenreBreakdown
import com.trackr.app.ui.theme.PillShape
import com.trackr.app.ui.theme.RatingAmber
import com.trackr.app.ui.theme.color

/** 10 bars (rating 1..10); the most common rating is highlighted in amber, a 10 in primary. */
@Composable
fun RatingDistributionChart(counts: List<Int>, modifier: Modifier = Modifier) {
    val max = (counts.maxOrNull() ?: 0).coerceAtLeast(1)
    val peak = counts.indexOf(counts.maxOrNull() ?: 0)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth().height(120.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
            counts.forEachIndexed { i, c ->
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.Bottom, horizontalAlignment = Alignment.CenterHorizontally) {
                    if (c > 0 && i == peak) Text(c.toString(), style = MaterialTheme.typography.labelSmall, color = RatingAmber)
                    Box(
                        Modifier.fillMaxWidth().fillMaxHeight(if (c == 0) 0.04f else (c.toFloat() / max) * 0.8f)
                            .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                            .background(
                                when {
                                    c > 0 && i == peak -> RatingAmber
                                    i == 9 && c > 0 -> MaterialTheme.colorScheme.primary
                                    else -> MaterialTheme.colorScheme.surfaceContainerHighest
                                },
                            ),
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            (1..10).forEach { Text("$it", Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center) }
        }
    }
}

/** Stacked bar + legend rows for how the list is distributed over the four statuses. */
@Composable
fun StatusBreakdown(counts: Map<ListStatus, Int>, modifier: Modifier = Modifier) {
    val total = counts.values.sum().coerceAtLeast(1)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth().height(10.dp).clip(PillShape).background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
            ListStatus.entries.forEach { s ->
                val c = counts[s] ?: 0
                if (c > 0) Box(Modifier.weight(c.toFloat()).fillMaxHeight().background(s.color()))
            }
        }
        ListStatus.entries.forEach { s ->
            val c = counts[s] ?: 0
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(s.color()))
                    Text(s.label, style = MaterialTheme.typography.bodyMedium)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (c == 1) "1 title" else "$c titles", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${c * 100 / total}%", style = MaterialTheme.typography.labelLarge, color = s.color(), modifier = Modifier.padding(start = 4.dp))
                }
            }
        }
    }
}

/** Horizontal bars for the most common genres, longest first; each shows the share of titles in that genre. */
@Composable
fun GenreChart(breakdown: GenreBreakdown, modifier: Modifier = Modifier) {
    val max = (breakdown.top.maxOfOrNull { it.titles } ?: 0).coerceAtLeast(1)
    val counted = breakdown.counted.coerceAtLeast(1)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        breakdown.top.forEachIndexed { i, g ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(g.genre, style = MaterialTheme.typography.bodyMedium)
                    Text("${g.titles * 100 / counted}%", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Box(Modifier.fillMaxWidth().height(8.dp).clip(PillShape).background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
                    Box(
                        Modifier.fillMaxWidth(g.titles.toFloat() / max).fillMaxHeight().clip(PillShape)
                            .background(if (i == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer),
                    )
                }
            }
        }
    }
}
