package com.trackr.app.data.remote.tmdb

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface TmdbApi {
    @GET("trending/movie/week")
    suspend fun trendingMovies(@Query("page") page: Int = 1): TmdbPage<TmdbResult>

    @GET("trending/tv/week")
    suspend fun trendingTv(@Query("page") page: Int = 1): TmdbPage<TmdbResult>

    @GET("search/multi")
    suspend fun searchMulti(
        @Query("query") query: String,
        @Query("page") page: Int = 1,
        @Query("include_adult") includeAdult: Boolean = false,
    ): TmdbPage<TmdbResult>

    @GET("search/movie")
    suspend fun searchMovies(@Query("query") query: String, @Query("include_adult") includeAdult: Boolean = false): TmdbPage<TmdbResult>

    @GET("search/tv")
    suspend fun searchTv(@Query("query") query: String, @Query("include_adult") includeAdult: Boolean = false): TmdbPage<TmdbResult>

    @GET("movie/{id}?append_to_response=credits,release_dates")
    suspend fun movieDetail(@Path("id") id: Int): TmdbDetail

    @GET("tv/{id}?append_to_response=credits,content_ratings")
    suspend fun tvDetail(@Path("id") id: Int): TmdbDetail
}
