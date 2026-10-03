package com.trackr.app.data.repository

import com.trackr.app.data.remote.supabase.ActivityDto
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SocialRepository @Inject constructor(private val supabase: SupabaseClient) {
    /** Friends (RLS-filtered through the friend_activity view) who have this title in their list. */
    suspend fun friendsWhoTracked(source: String, externalId: String): List<ActivityDto> =
        supabase.from("friend_activity").select {
            filter { eq("source", source); eq("external_id", externalId) }
            order("updated_at", Order.DESCENDING)
        }.decodeList()
}
