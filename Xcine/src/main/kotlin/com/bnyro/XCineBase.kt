package com.bnyro

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.loadExtractor

abstract class XCineBase : MainAPI() {
    override var name = "XCine"
    override var lang = "de"
    override val hasQuickSearch = true
    override val usesWebView = false
    override val hasMainPage = true
    override val supportedTypes = setOf(TvType.TvSeries, TvType.Movie)
    abstract val mainAPI: String

    override val mainPage = mainPageOf(
        "data/browse/?lang=2&keyword=&year=&rating=&votes=&genre=&country=&cast=&directors=&type=movies&order_by=trending" to "Trending",
        "data/browse/?lang=2&keyword=&year=&rating=&votes=&genre=&country=&cast=&directors=&type=movies&order_by=Views" to "Most View Filme",
        "data/browse/?lang=2&keyword=&year=&rating=&votes=&genre=&country=&cast=&directors=&type=tvseries&order_by=Trending" to "Trending Serien",
        "data/browse/?lang=2&keyword=&year=&rating=&votes=&genre=&country=&cast=&directors=&type=movies&order_by=Updates" to "Updated Filme",
        "data/browse/?lang=2&keyword=&year=&rating=&votes=&genre=&country=&cast=&directors=&type=tvseries&order_by=Updates" to "Updated Serien",
    )

    private fun getImageUrl(link: String?): String? {
        if (link == null) return null

        return if (link.startsWith("/")) "https://image.tmdb.org/t/p/w500/$link" else link
    }

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val home =
            app.get("$mainAPI/${request.data}&page=$page", referer = "$mainUrl/")
                .parsedSafe<MediaResponse>()?.movies?.mapNotNull { res ->
                    res.toSearchResponse()
                } ?: throw ErrorLoadingException("Failed to parse Homepage.")
        return newHomePageResponse(request.name, home)
    }

    private fun Media.toSearchResponse(): SearchResponse? {
        return newAnimeSearchResponse(
            title ?: originalTitle ?: return null,
            LinkData(id).toJson(),
            TvType.TvSeries,
            false
        ) {
            this.posterUrl = getImageUrl(posterPath ?: backdropPath)
            addDub(lastUpdatedEpi?.toIntOrNull())
            addSub(totalEpisodes?.toIntOrNull())
        }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun search(query: String): List<SearchResponse> {
        val res = app.get("$mainAPI/data/browse/?lang=2&keyword=$query", referer = "$mainUrl/").text
        return tryParseJson<MediaResponse>(res)?.movies?.mapNotNull {
            it.toSearchResponse()
        } ?: throw ErrorLoadingException("Failed to parse search response.")
    }

    override suspend fun load(url: String): LoadResponse? {
        val id = parseJson<LinkData>(url).id

        val requestUrl = "$mainAPI/data/watch/?_id=$id"
        val res = app.get(requestUrl, referer = "$mainUrl/")
            .parsedSafe<MediaDetail>() ?: throw ErrorLoadingException("Failed to get and parse $requestUrl")
        val type = if (res.tv == 1) "tv" else "movie"

        val recommendations =
            app.get("$mainAPI/data/related_movies/?lang=2&cat=$type&_id=$url&server=0").text.let {
                tryParseJson<List<Media>>(it)
            }?.mapNotNull {
                it.toSearchResponse()
            }

        return if (type == "tv") {
            val episodes = res.streams?.groupBy { it.e }?.mapNotNull { eps ->
                val epsLink = eps.value.map { it.stream }.toJson()

                newEpisode(epsLink) {
                    this.episode = eps.key
                    this.name = eps.value.firstOrNull()?.eTitle
                }
            }.orEmpty()

            newTvSeriesLoadResponse(
                res.title ?: return null,
                requestUrl,
                TvType.TvSeries,
                episodes
            ) {
                this.posterUrl = getImageUrl(res.backdropPath ?: res.posterPath)
                this.year = res.year
                this.plot = res.storyline ?: res.overview
                this.tags = listOf(res.genres ?: "")
                this.actors = res.cast?.map { ActorData(Actor(it)) }
                this.contentRating = res.rating
                this.recommendations = recommendations
            }
        } else {
            newMovieLoadResponse(
                res.title ?: return null,
                requestUrl,
                TvType.Movie,
                res.streams?.map { it.stream }?.toJson()
            ) {
                this.posterUrl = getImageUrl(res.backdropPath ?: res.posterPath)
                this.year = res.year
                this.plot = res.storyline ?: res.overview
                this.tags = listOf(res.genres ?: "")
                this.actors = res.cast?.map { ActorData(Actor(it)) }
                this.contentRating = res.rating
                this.recommendations = recommendations
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val loadData = parseJson<List<String>>(data)

        loadData.apmap {
            val link = fixUrlNull(it) ?: return@apmap null
            if (link.startsWith("https://dl.streamcloud")) {
                callback.invoke(
                    ExtractorLink(
                        this.name,
                        this.name,
                        link,
                        "",
                        Qualities.Unknown.value
                    )
                )
            } else {
                loadExtractor(
                    link,
                    "$mainUrl/",
                    subtitleCallback,
                    callback
                )
            }
        }

        return true
    }

    data class LinkData(
        val id: String? = null,
    )

    data class Streams(
        @JsonProperty("_id") val id: String? = null,
        @JsonProperty("stream") val stream: String? = null,
        @JsonProperty("e") val e: Int? = null,
        @JsonProperty("e_title") val eTitle: String? = null,
    )

    data class MediaDetail(
        @JsonProperty("_id") val id: String? = null,
        @JsonProperty("tv") val tv: Int? = null,
        @JsonProperty("title") val title: String? = null,
        @JsonProperty("poster_path") val posterPath: String? = null,
        @JsonProperty("backdrop_path") val backdropPath: String? = null,
        @JsonProperty("year") val year: Int? = null,
        @JsonProperty("rating") val rating: String? = null,
        @JsonProperty("genres") val genres: String? = null,
        @JsonProperty("storyline") val storyline: String? = null,
        @JsonProperty("overview") val overview: String? = null,
        @JsonProperty("streams") val streams: ArrayList<Streams>? = arrayListOf(),
        @JsonProperty("cast") val cast: ArrayList<String>? = arrayListOf(),
    )

    data class Media(
        @JsonProperty("_id") val id: String? = null,
        @JsonProperty("original_title") val originalTitle: String? = null,
        @JsonProperty("title") val title: String? = null,
        @JsonProperty("poster_path") val posterPath: String? = null,
        @JsonProperty("backdrop_path") val backdropPath: String? = null,
        @JsonProperty("img") val img: String? = null,
        @JsonProperty("imdb_id") val imdbId: String? = null,
        @JsonProperty("totalEpisodes") val totalEpisodes: String? = null,
        @JsonProperty("last_updated_epi") val lastUpdatedEpi: String? = null,
    )

    data class MediaResponse(
        @JsonProperty("movies") val movies: ArrayList<Media>? = arrayListOf(),
    )
}