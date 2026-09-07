package com.example

import com.lagradost.cloudstream3.*
import org.json.JSONArray
import org.json.JSONObject
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
import java.net.URLEncoder

class MovieBoxWebProvider : MainAPI() {

    override var mainUrl = "https://officialmoviebox.com"
    override var name = "MovieBox Web"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)
    override var lang = "en"
    override val hasMainPage = true

    override val mainPage = mainPageOf(
        Pair("/", "Trending"),
        Pair("/newWeb/movie", "Movies"),
        Pair("/newWeb/tv-series", "TV Series")
    )

    private fun absolute(path: String): String {
        return if (path.startsWith("http")) {
            path
        } else {
            "$mainUrl$path"
        }
    }

    private fun parsePosts(html: String): List<SearchResponse> {
        val results = mutableListOf<SearchResponse>()

        val regex = Regex(
            """href=["'](/moviesDetail/[^"']+)["'][^>]*>(.*?)</a>""",
            RegexOption.IGNORE_CASE
        )

        val seen = mutableSetOf<String>()

        for (match in regex.findAll(html)) {
            val link = match.groupValues[1]

            if (!seen.add(link)) continue

            val rawTitle = match.groupValues[2]
                .replace(Regex("<[^>]+>"), "")
                .replace("&amp;", "&")
                .replace("&quot;", "\"")
                .trim()

            if (rawTitle.isBlank()) continue

            results.add(
                newMovieSearchResponse(
                    rawTitle,
                    absolute(link),
                    TvType.Movie,
                    false
                )
            )
        }

        return results
    }

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {

        val url = if (request.data == "/") {
            mainUrl
        } else {
            "$mainUrl${request.data}"
        }

        val response = app.get(url)

        return newHomePageResponse(
            request.name,
            parsePosts(response.text),
            true
        )
    }

    override suspend fun search(query: String): List<SearchResponse> {

        val encoded = URLEncoder.encode(
            query.trim(),
            "UTF-8"
        )

        val url =
            "$mainUrl/newWeb/searchResult?keyword=$encoded"

        return parsePosts(
            app.get(url).text
        )
    }

    override suspend fun load(url: String): LoadResponse {

        val response = app.get(url)
        val html = response.text

        val title = Regex(
            """<title[^>]*>(.*?)</title>""",
            RegexOption.IGNORE_CASE
        )
            .find(html)
            ?.groupValues
            ?.getOrNull(1)
            ?.replace(Regex("\\s*[-|].*$"), "")
            ?.trim()
            ?: "MovieBox"

        val description = Regex(
            """"description"\s*:\s*"([^"]*)"""",
            RegexOption.IGNORE_CASE
        )
            .find(html)
            ?.groupValues
            ?.getOrNull(1)
            ?.replace("\\n", "\n")
            ?.replace("\\\"", "\"")
            ?: ""

        val image = Regex(
            """"(?:cover|coverUrl|image|poster)"\s*:\s*\{\s*"url"\s*:\s*"([^"]+)"""",
            RegexOption.IGNORE_CASE
        )
            .find(html)
            ?.groupValues
            ?.getOrNull(1)

        val isSeries =
            url.contains("/tv/", true) ||
            html.contains("subjectType", true) &&
            html.contains("\"subjectType\":2", true)

    return if (isSeries) {
        newTvSeriesLoadResponse(
            title,
            url,
            TvType.TvSeries,
            emptyList()
        ) {
            plot = description
            posterUrl = image
        }
    } else {
        newMovieLoadResponse(
            title,
            url,
            TvType.Movie,
            title
        ) {
            plot = description
            posterUrl = image
        }
}
}
}
