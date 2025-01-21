package com.bnyro

import com.fasterxml.jackson.databind.JsonNode
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor


open class EinschaltenInProvider : MainAPI() {
    override var name = "EinschaltenIn"
    override var lang = "de"
    override val hasQuickSearch = true
    override val usesWebView = false
    override val hasMainPage = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)
    override var mainUrl = "https://einschalten.in"

    override val mainPage = mainPageOf(
        "public/events/recent" to "Recent events",
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val response = app.get("${mainUrl}/__data.json")
            .parsedSafe<Response>() ?: throw ErrorLoadingException()

        return newHomePageResponse(request.name, toSearchResponses(response))
    }

    private fun getImageUrl(file: String): String {
        return "$mainUrl/images/poster/$file"
    }

    private fun toSearchResponses(response: Response): List<SearchResponse> {
        val dataNode = response.nodes.firstOrNull { it.type == "data" } ?: return emptyList()
        val dataItems = dataNode.data.orEmpty().filterNotNull()
        val movies = dataItems.drop(dataItems.indexOfFirst { it.isArray } + 1).chunked(6)

        return movies.map { infoList ->
            newMovieSearchResponse(
                name = infoList[2].textValue(),
                url = "$mainUrl/movies/${infoList[1].textValue()}",
                type = TvType.Movie
            ) {
                this.posterUrl = getImageUrl(infoList[4].textValue())
                this.year = infoList[3].textValue().substring(0, 4).toIntOrNull()
            }
        }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun search(query: String): List<SearchResponse> {
        val response = app.get("${mainUrl}/__data.json?query=$query")
            .parsedSafe<Response>() ?: throw ErrorLoadingException()

        return toSearchResponses(response)
    }

    override suspend fun load(url: String): LoadResponse? {
        val response = app.get("$url/__data.json")
            .parsedSafe<Response>() ?: throw ErrorLoadingException()

        val dataNode = response.nodes.firstOrNull { it.type == "data" } ?: return null
        val dataList = dataNode.data.orEmpty().filterNotNull().dropWhile { it.isObject }

        return newMovieLoadResponse(
            name = dataList[1].textValue(),
            url = url,
            type = TvType.Movie,
            data = url
        ) {
            this.posterUrl = getImageUrl(dataList[6].textValue())
            this.plot = dataList[3].textValue()
            this.duration = dataList[5].intValue()
            this.year = dataList[4].textValue().substring(0, 4).toIntOrNull()
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val id = data.split("/").last()
        val source = app.get("https://einschalten.in/api/movies/$id/watch")
            .parsedSafe<StreamSource>() ?: throw ErrorLoadingException("Failed to extract link!")

        loadExtractor(source.streamUrl, referer = "$mainUrl/", subtitleCallback, callback)

        return true
    }

    data class Response(
        val type: String,
        val nodes: List<Node>,
    )

    data class Node(
        val type: String,
        val data: List<JsonNode?>?,
    )

    data class StreamSource(
        val releaseName: String,
        val streamUrl: String,
    )
}