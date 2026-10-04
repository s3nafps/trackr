package com.trackr.app.data.remote.supabase

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ReactionDto(
    @SerialName("entry_id") val entryId: String,
    @SerialName("user_id") val userId: String,
    val emoji: String,
)

@Serializable
data class CommentDto(
    val id: String,
    @SerialName("entry_id") val entryId: String,
    @SerialName("user_id") val userId: String,
    val body: String,
    @SerialName("created_at") val createdAt: String,
)

/** Just the entry id of a comment, for counting. */
@Serializable
data class CommentRefDto(@SerialName("entry_id") val entryId: String)

@Serializable
data class RecommendationDto(
    val id: String,
    @SerialName("from_user") val fromUser: String,
    @SerialName("to_user") val toUser: String,
    val source: String,
    @SerialName("external_id") val externalId: String,
    @SerialName("media_type") val mediaType: String,
    val title: String,
    @SerialName("poster_url") val posterUrl: String? = null,
    val note: String? = null,
    val seen: Boolean = false,
    @SerialName("created_at") val createdAt: String,
)

/** A list entry's server id, to find reactions on one of your own entries. */
@Serializable
data class EntryIdDto(val id: String)

@Serializable
data class SharedListDto(
    val id: String,
    val name: String,
    @SerialName("owner_id") val ownerId: String,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
data class SharedMemberDto(
    @SerialName("list_id") val listId: String,
    @SerialName("user_id") val userId: String,
)

@Serializable
data class SharedItemDto(
    @SerialName("list_id") val listId: String,
    val source: String,
    @SerialName("external_id") val externalId: String,
    @SerialName("media_type") val mediaType: String,
    val title: String,
    @SerialName("poster_url") val posterUrl: String? = null,
    @SerialName("added_by") val addedBy: String? = null,
    @SerialName("created_at") val createdAt: String,
)

/** Just a list id, for "which of my lists has this title". */
@Serializable
data class ListIdDto(@SerialName("list_id") val listId: String)
