package com.trackr.app.data.repository

import com.trackr.app.data.mapper.ListEntryMapper
import com.trackr.app.data.mapper.ListEntryMapper.toEntity
import com.trackr.app.data.mapper.ListEntryMapper.toDomain
import com.trackr.app.data.remote.supabase.ActivityDto
import com.trackr.app.data.remote.supabase.ListEntryDto
import com.trackr.app.domain.model.ActivityEntry
import com.trackr.app.domain.model.Friend
import com.trackr.app.domain.model.FriendRequest
import com.trackr.app.domain.model.FriendshipRow
import com.trackr.app.domain.model.ListEntry
import com.trackr.app.domain.model.ListStatus
import com.trackr.app.domain.model.MediaSource
import com.trackr.app.domain.model.MediaType
import com.trackr.app.domain.model.Profile
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

class FriendshipException(message: String) : Exception(message)

fun ActivityDto.toDomain() = ActivityEntry(
    id, userId, username, avatarUrl, MediaSource.fromKey(source), externalId, MediaType.fromKey(mediaType), title, posterUrl,
    ListStatus.fromKey(status), rating, progress, totalEpisodes, ListEntryMapper.parseInstant(updatedAt),
)

@Singleton
class FriendsRepository @Inject constructor(
    private val supabase: SupabaseClient,
    private val auth: AuthRepository,
    private val profiles: ProfileRepository,
) {
    private fun me(): String = auth.currentUserId ?: throw FriendshipException("You're signed out.")

    private suspend fun myFriendships(status: String): List<FriendshipRow> =
        supabase.from("friendships").select { filter { eq("status", status) } }.decodeList()

    suspend fun pendingRequests(): List<FriendRequest> {
        val me = me()
        val rows = myFriendships("pending").filter { it.addresseeId == me }
        val byId = profiles.getProfiles(rows.map { it.requesterId }).associateBy { it.id }
        return rows.mapNotNull { r -> byId[r.requesterId]?.let { FriendRequest(r.id, it) } }
    }

    suspend fun friends(): List<Friend> {
        val me = me()
        val rows = myFriendships("accepted")
        val otherIds = rows.map { if (it.requesterId == me) it.addresseeId else it.requesterId }
        val byId = profiles.getProfiles(otherIds).associateBy { it.id }
        val watching = runCatching { watchingByUser() }.getOrDefault(emptyMap())
        return rows.mapNotNull { r ->
            val other = if (r.requesterId == me) r.addresseeId else r.requesterId
            byId[other]?.let { Friend(r.id, it, watching[other].orEmpty()) }
        }.sortedBy { it.profile.username.lowercase() }
    }

    private suspend fun watchingByUser(): Map<String, List<ActivityEntry>> =
        supabase.from("friend_activity").select {
            filter { eq("status", "watching") }
            order("updated_at", Order.DESCENDING)
            limit(200)
        }.decodeList<ActivityDto>().map { it.toDomain() }.groupBy { it.userId }

    suspend fun activity(limit: Long = 50): List<ActivityEntry> =
        supabase.from("friend_activity").select {
            order("updated_at", Order.DESCENDING)
            limit(limit)
        }.decodeList<ActivityDto>().map { it.toDomain() }

    /** Sends a request; the DB enforces no self-friending and one friendship per pair. */
    suspend fun sendRequest(target: Profile) {
        val me = me()
        if (target.id == me) throw FriendshipException("That's you!")
        val existing = supabase.from("friendships").select().decodeList<FriendshipRow>()
            .firstOrNull { (it.requesterId == target.id && it.addresseeId == me) || (it.requesterId == me && it.addresseeId == target.id) }
        if (existing != null) {
            throw FriendshipException(
                if (existing.status == "accepted") "You're already friends with ${target.username}."
                else if (existing.requesterId == me) "Request already sent to ${target.username}."
                else "${target.username} already sent you a request – accept it below.",
            )
        }
        try {
            supabase.from("friendships").insert(buildJsonObject {
                put("requester_id", me); put("addressee_id", target.id); put("status", "pending")
            })
        } catch (e: Exception) {
            throw FriendshipException(if (e.message.orEmpty().contains("duplicate", true)) "Request already exists." else e.userMessage())
        }
    }

    suspend fun accept(friendshipId: String) {
        supabase.from("friendships").update(buildJsonObject { put("status", "accepted") }) { filter { eq("id", friendshipId) } }
    }

    /** Decline a request or remove a friend. */
    suspend fun delete(friendshipId: String) {
        supabase.from("friendships").delete { filter { eq("id", friendshipId) } }
    }

    /** A friend's list (RLS only returns it for accepted friends). */
    suspend fun entriesOf(userId: String): List<ListEntry> =
        supabase.from("list_entries").select { filter { eq("user_id", userId) } }.decodeList<ListEntryDto>()
            .map { it.toEntity().toDomain() }

    suspend fun find(query: String): Profile? = profiles.findByUsernameOrCode(query)
}
