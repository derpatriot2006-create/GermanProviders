package com.bnyro

import com.lagradost.cloudstream3.ErrorLoadingException
import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
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

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val response = app.get("${mainUrl}/__data.json")
            .parsedSafe<Response>() ?: throw ErrorLoadingException()

        return newHomePageResponse(HomePageList("Popular", toSearchResponses(response)))
    }

    private fun getImageUrl(file: String): String {
        return "$mainUrl/images/poster/$file"
    }

    private fun getData(response: Response): List<Any>? {
        return response.nodes.firstOrNull {
            it.type == "data" && it.data.orEmpty().filterNotNull().isNotEmpty()
        }?.data?.filterNotNull()
    }

    private fun toSearchResponses(response: Response): List<SearchResponse> {
        val dataItems = getData(response) ?: return emptyList()

        val infoListSize = 6
        val infoStream = dataItems.drop(dataItems.indexOfFirst { it is List<*> } + 1)
            .filter { it !is Map<*, *> || it.size == infoListSize - 1 }
        val movies = infoStream.chunked(infoListSize)
            .filter { it.size == infoListSize }

        return movies.map { infoList ->
            newMovieSearchResponse(
                name = infoList[2] as String,
                url = "$mainUrl/movies/${infoList[1] as Int}",
                type = TvType.Movie
            ) {
                this.posterUrl = getImageUrl(infoList[4] as String)
                this.year = (infoList[3] as String).substring(0, 4).toIntOrNull()
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

        val dataList = getData(response)?.dropWhile { it is Map<*, *> } ?: return null

        return newMovieLoadResponse(
            name = dataList[1] as String,
            url = url,
            type = TvType.Movie,
            data = url
        ) {
            this.posterUrl = getImageUrl(dataList[6] as String)
            this.plot = dataList[3] as String
            this.duration = dataList[5] as Int
            this.year = (dataList[4] as String).substring(0, 4).toIntOrNull()
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
        val data: List<Any?>?,
    )

    data class StreamSource(
        val releaseName: String,
        val streamUrl: String,
    )
}