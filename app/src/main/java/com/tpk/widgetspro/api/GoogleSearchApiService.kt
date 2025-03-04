package com.tpk.widgetspro.api

import com.tpk.widgetspro.models.GoogleSearchResponse
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Query

interface GoogleSearchApiService {
    @GET("customsearch/v1")
    fun searchImages(
        @Query("key") apiKey: String,
        @Query("cx") searchEngineId: String,
        @Query("q") query: String,
        @Query("searchType") searchType: String,
        @Query("fileType") fileType: String? = null
    ): Call<GoogleSearchResponse>
}