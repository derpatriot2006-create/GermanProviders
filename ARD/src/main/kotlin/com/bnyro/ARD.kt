package com.bnyro

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities


open class ARD : MainAPI() {
    override var name = "ARD"
    override var lang = "de"
    override val hasQuickSearch = true
    override val hasMainPage = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)
    override var mainUrl = "https://api.ardmediathek.de"

    override val mainPage = mainPageOf(
        "1FdQ5oz2JK6o2qmyqMsqiI:7024894723483797725" to "Film-Empfehlungen",
        "d08a2b5f-8133-4bdd-8ea9-64b6172ce5ee:5379188208992982100" to "Aktuelle Debatten",
        "d08a2b5f-8133-4bdd-8ea9-64b6172ce5ee:-1083078599273736954" to "Exklusive Recherchen",
        "3JvraZLz6r8E9VJOSjxe0m:5345608557251872358" to "Derzeit beliebte Dokus",
        "3JvraZLz6r8E9VJOSjxe0m:3945748791191973508" to "Spannende Dokus und Reportagen",
        "3JvraZLz6r8E9VJOSjxe0m:-4951729414550313310" to "Dokumentarfilme"
    )

    private fun getImageUrl(url: String): String {
        return url.replace("{width}", "200")
    }

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val response = app.get("${mainUrl}/page-gateway/widgets/ard/editorials/${request.data}?pageSize=${PAGE_SIZE}")
            .parsed<Editorial>()

        return newHomePageResponse(request.name, response.teasers.mapNotNull { it.toSearchResponse() })
    }

    private fun Teaser.toSearchResponse(): SearchResponse? {
        return newMovieSearchResponse(
            name = this.mediumTitle ?: this.shortTitle ?: return null,
            url = EpisodeInfo(this.links?.target?.id ?: this.id).toJson(),
            type = TvType.Movie,
        ) {
            this.posterUrl =
                this@toSearchResponse.images.values.firstOrNull()?.src?.let { getImageUrl(it) }
            this.year = this@toSearchResponse.broadcastedOn?.take(4)?.toIntOrNull()
        }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun search(query: String): List<SearchResponse> {
        val response =
            app.get("$mainUrl/search-system/search/vods/ard?query=${query}&pageSize=${PAGE_SIZE}&platform=MEDIA_THEK&sortingCriteria=SCORE_DESC")
                .parsed<Search>()

        return response.teasers.mapNotNull { it.toSearchResponse() }
    }

    override suspend fun load(url: String): LoadResponse? {
        val episodeId = parseJson<EpisodeInfo>(url).id

        val response = app.get("${mainUrl}/page-gateway/pages/ard/item/${episodeId}?embedded=false&mcV6=true")
            .parsed<DetailsResponse>()

        val streamInfo = response.widgets.firstOrNull { it.type == "player_ondemand" }
        val embedded = streamInfo?.mediaCollection?.embedded
        return newMovieLoadResponse(
            name = response.title,
            url = url,
            type = TvType.Others,
            data = embedded?.toJson()
        ) {
            this.posterUrl = streamInfo?.image?.src?.let { getImageUrl(it) }
            this.plot = streamInfo?.synopsis
            this.duration = embedded?.meta?.durationSeconds?.div(60)?.toInt()
            this.comingSoon = embedded?.streams?.isEmpty() == true
            this.year = streamInfo?.broadcastedOn?.take(4)?.toIntOrNull()
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val embedded = parseJson<Embedded>(data)
        if (embedded.streams.isEmpty()) return false

        for (stream in embedded.streams) {
            for (media in stream.media) {
                callback.invoke(
                    ExtractorLink(
                        name = "ARD ${media.forcedLabel} (${stream.kind})",
                        source = "ARD",
                        quality = media.maxVresolutionPx?.toInt() ?: Qualities.Unknown.value,
                        url = media.url,
                        referer = mainUrl,
                        isM3u8 = media.url.endsWith("m3u8")
                    )
                )
            }
        }

        for (subtitle in embedded.subtitles) {
            for (source in subtitle.sources) {
                subtitleCallback.invoke(
                    SubtitleFile(lang = subtitle.languageCode.orEmpty(), url = source.url ?: continue)
                )
            }
        }

        return true
    }

    data class EpisodeInfo(val id: String)

    data class Search(
        val id: String,
        val teasers: List<Teaser> = emptyList(),
        val title: String,
        val type: String,
    )

    data class Teaser(
        val availableTo: String?,
        val broadcastedOn: String?,
        val duration: Double?,
        val id: String,
        val images: Map<String, Image> = emptyMap(),
        val longTitle: String?,
        val mediumTitle: String?,
        val shortTitle: String?,
        val show: Show?,
        val type: String?,
        val links: Links?
    )

    data class Links(
        val self: Link?,
        val target: Link?
    )

    data class Link(
        val id: String,
        val title: String,
    )

    data class Show(
        val id: String,
        val title: String,
        val shortSynopsis: String?,
        val synopsis: String?,
        val longSynopsis: String?,
        val coreId: String?,
        val groupingType: String?,
        val hasSeasons: Boolean?,
    )

    data class DetailsResponse(
        val fskRating: String?,
        val id: String?,
        val title: String,
        val widgets: List<Widget> = emptyList(),
    )

    data class Widget(
        val availableTo: String?,
        val blockedByFsk: Boolean?,
        val broadcastedOn: String?,
        val embeddable: Boolean?,
        val geoblocked: Boolean?,
        val id: String,
        val image: Image?,
        val maturityContentRating: String?,
        val mediaCollection: MediaCollection?,
        val show: ShowShort?,
        val synopsis: String?,
        val title: String,
        val type: String,
        @JsonProperty("aZContent")
        val aZcontent: Boolean?,
        val compilationType: String?,
        val size: String?,
        val swipeable: Boolean?,
        val titleVisible: Boolean?,
        val userVisibility: String?,
    )

    data class Image(
        val src: String?,
        val title: String?,
    )

    data class MediaCollection(
        val embedded: Embedded,
        val href: String,
    )

    data class Embedded(
        val id: String?,
        val isGeoBlocked: Boolean = false,
        val meta: Meta?,
        val streams: List<Stream> = emptyList(),
        val subtitles: List<Subtitle> = emptyList(),
    )

    data class Meta(
        val availableToDateTime: String?,
        val broadcastedOnDateTime: String?,
        val clipSourceName: String?,
        val durationSeconds: Long?,
        val seriesTitle: String?,
        val synopsis: String?,
        val title: String?,
    )

    data class Stream(
        val kind: String?,
        val kindName: String?,
        val media: List<Medum> = emptyList(),
        val videoLanguageCode: String?,
    )

    data class Medum(
        val aspectRatio: String?,
        val audios: List<Audio> = emptyList(),
        val forcedLabel: String?,
        val isAdaptiveQualitySelectable: Boolean = false,
        val isHighDynamicRange: Boolean = false,
        @JsonProperty("maxHResolutionPx")
        val maxHresolutionPx: Long?,
        @JsonProperty("maxVResolutionPx")
        val maxVresolutionPx: Long?,
        val mimeType: String? = null,
        val url: String,
        val videoCodec: String? = null,
    )

    data class Audio(
        val kind: String?,
        val languageCode: String?,
    )

    data class Subtitle(
        val kind: String?,
        val languageCode: String?,
        val sources: List<Source> = emptyList(),
    )

    data class Source(
        val kind: String?,
        val url: String?,
    )

    data class ShowShort(
        val id: String,
        val title: String,
        val image: Image?,
    )

    data class Editorial(
        @JsonProperty("aZContent")
        val aZcontent: Boolean,
        val compilationType: String,
        val id: String,
        val title: String,
        val size: String?,
        val teasers: List<Teaser> = emptyList(),
        val type: String?,
    )

    companion object {
        private const val PAGE_SIZE = 30
    }
}