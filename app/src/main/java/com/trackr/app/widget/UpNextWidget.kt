package com.trackr.app.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
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
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
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

/** From this width the widget shows the hero beside what's coming up; narrower, the hero alone. */
private val WideFrom = 200.dp

/**
 * Home-screen "Up next": the title to watch next, with its progress and +1, and what's airing this week. The launcher
 * picks whichever of the two sizes fits, so a 2x2 and a 4x2 get different layouts.
 */
class UpNextWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Responsive(setOf(SMALL, WIDE))

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

    private companion object {
        val SMALL = DpSize(110.dp, 110.dp)
        val WIDE = DpSize(250.dp, 110.dp)
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
    val wide = LocalSize.current.width >= WideFrom
    val hero = state.hero
    Column(GlanceModifier.fillMaxSize().cornerRadius(16.dp).background(GlanceTheme.colors.surface).padding(12.dp)) {
        when {
            state.isEmpty -> Text(
                "Nothing airing this week. Start watching something in Trackr and it shows up here.",
                GlanceModifier.clickable(openApp),
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp),
            )
            hero == null -> ComingUp(state.coming, GlanceModifier.fillMaxSize())
            wide -> Row(GlanceModifier.fillMaxSize()) {
                HeroView(hero, GlanceModifier.defaultWeight().fillMaxHeight())
                Spacer(GlanceModifier.width(12.dp))
                ComingUp(state.coming, GlanceModifier.defaultWeight().fillMaxHeight())
            }
            else -> HeroView(hero, GlanceModifier.fillMaxSize())
        }
    }
}

/** The title to watch next: its status, progress bar, the episode the +1 marks, and the +1 itself when there is one. */
@Composable
private fun HeroView(hero: UpNextHero, modifier: GlanceModifier) {
    val context = LocalContext.current
    Column(modifier.clickable(actionStartActivity(openTitleIntent(context, hero.row)))) {
        Text("Watch next", style = TextStyle(color = GlanceTheme.colors.primary, fontSize = 11.sp, fontWeight = FontWeight.Medium))
        Text(hero.row.title, maxLines = 1, style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Bold))
        Text(hero.status, maxLines = 1, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp))
        if (hero.cells.isNotEmpty()) {
            Spacer(GlanceModifier.height(6.dp))
            ProgressBar(hero.cells)
        }
        Spacer(GlanceModifier.defaultWeight())
        hero.episode?.let {
            Text(it, maxLines = 1, style = TextStyle(color = GlanceTheme.colors.primary, fontSize = 26.sp, fontWeight = FontWeight.Bold))
        }
        if (hero.row.canIncrement) {
            Spacer(GlanceModifier.height(6.dp))
            Box(
                GlanceModifier.fillMaxWidth().height(30.dp).cornerRadius(15.dp).background(GlanceTheme.colors.primaryContainer)
                    .clickable(actionRunCallback<IncrementEpisodeAction>(actionParametersOf(IncrementEpisodeAction.SourceKey to hero.row.source, IncrementEpisodeAction.IdKey to hero.row.externalId)))
                    .semantics { contentDescription = "Mark next episode of ${hero.row.title} watched" },
                contentAlignment = Alignment.Center,
            ) {
                Text("+1 Ep", style = TextStyle(color = GlanceTheme.colors.onPrimaryContainer, fontSize = 12.sp, fontWeight = FontWeight.Bold))
            }
        }
    }
}

/** One equal cell per entry of [cells]: Glance can't weight cells by size, so the bar is built from equal ones. */
@Composable
private fun ProgressBar(cells: List<ProgressCell>) {
    Row(GlanceModifier.fillMaxWidth().height(6.dp)) {
        cells.forEach { cell ->
            val color = when (cell) {
                ProgressCell.WATCHED -> GlanceTheme.colors.primary
                ProgressCell.TO_WATCH -> GlanceTheme.colors.secondary
                ProgressCell.UPCOMING -> GlanceTheme.colors.surfaceVariant
            }
            Box(GlanceModifier.defaultWeight().fillMaxHeight().padding(horizontal = 0.5.dp).background(color)) {}
        }
    }
}

/** What's airing this week, soonest first. */
@Composable
private fun ComingUp(rows: List<UpNextRow>, modifier: GlanceModifier) {
    LazyColumn(modifier) {
        item { SectionLabel("Coming up") }
        if (rows.isEmpty()) {
            item { Text("Nothing else airing this week.", style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp)) }
        } else {
            items(rows) { UpNextRowView(it) }
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
    Column(GlanceModifier.fillMaxWidth().padding(vertical = 5.dp).clickable(actionStartActivity(openTitleIntent(context, row)))) {
        Text(row.title, maxLines = 1, style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Medium))
        Text(row.line, maxLines = 1, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp))
    }
}

/** Opens the title's Detail screen, with the same extras as the airing notification. */
private fun openTitleIntent(context: Context, row: UpNextRow): Intent =
    Intent(context, MainActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        .putExtra(AiringNotifier.EXTRA_OPEN_SOURCE, row.source)
        .putExtra(AiringNotifier.EXTRA_OPEN_ID, row.externalId)
        .putExtra(AiringNotifier.EXTRA_OPEN_TYPE, row.mediaType)
