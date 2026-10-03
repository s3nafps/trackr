package com.trackr.app.di

import android.content.Context
import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.network.okHttpClient
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.trackr.app.BuildConfig
import com.trackr.app.data.remote.anilist.AniListRateLimiter
import com.trackr.app.data.remote.tmdb.TmdbApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.Cache
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides @Singleton
    fun json(): Json = Json { ignoreUnknownKeys = true; coerceInputValues = true; explicitNulls = false }

    @Provides @Singleton @Named("tmdb")
    fun tmdbOkHttp(@ApplicationContext ctx: Context): OkHttpClient = OkHttpClient.Builder()
        .cache(Cache(File(ctx.cacheDir, "http-tmdb"), 10L * 1024 * 1024))
        .connectTimeout(15, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            chain.proceed(
                chain.request().newBuilder()
                    .header("Authorization", "Bearer ${BuildConfig.TMDB_READ_TOKEN}")
                    .header("Accept", "application/json")
                    .build(),
            )
        }
        .build()

    @Provides @Singleton
    fun tmdbApi(@Named("tmdb") client: OkHttpClient, json: Json): TmdbApi = Retrofit.Builder()
        .baseUrl("https://api.themoviedb.org/3/")
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build().create(TmdbApi::class.java)

    @Provides @Singleton
    fun apollo(): ApolloClient = ApolloClient.Builder()
        .serverUrl("https://graphql.anilist.co")
        .okHttpClient(
            OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS)
                .addInterceptor(AniListRateLimiter())
                .build(),
        )
        .build()
}
