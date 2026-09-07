package com.example

import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.newMovieSearchResponse

class ExampleProvider : MainAPI() {

    override var mainUrl = "https://example.com/"
    override var name = "My CloudStream Provider"
    override val supportedTypes = setOf(TvType.Movie)
    override var lang = "en"
    override val hasMainPage = false

    override suspend fun search(query: String): List<SearchResponse> {
        return listOf(
            newMovieSearchResponse(
                "Test Movie",
                "$mainUrl",
                TvType.Movie,
                false
            ) {
                posterUrl = null
            }
        )
    }
}
// Build trigger
// Build retry
