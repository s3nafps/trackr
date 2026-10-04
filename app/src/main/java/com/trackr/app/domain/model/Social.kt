package com.trackr.app.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class FriendshipRow(
    val id: String,
    @SerialName("requester_id") val requesterId: String,
    @SerialName("addressee_id") val addresseeId: String,
    val status: String,
    @SerialName("created_at") val createdAt: String = "",
)

data class FriendRequest(val friendshipId: String, val from: Profile)

data class Friend(val friendshipId: String, val profile: Profile, val watching: List<ActivityEntry> = emptyList())

/** A friend's list entry as seen through the friend_activity view. */
data class ActivityEntry(
    val id: String,
    val userId: String,
    val username: String,
    val avatarUrl: String?,
    val source: MediaSource,
    val externalId: String,
    val mediaType: MediaType,
    val title: String,
    val posterUrl: String?,
    val status: ListStatus,
    val rating: Int?,
    val progress: Int,
    val totalEpisodes: Int?,
    val updatedAt: Long,
) {
    val key: String get() = "${source.key}:$externalId"

    /** "rated 9/10", "completed", "is watching", ... */
    val verb: String
        get() = when {
            rating != null && status == ListStatus.COMPLETED -> "rated"
            status == ListStatus.COMPLETED -> "completed"
            status == ListStatus.WATCHING -> "is watching"
            status == ListStatus.PLAN_TO_WATCH -> "plans to watch"
            else -> "dropped"
        }

    fun toMediaItem() = MediaItem(source, externalId, mediaType, title, posterUrl, totalEpisodes = totalEpisodes)
}

data class ProfileStats(
    val moviesWatched: Int,
    val tvShows: Int,
    val tvEpisodes: Int,
    val animeTitles: Int,
    val animeEpisodes: Int,
    val hours: Int,
    val averageRating: Double?,
    val ratingDistribution: List<Int>, // index 0 => rating 1
    val statusCounts: Map<ListStatus, Int>,
    val total: Int,
    val ratedCount: Int,
)

object StatsCalculator {
    const val MOVIE_MINUTES = 120
    const val TV_EPISODE_MINUTES = 45
    const val ANIME_EPISODE_MINUTES = 24

    fun compute(entries: List<ListEntry>): ProfileStats {
        val movies = entries.count { it.mediaType == MediaType.MOVIE && it.status == ListStatus.COMPLETED }
        val tv = entries.filter { it.mediaType == MediaType.TV && it.status != ListStatus.PLAN_TO_WATCH }
        val anime = entries.filter { it.mediaType == MediaType.ANIME && it.status != ListStatus.PLAN_TO_WATCH }
        val tvEps = tv.sumOf { it.progress }
        val animeEps = anime.sumOf { it.progress }
        val minutes = movies * MOVIE_MINUTES + tvEps * TV_EPISODE_MINUTES + animeEps * ANIME_EPISODE_MINUTES
        val ratings = entries.mapNotNull { it.rating }
        val dist = MutableList(10) { 0 }.also { d -> ratings.forEach { r -> if (r in 1..10) d[r - 1]++ } }
        return ProfileStats(
            moviesWatched = movies, tvShows = tv.size, tvEpisodes = tvEps, animeTitles = anime.size, animeEpisodes = animeEps,
            hours = minutes / 60,
            averageRating = ratings.takeIf { it.isNotEmpty() }?.average(),
            ratingDistribution = dist,
            statusCounts = ListStatus.entries.associateWith { s -> entries.count { it.status == s } },
            total = entries.size, ratedCount = ratings.size,
        )
    }

    /** Titles both users have in Plan to Watch ("Watch together"). */
    fun overlap(mine: List<ListEntry>, theirs: List<ListEntry>): List<ListEntry> {
        val other = theirs.filter { it.status == ListStatus.PLAN_TO_WATCH }.map { it.key }.toSet()
        return mine.filter { it.status == ListStatus.PLAN_TO_WATCH && it.key in other }
    }
}

/** The reactions people can leave on an activity entry, in display order (matches the DB check constraint). */
val REACTIONS = listOf("🔥", "❤️", "😂", "😮", "👏")

/** Reactions and comment count on one list entry, from the caller's point of view. */
data class EntrySocial(
    val reactions: Map<String, Int> = emptyMap(),
    val myReactions: Set<String> = emptySet(),
    val commentCount: Int = 0,
) {
    fun toggled(emoji: String): EntrySocial {
        val on = emoji !in myReactions
        val count = (reactions[emoji] ?: 0) + if (on) 1 else -1
        return copy(
            reactions = if (count > 0) reactions + (emoji to count) else reactions - emoji,
            myReactions = if (on) myReactions + emoji else myReactions - emoji,
        )
    }
}

data class Comment(
    val id: String,
    val userId: String,
    val username: String,
    val avatarUrl: String?,
    val body: String,
    val createdAt: Long,
    /** Its author, or the owner of the entry it's on. */
    val canDelete: Boolean,
)

/** A title a friend sent you. */
data class Recommendation(
    val id: String,
    val from: Profile,
    val item: MediaItem,
    val note: String?,
    val seen: Boolean,
    val createdAt: Long,
)

/** A watchlist several friends share. [posters] are a few recent items' posters for the cover. */
data class SharedList(
    val id: String,
    val name: String,
    val ownerId: String,
    val members: List<Profile>,
    val itemCount: Int,
    val posters: List<String> = emptyList(),
)

data class SharedListItem(val item: MediaItem, val addedBy: Profile?, val addedAt: Long)
