package com.example

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
import org.json.JSONObject
import java.net.URLEncoder

class ExampleProvider : MainAPI() {

    override var mainUrl = "https://archive.org"
    override var name = "Public Domain Movies"
    override val supportedTypes = setOf(TvType.Movie)
    override var lang = "en"
    override val hasMainPage = true

    override val mainPage = mainPageOf(
        Pair(
            "collection:feature_films AND mediatype:movies",
            "Public Domain Movies"
        )
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {

        val query = URLEncoder.encode(
            "${request.data} AND mediatype:movies",
            "UTF-8"
        )

        val url =
            "$mainUrl/advancedsearch.php?q=$query" +
            "&fl[]=identifier&fl[]=title&fl[]=year" +
            "&rows=24&page=$page&output=json" +
            "&sort[]=downloads+desc"

        val json = JSONObject(app.get(url).text)
        val docs = json
            .getJSONObject("response")
            .getJSONArray("docs")

        val results = mutableListOf<SearchResponse>()

        for (i in 0 until docs.length()) {
            val item = docs.getJSONObject(i)

            val id = item.optString("identifier")
            val title = item.optString("title")

            if (id.isBlank() || title.isBlank()) continue

            val year = item.optInt("year", 0).takeIf { it > 0 }

            results.add(
                newMovieSearchResponse(
                    title,
                    "$mainUrl/details/$id",
                    TvType.Movie,
                    false
                ) {
                    posterUrl = "$mainUrl/services/img/$id"
                    this.year = year
                }
            )
        }

        return newHomePageResponse(
            request.name,
            results,
            docs.length() > 0
        )
    }

    override suspend fun search(query: String): List<SearchResponse> {

        val encoded = URLEncoder.encode(
            "collection:feature_films AND mediatype:movies AND title:($query)",
            "UTF-8"
        )

        val url =
            "$mainUrl/advancedsearch.php?q=$encoded" +
            "&fl[]=identifier&fl[]=title&fl[]=year" +
            "&rows=30&output=json"

        val json = JSONObject(app.get(url).text)
        val docs = json
            .getJSONObject("response")
            .getJSONArray("docs")

        return buildList {
            for (i in 0 until docs.length()) {
                val item = docs.getJSONObject(i)

                val id = item.optString("identifier")
                val title = item.optString("title")

                if (id.isBlank() || title.isBlank()) continue

                val year = item.optInt("year", 0).takeIf { it > 0 }

                add(
                    newMovieSearchResponse(
                        title,
                        "$mainUrl/details/$id",
                        TvType.Movie,
                        false
                    ) {
                        posterUrl = "$mainUrl/services/img/$id"
                        this.year = year
                    }
                )
            }
        }
    }

    override suspend fun load(url: String): LoadResponse {

        val id = url.substringAfter("/details/")

        val json = JSONObject(
            app.get("$mainUrl/metadata/$id").text
        )

        val metadata = json.getJSONObject("metadata")

        val title = metadata.optString("title", id)
        val year = metadata.optInt("year", 0).takeIf { it > 0 }
        val description = metadata.optString("description", "")

        return newMovieLoadResponse(
            title,
            url,
            TvType.Movie,
            id
        ) {
            posterUrl = "$mainUrl/services/img/$id"
            this.year = year
            plot = description
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {

        val json = JSONObject(
            app.get("$mainUrl/metadata/$data").text
        )

        val files = json.getJSONArray("files")

        var found = false

        for (i in 0 until files.length()) {

            val file = files.getJSONObject(i)
            val name = file.optString("name")

            if (!name.lowercase().endsWith(".mp4")) continue

            val encodedName = URLEncoder
                .encode(name, "UTF-8")
                .replace("+", "%20")

            val videoUrl =
                "$mainUrl/download/$data/$encodedName"

            callback(
                newExtractorLink(
                    source = "Internet Archive",
                    name = "Internet Archive",
                    url = videoUrl,
                    type = ExtractorLinkType.VIDEO
                ) {
                    this.quality = Qualities.Unknown.value
                }
            )

            found = true
        }

        return found
    }
}
