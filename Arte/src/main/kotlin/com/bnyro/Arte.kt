package com.bnyro

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.fixUrl
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.M3u8Helper
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink

open class Arte : MainAPI() {
    override var name = "Arte"
    override var lang = "de"
    override val hasQuickSearch = true
    override val hasMainPage = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)
    override var mainUrl = "https://www.arte.tv"
    open var apiUrl = "https://api.arte.tv"
    open var client = "web"

    override val mainPage = mainPageOf(
        "AVN" to "Demnächst",
        "CIN" to "Filme",
        "SER" to "Serien",
        "HIS" to "Geschichte",
        "SCI" to "Wissenschaft",
        "CPO" to "Kultur und Pop",
        "DEC" to "Entdeckung der Welt",
        "ACT" to "Aktuelles und Gesellschaft"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? {
        val zones = app.get("$mainUrl/api/rproxy/emac/v4/de/$client/pages/${request.data}")
            .parsed<ZoneInfoResponse>()
            .value
            .zones
            .filter { it.content?.data.orEmpty().size >= 5 }

        val pages = zones.map { zone ->
            val items = zone.content!!.data.filter { it.kind.code != "TOPIC" }
            HomePageList(name = zone.title, list = items.map { it.toSearchResponse() })
        }

        return newHomePageResponse(pages, hasNext = false)
    }

    override suspend fun search(query: String): List<SearchResponse>? {
        val zoneId = app.get("$mainUrl/api/rproxy/emac/v4/de/$client/pages/SEARCH")
            .parsed<ZoneInfoResponse>()
            .value.zones.firstOrNull()?.id ?: return null

        val resp = app.get(
            "$mainUrl/api/rproxy/emac/v4/de/$client/zones/$zoneId/content?authorizedCountry=${lang.uppercase()}&page=1&query=$query"
        )
            .parsed<MediaListResponse>()

        return resp.value.data.filter { it.kind.code != "TOPIC" }.map { it.toSearchResponse() }
    }

    private fun ResultItem.toSearchResponse(): SearchResponse {
        return newMovieSearchResponse(
            type = if (isSeries(extractProgramId(url)!!)) TvType.TvSeries else TvType.Movie,
            url = fixUrl(this.url),
            name = this.title,
        ) {
            posterUrl = this@toSearchResponse.mainImage?.url?.replace("__SIZE__", "380x214")
            year = this@toSearchResponse.availability?.start?.take(4)?.toIntOrNull()
        }
    }

    private fun extractProgramId(url: String): String? {
        return Regex("/videos/(.+?)/").find(url)?.groupValues?.get(1)
    }

    private fun isSeries(programId: String): Boolean {
        return programId.startsWith("RC")
    }

    override suspend fun load(url: String): LoadResponse? {
        val programId = extractProgramId(url) ?: return null

        if (isSeries(programId)) {
            val firstProgramId = app.get("$apiUrl/api/player/v2/config/$lang/$programId")
                .parsed<PlayerConfigResponse>().data.attributes.metadata.config.replay!!.substringAfterLast(
                    "/"
                )

            val programInfo = app.get("$apiUrl/api/player/v2/playlist/de/$programId")
                .parsed<PlayerConfigResponse>().data.attributes

            val seriesInfo =
                app.get("$mainUrl/api/rproxy/emac/v4/$lang/$client/programs/$firstProgramId")
                    .parsed<ProgramInfo>()

            val episodes = seriesInfo.value.zones.filter {
                it.code.startsWith("program_playNext")
            }.flatMapIndexed { seasonIndex, zone ->
                zone.content.data.mapIndexed { index, episode ->
                    newEpisode(url = fixUrl(episode.url), initializer = {
                        this.name = episode.title
                        this.description = episode.shortDescription
                        this.posterUrl = episode.mainImage?.url?.replace("__SIZE__", "380x214")
                        this.season = seasonIndex + 1
                        this.episode = index + 1
                    })
                }
            }

            return newTvSeriesLoadResponse(
                episodes = episodes,
                type = TvType.TvSeries,
                url = url,
                name = programInfo.metadata.title
            ) {
                posterUrl = programInfo.metadata.images.firstOrNull()?.url
                plot = programInfo.metadata.description
                duration = programInfo.metadata.duration.seconds.div(60).toInt()
            }
        } else {
            val response = app.get("$apiUrl/api/player/v2/config/$lang/$programId")
                .parsed<PlayerConfigResponse>().data.attributes

            return newMovieLoadResponse(
                name = response.metadata.title,
                url = url,
                type = TvType.Movie,
                dataUrl = url
            ) {
                plot = response.metadata.description
                posterUrl = response.metadata.images.firstOrNull()?.url
                year = response.rights?.begin?.take(4)?.toIntOrNull()
                duration = response.metadata.duration.seconds.div(60).toInt()
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val programId = extractProgramId(data)
        val response = app.get("$apiUrl/api/player/v2/config/$lang/$programId")
            .parsed<PlayerConfigResponse>()

        for (stream in response.data.attributes.streams) {
            if (stream.protocol.contains("HLS")) {
                M3u8Helper.generateM3u8(
                    source = "Arte",
                    streamUrl = stream.url,
                    referer = mainUrl
                ).forEach(callback)
            }

            callback.invoke(
                newExtractorLink(
                    source = "Arte",
                    name = "Arte",
                    url = stream.url,
                ) {
                    quality = stream.mainQuality.label.takeWhile { it.isDigit() }.toIntOrNull()
                        ?: Qualities.Unknown.value
                }
            )
        }

        return response.data.attributes.streams.isNotEmpty()
    }

    data class PlayerConfigResponse(
        val data: Data,
    )

    data class Data(
        val id: String,
        val type: String,
        val attributes: Attributes,
    )

    data class Attributes(
        val provider: String,
        val metadata: Metadata,
        val live: Boolean,
        val rights: Rights?,
        val streams: StreamList = StreamList(),
    )

    data class Metadata(
        val providerId: String,
        val language: String,
        val title: String,
        val subtitle: String?,
        val description: String,
        val images: List<Image>,
        val link: Link,
        val config: Config,
        val duration: Duration,
        val episodic: Boolean,
    )

    data class Image(
        val url: String,
        val caption: String?,
    )

    data class Link(
        val url: String,
        val deeplink: String,
        val videoOnDemand: Any?,
        val replayUrl: Any?,
    )

    data class Config(
        val url: String,
        val replay: String?,
        val playlist: String,
    )

    data class Duration(
        val seconds: Long,
    )

    data class Rights(
        val begin: String,
        val end: String,
    )

    class StreamList : ArrayList<Stream>()

    data class Stream(
        val url: String,
        val versions: List<Version>,
        val mainQuality: Quality,
        val slot: Long,
        val protocol: String,
        val segments: List<Any?>,
        val externalId: Any?,
    )

    data class Version(
        val code: String,
        val label: String,
        val shortLabel: String,
        val audioLanguage: String,
        val subtitleLanguage: String,
        val closedCaptioning: Boolean,
        val audioDescription: Boolean,
    )

    data class Quality(
        val code: String,
        val label: String,
    )

    data class ZoneInfoResponse(
        val value: ZonePageValue,
    )

    data class ZonePageValue(
        val zones: List<Zone> = emptyList(),
    )

    data class Zone(
        val id: String,
        val title: String,
        val content: ZoneData?
    )

    data class ZoneData(
        val data: List<ResultItem>
    )

    data class MediaListResponse(
        val tag: String,
        val value: SearchValue,
    )

    data class SearchValue(
        val data: List<ResultItem> = emptyList(),
    )

    data class ResultItem(
        val deeplink: String?,
        val id: String,
        val kind: Kind,
        val mainImage: Image?,
        val shortDescription: String?,
        val stickers: List<Quality> = emptyList(),
        val subtitle: String?,
        val title: String,
        val trackingPixel: String,
        val type: String,
        val availability: Availability? = null,
        val duration: Long,
        val genre: Genre? = null,
        val programId: String?,
        val teaserText: String?,
        val url: String,
    )

    data class Kind(
        val code: String,
        val isCollection: Boolean,
        val label: String? = null,
    )

    data class Availability(
        val end: String,
        val start: String,
        val type: String,
        val upcomingDate: String,
        val hasVideoStreams: Boolean,
        val remainingDays: Long?,
    )

    data class Genre(
        val id: Long,
        val deeplink: String,
        val label: String,
        val genreName: String,
        val itemLabel: String,
        val url: String,
    )

    data class ProgramInfo(
        val tag: String,
        val value: ProgramValue,
    )

    data class ProgramValue(
        val code: String,
        val language: String,
        val support: String,
        val type: String,
        val level: Long,
        val alternativeLanguages: List<AlternativeLanguage>,
        val url: String,
        val deeplink: String,
        val slug: String,
        val stats: Stats,
        val metadata: ProgramMetadata,
        val zones: List<ProgramInfoZone>,
        val parent: Parent,
    )

    data class AlternativeLanguage(
        val code: String,
        val label: String,
        val page: String,
        val url: String,
        val title: String,
    )

    data class Stats(
        val xiti: Xiti,
        val serverSideTracking: ServerSideTracking,
    )

    data class Xiti(
        @JsonProperty("page_name")
        val pageName: String,
        val chapter1: String,
        val chapter2: String,
        val chapter3: Any?,
        val x1: String,
        val x2: String,
        val x4: String,
        val s2: Long,
        val siteId: String,
        @JsonProperty("env_work")
        val envWork: String,
        @JsonProperty("search_keywords")
        val searchKeywords: Any?,
    )

    data class ServerSideTracking(
        val page: Page,
        val content: Content,
    )

    data class Page(
        val id: String,
        val language: String,
        val url: String,
        val abv: String,
    )

    data class Content(
        val id: String,
        val slug: String,
        val category: String,
        val subcategory: String,
        val kind: String,
    )

    data class ProgramMetadata(
        val title: String,
        val description: String,
        val og: Og,
    )

    data class Og(
        val image: Image,
    )

    data class ProgramInfoZone(
        val id: String,
        val code: String,
        val title: String,
        val displayOptions: DisplayOptions,
        val link: Link?,
        val displayTeaserGenre: Boolean,
        val content: ZoneContent,
    )

    data class DisplayOptions(
        val template: String,
        val showZoneTitle: Boolean,
        val showItemTitle: Boolean,
    )

    data class ZoneContent(
        val data: List<ResultItem>,
    )

    data class Parent(
        val id: String,
        val label: String,
        val page: String,
        val type: String,
        val url: String,
        val deeplink: String,
        val slug: String,
    )
}