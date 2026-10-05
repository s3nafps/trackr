package com.trackr.app.di

import com.trackr.app.BuildConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SupabaseModule {
    @Provides
    @Singleton
    fun supabase(): SupabaseClient = createSupabaseClient(
        supabaseUrl = BuildConfig.SUPABASE_URL.ifBlank { "https://invalid.supabase.co" },
        supabaseKey = BuildConfig.SUPABASE_ANON_KEY.ifBlank { "invalid" },
    ) {
        install(Auth) {
            alwaysAutoRefresh = true
            autoLoadFromStorage = true
        }
        install(Postgrest)
        // Live friend activity, reactions and comments; connects only while a screen follows changes.
        install(Realtime)
    }
}
