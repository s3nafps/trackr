package com.trackr.app.data.repository

import com.trackr.app.data.mapper.SocialMapper
import com.trackr.app.data.remote.supabase.ListIdDto
import com.trackr.app.data.remote.supabase.SharedItemDto
import com.trackr.app.data.remote.supabase.SharedListDto
import com.trackr.app.data.remote.supabase.SharedMemberDto
import com.trackr.app.domain.model.MediaItem
import com.trackr.app.domain.model.SharedList
import com.trackr.app.domain.model.SharedListItem
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Shared watchlists. RLS (the shared_lists Supabase migration) limits everything to lists you're a member of; only
 * the owner adds or removes people, and only their accepted friends can be added.
 */
@Singleton
class SharedListsRepository @Inject constructor(
    private val supabase: SupabaseClient,
    private val auth: AuthRepository,
    private val profiles: ProfileRepository,
) {
    val currentUserId: String? get() = auth.currentUserId

    private fun me(): String = auth.currentUserId ?: throw SocialException("You're signed out.")

    /** Your shared lists, newest first, with members and a poster cover. */
    suspend fun lists(): List<SharedList> = load(null)

    suspend fun list(id: String): SharedList? = load(id).firstOrNull()

    private suspend fun load(only: String?): List<SharedList> {
        val lists = supabase.from("shared_lists").select { if (only != null) filter { eq("id", only) } }.decodeList<SharedListDto>()
        if (lists.isEmpty()) return emptyList()
        val ids = lists.map { it.id }
        val members = supabase.from("shared_list_members").select { filter { isIn("list_id", ids) } }.decodeList<SharedMemberDto>()
        val items = supabase.from("shared_list_items").select { filter { isIn("list_id", ids) } }.decodeList<SharedItemDto>()
        val byId = profiles.getProfiles(members.map { it.userId }.distinct()).associateBy { it.id }
        return SocialMapper.sharedLists(lists, members, items, byId)
    }

    /**
     * Creates a list (you become its first member) and adds [friendIds]. The id is made here so creating doesn't
     * depend on reading the row back. Returns the id and the friends who couldn't be added.
     */
    suspend fun create(name: String, friendIds: Collection<String>): Pair<String, List<String>> {
        val title = name.trim()
        if (title.isEmpty()) throw SocialException("Give the list a name.")
        if (title.length > MAX_NAME_LENGTH) throw SocialException("Names can be up to $MAX_NAME_LENGTH characters.")
        val id = UUID.randomUUID().toString()
        supabase.from("shared_lists").insert(buildJsonObject { put("id", id); put("name", title); put("owner_id", me()) })
        val failed = friendIds.distinct().filterNot { runCatching { addMember(id, it) }.isSuccess }
        return id to failed
    }

    suspend fun delete(listId: String) {
        supabase.from("shared_lists").delete { filter { eq("id", listId) } }
    }

    suspend fun leave(listId: String) = removeMember(listId, me())

    suspend fun addMember(listId: String, userId: String) {
        try {
            supabase.from("shared_list_members").insert(buildJsonObject { put("list_id", listId); put("user_id", userId) })
        } catch (e: Exception) {
            if (!e.message.orEmpty().contains("duplicate", ignoreCase = true)) throw e
        }
    }

    suspend fun removeMember(listId: String, userId: String) {
        supabase.from("shared_list_members").delete { filter { eq("list_id", listId); eq("user_id", userId) } }
    }

    /** A list's titles, newest first. */
    suspend fun items(listId: String): List<SharedListItem> {
        val rows = supabase.from("shared_list_items").select {
            filter { eq("list_id", listId) }
            order("created_at", Order.DESCENDING)
        }.decodeList<SharedItemDto>()
        val byId = profiles.getProfiles(rows.mapNotNull { it.addedBy }.distinct()).associateBy { it.id }
        return SocialMapper.sharedItems(rows, byId)
    }

    /** Adding a title that's already on the list is a no-op. */
    suspend fun addItem(listId: String, item: MediaItem) {
        try {
            supabase.from("shared_list_items").insert(
                buildJsonObject {
                    put("list_id", listId); put("source", item.source.key); put("external_id", item.externalId)
                    put("media_type", item.type.key); put("title", item.title.take(300)); put("poster_url", item.posterUrl)
                    put("added_by", me())
                },
            )
        } catch (e: Exception) {
            if (!e.message.orEmpty().contains("duplicate", ignoreCase = true)) throw e
        }
    }

    suspend fun removeItem(listId: String, item: MediaItem) {
        supabase.from("shared_list_items").delete {
            filter { eq("list_id", listId); eq("source", item.source.key); eq("external_id", item.externalId) }
        }
    }

    /** Ids of your lists that already contain [item]. */
    suspend fun listsContaining(item: MediaItem): Set<String> =
        supabase.from("shared_list_items").select(Columns.list("list_id")) {
            filter { eq("source", item.source.key); eq("external_id", item.externalId) }
        }.decodeList<ListIdDto>().map { it.listId }.toSet()

    companion object {
        const val MAX_NAME_LENGTH = 60
    }
}
