package com.trackr.app.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.material3.ColorProviders
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.trackr.app.MainActivity
import com.trackr.app.data.airing.AiringNotifier
import com.trackr.app.data.local.AiringDao
import com.trackr.app.data.local.ListEntryDao
import com.trackr.app.data.local.TitleMetaDao
import com.trackr.app.data.meta.TitleMetaRepository.Companion.toDomain
import com.trackr.app.data.mapper.ListEntryMapper.toDomain
import com.trackr.app.data.repository.ListRepository
import com.trackr.app.ui.theme.DarkScheme
import com.trackr.app.ui.theme.LightScheme
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun listDao(): ListEntryDao
    fun airingDao(): AiringDao
    fun titleMetaDao(): TitleMetaDao
    fun lists(): ListRepository
}

private fun Context.widgetEntryPoint() = EntryPointAccessors.fromApplication(applicationContext, WidgetEntryPoint::class.java)

/** Same colour tokens as the app; follows the system's light/dark setting. */
private val WidgetColors = ColorProviders(light = LightScheme, dark = DarkScheme)

/** Home-screen "Up next": episodes airing this week and what you're watching, with a +1 per show. */
class UpNextWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val ep = context.widgetEntryPoint()
        val state = UpNext.build(
            entries = ep.listDao().observeAll().first().map { it.toDomain() },
            airing = ep.airingDao().getAll(),
            now = System.currentTimeMillis(),
            meta = ep.titleMetaDao().getAll().associate { "${it.source}:${it.externalId}" to it.toDomain() },
        )
        provideContent { GlanceTheme(colors = WidgetColors) { UpNextContent(state) } }
    }
}

class UpNextWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = UpNextWidget()
}

/** "+1": marks the next episode watched, exactly like My List's +1 Ep. */
class IncrementEpisodeAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val source = parameters[SourceKey] ?: return
        val id = parameters[IdKey] ?: return
        val lists = context.widgetEntryPoint().lists()
        val entry = lists.entry(source, id).first() ?: return
        val aired = context.widgetEntryPoint().titleMetaDao().get(source, id)?.airedEpisodes
        lists.incrementProgress(entry, aired)
        UpNextWidget().update(context, glanceId)
    }

    companion object {
        val SourceKey = ActionParameters.Key<String>("source")
        val IdKey = ActionParameters.Key<String>("id")
    }
}

@Composable
private fun UpNextContent(state: UpNextState) {
    val openApp = actionStartActivity<MainActivity>()
    Column(GlanceModifier.fillMaxSize().cornerRadius(16.dp).background(GlanceTheme.colors.surface).padding(12.dp)) {
        Row(GlanceModifier.fillMaxWidth().clickable(openApp), verticalAlignment = Alignment.CenterVertically) {
            Text("Up next", style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Bold))
            Spacer(GlanceModifier.defaultWeight())
            Text("Trackr", style = TextStyle(color = GlanceTheme.colors.primary, fontSize = 12.sp, fontWeight = FontWeight.Medium))
        }
        Spacer(GlanceModifier.height(6.dp))
        if (state.isEmpty) {
            Text(
                "Nothing airing this week. Start watching something in Trackr and it shows up here.",
                GlanceModifier.clickable(openApp),
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp),
            )
        } else {
            LazyColumn {
                if (state.airing.isNotEmpty()) {
                    item { SectionLabel("Airing this week") }
                    items(state.airing) { UpNextRowView(it) }
                }
                if (state.watching.isNotEmpty()) {
                    item { SectionLabel("Continue watching") }
                    items(state.watching) { UpNextRowView(it) }
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text, GlanceModifier.padding(top = 6.dp, bottom = 2.dp),
        style = TextStyle(color = GlanceTheme.colors.primary, fontSize = 12.sp, fontWeight = FontWeight.Medium),
    )
}

@Composable
private fun UpNextRowView(row: UpNextRow) {
    val context = LocalContext.current
    // Same extras as the airing notification, so MainActivity opens the title's Detail screen.
    val open = Intent(context, MainActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        .putExtra(AiringNotifier.EXTRA_OPEN_SOURCE, row.source)
        .putExtra(AiringNotifier.EXTRA_OPEN_ID, row.externalId)
        .putExtra(AiringNotifier.EXTRA_OPEN_TYPE, row.mediaType)
    Row(GlanceModifier.fillMaxWidth().padding(vertical = 5.dp).clickable(actionStartActivity(open)), verticalAlignment = Alignment.CenterVertically) {
        Column(GlanceModifier.defaultWeight()) {
            Text(row.title, maxLines = 1, style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Medium))
            Text(row.line, maxLines = 1, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp))
        }
        if (row.canIncrement) {
            Box(
                GlanceModifier.size(36.dp).cornerRadius(18.dp).background(GlanceTheme.colors.primaryContainer)
                    .clickable(actionRunCallback<IncrementEpisodeAction>(actionParametersOf(IncrementEpisodeAction.SourceKey to row.source, IncrementEpisodeAction.IdKey to row.externalId)))
                    .semantics { contentDescription = "Mark next episode of ${row.title} watched" },
                contentAlignment = Alignment.Center,
            ) {
                Text("+1", style = TextStyle(color = GlanceTheme.colors.onPrimaryContainer, fontSize = 13.sp, fontWeight = FontWeight.Bold))
            }
        }
    }
}
