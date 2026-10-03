package com.trackr.app.data.remote.supabase

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ListEntryDto(
    @SerialName("user_id") val userId: String,
    val source: String,
    @SerialName("external_id") val externalId: String,
    @SerialName("media_type") val mediaType: String,
    val title: String,
    @SerialName("poster_url") val posterUrl: String? = null,
    @SerialName("backdrop_url") val backdropUrl: String? = null,
    val status: String,
    val rating: Int? = null,
    val progress: Int = 0,
    @SerialName("total_episodes") val totalEpisodes: Int? = null,
    @SerialName("updated_at") val updatedAt: String,
    val notify: Boolean = false,
)

/** Row from the friend_activity view. */
@Serializable
data class ActivityDto(
    val id: String,
    @SerialName("user_id") val userId: String,
    val username: String,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    val source: String,
    @SerialName("external_id") val externalId: String,
    @SerialName("media_type") val mediaType: String,
    val title: String,
    @SerialName("poster_url") val posterUrl: String? = null,
    val status: String,
    val rating: Int? = null,
    val progress: Int = 0,
    @SerialName("total_episodes") val totalEpisodes: Int? = null,
    @SerialName("updated_at") val updatedAt: String,
)
