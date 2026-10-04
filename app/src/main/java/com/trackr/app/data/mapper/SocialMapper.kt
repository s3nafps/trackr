package com.trackr.app.data.mapper

import com.trackr.app.data.remote.supabase.CommentDto
import com.trackr.app.data.remote.supabase.ReactionDto
import com.trackr.app.data.remote.supabase.RecommendationDto
import com.trackr.app.data.remote.supabase.SharedItemDto
import com.trackr.app.data.remote.supabase.SharedListDto
import com.trackr.app.data.remote.supabase.SharedMemberDto
import com.trackr.app.domain.model.Comment
import com.trackr.app.domain.model.EntrySocial
import com.trackr.app.domain.model.MediaItem
import com.trackr.app.domain.model.MediaSource
import com.trackr.app.domain.model.MediaType
import com.trackr.app.domain.model.Profile
import com.trackr.app.domain.model.Recommendation
import com.trackr.app.domain.model.SharedList
import com.trackr.app.domain.model.SharedListItem

object SocialMapper {
    /** One [EntrySocial] per id in [entryIds] (empty when nobody reacted or commented). */
    fun summarize(entryIds: Collection<String>, reactions: List<ReactionDto>, commentEntryIds: List<String>, me: String?): Map<String, EntrySocial> {
        val byEntry = reactions.groupBy { it.entryId }
        val comments = commentEntryIds.groupingBy { it }.eachCount()
        return entryIds.associateWith { id ->
            val rs = byEntry[id].orEmpty()
            EntrySocial(
                reactions = rs.groupingBy { it.emoji }.eachCount(),
                myReactions = rs.filter { it.userId == me }.map { it.emoji }.toSet(),
                commentCount = comments[id] ?: 0,
            )
        }
    }

    /** Comments oldest first; ones whose author's profile is gone are dropped. */
    fun comments(rows: List<CommentDto>, profiles: Map<String, Profile>, me: String?, entryOwner: String?): List<Comment> =
        rows.sortedBy { ListEntryMapper.parseInstant(it.createdAt) }.mapNotNull { c ->
            val p = profiles[c.userId] ?: return@mapNotNull null
            Comment(
                c.id, c.userId, p.username, p.avatarUrl, c.body, ListEntryMapper.parseInstant(c.createdAt),
                canDelete = me != null && (c.userId == me || entryOwner == me),
            )
        }

    fun recommendations(rows: List<RecommendationDto>, profiles: Map<String, Profile>): List<Recommendation> =
        rows.sortedByDescending { ListEntryMapper.parseInstant(it.createdAt) }.mapNotNull { r ->
            val from = profiles[r.fromUser] ?: return@mapNotNull null
            val source = MediaSource.entries.firstOrNull { it.key == r.source } ?: return@mapNotNull null
            val type = MediaType.entries.firstOrNull { it.key == r.mediaType } ?: return@mapNotNull null
            Recommendation(
                r.id, from, MediaItem(source, r.externalId, type, r.title, r.posterUrl), r.note?.takeIf { it.isNotBlank() },
                r.seen, ListEntryMapper.parseInstant(r.createdAt),
            )
        }

    /** Lists newest first; members owner-first then by name; covers from the four newest items with posters. */
    fun sharedLists(
        lists: List<SharedListDto>, members: List<SharedMemberDto>, items: List<SharedItemDto>, profiles: Map<String, Profile>,
    ): List<SharedList> {
        val membersBy = members.groupBy { it.listId }
        val itemsBy = items.groupBy { it.listId }
        return lists.sortedByDescending { ListEntryMapper.parseInstant(it.createdAt) }.map { l ->
            val listItems = itemsBy[l.id].orEmpty().sortedByDescending { ListEntryMapper.parseInstant(it.createdAt) }
            SharedList(
                id = l.id,
                name = l.name,
                ownerId = l.ownerId,
                members = membersBy[l.id].orEmpty().mapNotNull { profiles[it.userId] }
                    .sortedWith(compareBy<Profile> { it.id != l.ownerId }.thenBy { it.username.lowercase() }),
                itemCount = listItems.size,
                posters = listItems.mapNotNull { it.posterUrl }.take(4),
            )
        }
    }

    /** Items newest first; unknown media types are skipped, and a departed adder shows as nobody. */
    fun sharedItems(rows: List<SharedItemDto>, profiles: Map<String, Profile>): List<SharedListItem> =
        rows.sortedByDescending { ListEntryMapper.parseInstant(it.createdAt) }.mapNotNull { r ->
            val source = MediaSource.entries.firstOrNull { it.key == r.source } ?: return@mapNotNull null
            val type = MediaType.entries.firstOrNull { it.key == r.mediaType } ?: return@mapNotNull null
            SharedListItem(
                MediaItem(source, r.externalId, type, r.title, r.posterUrl),
                r.addedBy?.let { profiles[it] },
                ListEntryMapper.parseInstant(r.createdAt),
            )
        }
}
