package com.trackr.app.data.realtime

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

/** Social tables whose changes the app follows live (see migration 20261006000001_realtime). */
object LiveTables {
    const val REACTIONS = "entry_reactions"
    const val COMMENTS = "entry_comments"
    const val RECOMMENDATIONS = "recommendations"
    const val FRIENDSHIPS = "friendships"
    const val LIST_ENTRIES = "list_entries"
    const val SHARED_LIST_ITEMS = "shared_list_items"
    const val SHARED_LIST_MEMBERS = "shared_list_members"
}

/**
 * Supabase Realtime, used as a "something changed" signal: screens reload what they show when it fires, so row-level
 * security still decides what each user reads (Realtime checks it too, except for deletes, which only carry ids).
 */
@Singleton
class LiveUpdates @Inject constructor(private val supabase: SupabaseClient) {
    private val channels = AtomicInteger()

    /**
     * Emits once per change to any of [tables], for as long as it is collected. The channel is closed when collection
     * stops; a dropped connection is retried with backoff (up to a minute), so callers never see an error.
     */
    fun changes(vararg tables: String): Flow<Unit> = flow {
        val channel = supabase.channel("live-${channels.incrementAndGet()}")
        // Bindings must exist before subscribing: they are sent with the join.
        val events = tables.map { t -> channel.postgresChangeFlow<PostgresAction>(schema = "public") { table = t } }
        try {
            channel.subscribe()
            emitAll(merge(*events.toTypedArray()).map { })
        } finally {
            withContext(NonCancellable) { runCatching { supabase.realtime.removeChannel(channel) } }
        }
    }.retryWhen { cause, attempt ->
        if (cause is CancellationException) return@retryWhen false
        delay(minOf(60_000L, 2_000L shl attempt.toInt().coerceAtMost(5)))
        true
    }
}
