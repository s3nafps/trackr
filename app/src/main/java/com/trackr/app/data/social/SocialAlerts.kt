package com.trackr.app.data.social

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** One row of social_notifications(since): something a friend did that concerns you. */
@Serializable
data class SocialNotificationDto(
    val kind: String,
    val actor: String,
    val title: String? = null,
    val detail: String? = null,
    val source: String? = null,
    @SerialName("external_id") val externalId: String? = null,
    @SerialName("media_type") val mediaType: String? = null,
    val at: String,
)

/** Where tapping an alert goes. */
sealed interface AlertTarget {
    data object Friends : AlertTarget
    data class Title(val source: String, val type: String, val id: String) : AlertTarget
}

/** A notification to show; [key] is stable per group so a later alert for the same thing replaces it. */
data class SocialAlert(val key: String, val title: String, val text: String?, val target: AlertTarget)

/** Turns the feed into notifications: one per kind and title, naming who did it ("ann and 2 others"). */
object SocialAlerts {
    fun compose(rows: List<SocialNotificationDto>): List<SocialAlert> =
        rows.groupBy { Triple(it.kind, it.source, it.externalId) }.mapNotNull { (group, items) ->
            val (kind, source, id) = group
            val first = items.first()
            val who = people(items.map { it.actor }.distinct())
            val title = first.title.orEmpty()
            val titleTarget = if (source != null && id != null && first.mediaType != null) AlertTarget.Title(source, first.mediaType, id) else AlertTarget.Friends
            val key = listOfNotNull(kind, source, id).joinToString(":")
            when (kind) {
                "friend_request" -> SocialAlert(key, if (items.size == 1) "$who sent you a friend request" else "$who sent you friend requests", "Tap to accept or decline.", AlertTarget.Friends)
                "friend_accepted" -> SocialAlert(key, "$who accepted your friend request", "You can now see what they're watching.", AlertTarget.Friends)
                "comment" -> SocialAlert(key, "$who commented on $title", items.last().detail, titleTarget)
                "reaction" -> SocialAlert(key, "$who reacted to $title", items.mapNotNull { it.detail }.distinct().joinToString(" "), titleTarget)
                "recommendation" -> SocialAlert(key, "$who recommended $title", first.detail ?: "Tap to see it in your inbox.", AlertTarget.Friends)
                else -> null
            }
        }

    /** "ann", "ann and ben", "ann and 2 others". */
    fun people(names: List<String>): String = when (names.size) {
        0 -> "Someone"
        1 -> names[0]
        2 -> "${names[0]} and ${names[1]}"
        else -> "${names[0]} and ${names.size - 1} others"
    }
}
