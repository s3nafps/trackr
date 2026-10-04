package com.trackr.app.data.repository

import com.trackr.app.data.mapper.SocialMapper
import com.trackr.app.data.remote.supabase.ActivityDto
import com.trackr.app.data.remote.supabase.CommentDto
import com.trackr.app.data.remote.supabase.CommentRefDto
import com.trackr.app.data.remote.supabase.EntryIdDto
import com.trackr.app.data.remote.supabase.ReactionDto
import com.trackr.app.data.remote.supabase.RecommendationDto
import com.trackr.app.domain.model.Comment
import com.trackr.app.domain.model.EntrySocial
import com.trackr.app.domain.model.MediaItem
import com.trackr.app.domain.model.Recommendation
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

class SocialException(message: String) : Exception(message)

/**
 * Reactions, comments and recommendations. Visibility is enforced by RLS (the social_reactions Supabase migration):
 * entries, and so their reactions and comments, are only visible to their owner and accepted friends.
 */
@Singleton
class SocialRepository @Inject constructor(
    private val supabase: SupabaseClient,
    private val auth: AuthRepository,
    private val profiles: ProfileRepository,
) {
    val currentUserId: String? get() = auth.currentUserId

    private fun me(): String = auth.currentUserId ?: throw SocialException("You're signed out.")

    /** Friends (RLS-filtered through the friend_activity view) who have this title in their list. */
    suspend fun friendsWhoTracked(source: String, externalId: String): List<ActivityDto> =
        supabase.from("friend_activity").select {
            filter { eq("source", source); eq("external_id", externalId) }
            order("updated_at", Order.DESCENDING)
        }.decodeList()

    // ----- reactions & comments -----

    /** Reactions and comment counts for list entries (server ids, as in friend_activity). */
    suspend fun socialFor(entryIds: Collection<String>): Map<String, EntrySocial> {
        if (entryIds.isEmpty()) return emptyMap()
        val ids = entryIds.distinct()
        val reactions = supabase.from("entry_reactions").select { filter { isIn("entry_id", ids) } }.decodeList<ReactionDto>()
        val comments = supabase.from("entry_comments").select(Columns.list("entry_id")) { filter { isIn("entry_id", ids) } }
            .decodeList<CommentRefDto>()
        return SocialMapper.summarize(ids, reactions, comments.map { it.entryId }, auth.currentUserId)
    }

    suspend fun setReaction(entryId: String, emoji: String, on: Boolean) {
        val me = me()
        if (on) {
            try {
                supabase.from("entry_reactions").insert(buildJsonObject { put("entry_id", entryId); put("user_id", me); put("emoji", emoji) })
            } catch (e: Exception) {
                // Already reacted (another device, a double tap): the end state is what was asked for.
                if (!e.message.orEmpty().contains("duplicate", ignoreCase = true)) throw e
            }
        } else {
            supabase.from("entry_reactions").delete {
                filter { eq("entry_id", entryId); eq("user_id", me); eq("emoji", emoji) }
            }
        }
    }

    /** Comments on an entry, oldest first. [entryOwner] lets the owner delete any of them. */
    suspend fun comments(entryId: String, entryOwner: String?): List<Comment> {
        val rows = supabase.from("entry_comments").select {
            filter { eq("entry_id", entryId) }
            order("created_at", Order.ASCENDING)
            limit(MAX_COMMENTS)
        }.decodeList<CommentDto>()
        val byId = profiles.getProfiles(rows.map { it.userId }.distinct()).associateBy { it.id }
        return SocialMapper.comments(rows, byId, auth.currentUserId, entryOwner)
    }

    suspend fun addComment(entryId: String, body: String) {
        val text = body.trim()
        if (text.isEmpty()) return
        if (text.length > MAX_COMMENT_LENGTH) throw SocialException("Comments can be up to $MAX_COMMENT_LENGTH characters.")
        supabase.from("entry_comments").insert(buildJsonObject { put("entry_id", entryId); put("user_id", me()); put("body", text) })
    }

    suspend fun deleteComment(id: String) {
        supabase.from("entry_comments").delete { filter { eq("id", id) } }
    }

    /** The server id of one of your own list entries, once it has synced; null before that. */
    suspend fun myEntryId(source: String, externalId: String): String? =
        supabase.from("list_entries").select(Columns.list("id")) {
            filter { eq("user_id", me()); eq("source", source); eq("external_id", externalId) }
        }.decodeList<EntryIdDto>().firstOrNull()?.id

    // ----- recommendations -----

    /** Sends [item] to a friend; sending the same title to the same friend again is a no-op. */
    suspend fun recommend(toUser: String, item: MediaItem, note: String?) {
        val text = note?.trim()?.takeIf { it.isNotEmpty() }
        if (text != null && text.length > MAX_NOTE_LENGTH) throw SocialException("Notes can be up to $MAX_NOTE_LENGTH characters.")
        try {
            supabase.from("recommendations").insert(
                buildJsonObject {
                    put("from_user", me()); put("to_user", toUser)
                    put("source", item.source.key); put("external_id", item.externalId); put("media_type", item.type.key)
                    put("title", item.title.take(300)); put("poster_url", item.posterUrl); put("note", text)
                },
            )
        } catch (e: Exception) {
            if (!e.message.orEmpty().contains("duplicate", ignoreCase = true)) throw e
        }
    }

    /** Recommendations sent to you, newest first. */
    suspend fun inbox(): List<Recommendation> {
        val rows = supabase.from("recommendations").select {
            filter { eq("to_user", me()) }
            order("created_at", Order.DESCENDING)
            limit(MAX_INBOX)
        }.decodeList<RecommendationDto>()
        val byId = profiles.getProfiles(rows.map { it.fromUser }.distinct()).associateBy { it.id }
        return SocialMapper.recommendations(rows, byId)
    }

    suspend fun markSeen(id: String) {
        supabase.from("recommendations").update(buildJsonObject { put("seen", true) }) { filter { eq("id", id) } }
    }

    suspend fun deleteRecommendation(id: String) {
        supabase.from("recommendations").delete { filter { eq("id", id) } }
    }

    companion object {
        const val MAX_COMMENT_LENGTH = 500
        const val MAX_NOTE_LENGTH = 300
        private const val MAX_COMMENTS = 200L
        private const val MAX_INBOX = 50L
    }
}
