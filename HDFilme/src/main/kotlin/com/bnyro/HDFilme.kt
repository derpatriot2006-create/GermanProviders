package com.bnyro

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.nodes.Element

open class HDFilme : MainAPI() {
    override var name = "HDFilme"
    override var lang = "de"

    override val hasMainPage = true
    override val supportedTypes = setOf(TvType.Movie)
    override var mainUrl = "https://hdfilme.my"

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val doc = app.get(mainUrl).document

        return newHomePageResponse(request, doc.select("#dle-content div.item").map { it.toSearchResponse() })
    }

    private fun Element.toSearchResponse(): SearchResponse {
        val title = select("a.movie-title").text()
        val url = select("a.movie-title").attr("href")
        val posterPath = select("figure img").attr("src")
        val metaList = select("div.meta > span")

        return newMovieSearchResponse(title, type = TvType.Movie, url = url).apply {
            this.posterUrl = fixUrl(posterPath)
            this.year = metaList.firstOrNull()?.text()?.toIntOrNull()
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val doc = app.get("$mainUrl/?story=$query&do=search&subaction=search").document

        return doc.select("#dle-content div.item").map { it.toSearchResponse() }
    }

    override suspend fun load(url: String): LoadResponse? {
        val doc = app.get(url).document

        val details = doc.select("section.details")
        val title = details.select(".info h1").text()
        val posterPath = details.select("figure img").attr("src")
        val meta = details.select("h1 + div span:not(.divider)")
        val description = doc.select("section:has(> h2) > div > p").firstOrNull()?.text()
        val actors = details.select("li > span > a").map { it.text() }
        val streams = doc.select("#streams a").mapNotNull {
            Regex("(?<=')http.*(?=')").find(it.attr("onclick"))?.value
        }

        val related = doc.select("section.top-filme .listing a").map {
            newMovieSearchResponse(
                name = it.attr("title"),
                url = it.attr("href"),
                type = TvType.Movie
            ) {
                this.posterUrl = fixUrl(it.select("figure img").attr("src"))
            }
        }

        return newMovieLoadResponse(
            title.ifEmpty { return null },
            url,
            TvType.Movie,
            LoadData(streams).toJson()
        ) {
            this.posterUrl = fixUrl(posterPath)
            this.year = meta.getOrNull(3)?.text()?.toIntOrNull()
            this.plot = description
            this.tags = listOfNotNull(meta.firstOrNull()?.text())
            this.actors = actors.map { ActorData(Actor(it)) }
            this.recommendations = related
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val loadData = parseJson<LoadData>(data)
        if (loadData.links.isEmpty()) return false

        loadData.links.apmap {
            val link = fixUrlNull(it) ?: return@apmap null

            loadExtractor(
                link,
                "$mainUrl/",
                subtitleCallback,
                callback
            )
        }

        return true
    }

    data class LoadData(
        val links: List<String>
    )
}