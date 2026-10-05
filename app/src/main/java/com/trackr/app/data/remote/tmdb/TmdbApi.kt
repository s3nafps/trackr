package com.trackr.app.data.remote.tmdb

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface TmdbApi {
    @GET("trending/movie/week")
    suspend fun trendingMovies(@Query("page") page: Int = 1): TmdbPage<TmdbResult>

    @GET("trending/tv/week")
    suspend fun trendingTv(@Query("page") page: Int = 1): TmdbPage<TmdbResult>

    /** Popular titles with enough votes to be worth suggesting. */
    @GET("discover/movie")
    suspend fun discoverMovies(
        @Query("page") page: Int = 1,
        @Query("sort_by") sortBy: String = "popularity.desc",
        @Query("vote_count.gte") minVotes: Int = 200,
        @Query("include_adult") includeAdult: Boolean = false,
        /** A TMDB movie genre id; null leaves the parameter out. */
        @Query("with_genres") withGenres: String? = null,
    ): TmdbPage<TmdbResult>

    /** Like [discoverMovies]; news (10763) and talk shows (10767) are left out. */
    @GET("discover/tv")
    suspend fun discoverTv(
        @Query("page") page: Int = 1,
        @Query("sort_by") sortBy: String = "popularity.desc",
        @Query("vote_count.gte") minVotes: Int = 200,
        @Query("without_genres") withoutGenres: String = "10763,10767",
        @Query("include_adult") includeAdult: Boolean = false,
        /** A TMDB TV genre id; null leaves the parameter out. */
        @Query("with_genres") withGenres: String? = null,
    ): TmdbPage<TmdbResult>

    @GET("search/multi")
    suspend fun searchMulti(
        @Query("query") query: String,
        @Query("page") page: Int = 1,
        @Query("include_adult") includeAdult: Boolean = false,
    ): TmdbPage<TmdbResult>

    @GET("search/movie")
    suspend fun searchMovies(
        @Query("query") query: String,
        @Query("include_adult") includeAdult: Boolean = false,
        @Query("year") year: Int? = null,
        @Query("page") page: Int = 1,
    ): TmdbPage<TmdbResult>

    @GET("search/tv")
    suspend fun searchTv(
        @Query("query") query: String,
        @Query("include_adult") includeAdult: Boolean = false,
        @Query("page") page: Int = 1,
    ): TmdbPage<TmdbResult>

    @GET("movie/{id}?append_to_response=credits,release_dates,watch/providers,videos,recommendations,similar")
    suspend fun movieDetail(@Path("id") id: Int): TmdbDetail

    @GET("tv/{id}?append_to_response=credits,content_ratings,watch/providers,videos,recommendations,similar")
    suspend fun tvDetail(@Path("id") id: Int): TmdbDetail
}
