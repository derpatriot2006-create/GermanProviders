package com.bnyro

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.ExtractorLink


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
            .parsed<Editorial>() ?: throw ErrorLoadingException()

        return newHomePageResponse(request.name, response.teasers.map { it.toSearchResponse() })
    }

    private fun Teaser.toSearchResponse(): SearchResponse {
        return newMovieSearchResponse(
            name = this.mediumTitle,
            url = EpisodeInfo(this.id).toJson(),
            type = TvType.Movie,
        ) {
            this.posterUrl =
                this@toSearchResponse.images.values.firstOrNull()?.src?.let { getImageUrl(it) }
            this.year = this@toSearchResponse.broadcastedOn.take(4).toIntOrNull()
        }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun search(query: String): List<SearchResponse> {
        val response =
            app.get("$mainUrl/search-system/search/vods/ard?query=${query}&pageSize=${PAGE_SIZE}&platform=MEDIA_THEK&sortingCriteria=SCORE_DESC")
                .parsed<Search>() ?: throw ErrorLoadingException()

        return response.teasers.map { it.toSearchResponse() }
    }

    override suspend fun load(url: String): LoadResponse? {
        val episodeId = parseJson<EpisodeInfo>(url).id

        val response = app.get("${mainUrl}page-gateway/pages/ard/item/${episodeId}")
            .parsedSafe<DetailsResponse>() ?: throw ErrorLoadingException()

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
                        quality = media.maxVresolutionPx.toInt(),
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
                    SubtitleFile(lang = subtitle.languageCode, url = source.url)
                )
            }
        }

        return true
    }

    data class EpisodeInfo(val id: String)

    data class Search(
        val id: String,
        val isChildContent: Boolean,
        val pagination: Pagination,
        val personalized: Boolean,
        val links: Links,
        val size: String,
        val teasers: List<Teaser>,
        val title: String,
        val titleVisible: Boolean,
        val type: String,
    )

    data class Links(
        val self: Self,
    )

    data class Self(
        val partner: String,
        val id: String,
        val urlId: String,
        val title: String,
        val href: String,
        val type: String,
    )

    data class Teaser(
        val availableTo: String,
        val binaryFeatures: List<String>,
        val broadcastedOn: String,
        val coreAssetType: String,
        val duration: Double,
        val id: String,
        val images: Map<String, Image>,
        val isChildContent: Boolean,
        val isFamilyFriendly: Boolean,
        val longTitle: String,
        val maturityContentRating: String,
        val mediumTitle: String,
        val personalized: Boolean,
        val playtime: Any?,
        val publicationService: PublicationService,
        val links: Links,
        val shortTitle: String,
        val show: Show,
        val subtitled: Boolean,
        val titleVisible: Boolean,
        val type: String,
    )

    data class PublicationService(
        val name: String,
        val logo: Logo,
        val publisherType: String,
        val partner: String,
        val id: String,
        val coreId: String,
    )

    data class Logo(
        val title: String,
        val alt: String,
        val producerName: String,
        val src: String,
        val aspectRatio: String,
    )

    data class Show(
        val id: String,
        val coremediaId: Double,
        val title: String,
        val publisher: Publisher,
        val self: Self3,
        val images: Map<String, Image>,
        val shortSynopsis: String,
        val synopsis: String,
        val longSynopsis: String,
        val modificationDate: Any?,
        val assetSource: Any?,
        val isChildContent: Boolean,
        val isFamilyFriendly: Boolean,
        val targetAudienceAgeMin: Any?,
        val targetAudienceAgeMax: Any?,
        val categories: Any?,
        val categoryIds: Any?,
        val coreAssetType: String,
        val coreId: String,
        val groupingType: String,
        val homepage: Homepage,
        val hasSeasons: Boolean,
        val availableSeasons: Any?,
        val binaryFeatures: List<String>,
    )

    data class Publisher(
        val name: String,
        val logo: Logo2,
        val publisherType: String,
        val partner: String,
        val id: String,
        val coreId: String,
    )

    data class Logo2(
        val title: String,
        val alt: String,
        val producerName: String,
        val src: String,
        val aspectRatio: String,
    )

    data class Self3(
        val id: Any?,
        val title: String,
        val href: String,
        val type: String,
    )

    data class Homepage(
        val id: Any?,
        val title: String,
        val href: String,
        val type: Any?,
    )

    data class DetailsResponse(
        val coreAssetType: String,
        val fskRating: String,
        val id: String,
        val isChildContent: Boolean,
        val isFamilyFriendly: Boolean,
        val personalized: Boolean,
        val links: Links,
        val targetAudienceAgeMax: Any?,
        val targetAudienceAgeMin: Any?,
        val title: String,
        val widgets: List<Widget>,
    )

    data class Widget(
        val availableTo: String?,
        val binaryFeatures: List<String>?,
        val blockedByFsk: Boolean?,
        val broadcastedOn: String?,
        val embeddable: Boolean?,
        val geoblocked: Boolean?,
        val id: String,
        val image: Image?,
        val isChildContent: Boolean,
        val maturityContentRating: String?,
        val mediaCollection: MediaCollection?,
        val pagination: Pagination?,
        val personalized: Boolean,
        val playerConfig: Any?,
        val publicationService: PublicationService2?,
        val links: Links,
        val show: ShowShort?,
        val synopsis: String?,
        val title: String,
        val type: String,
        @JsonProperty("aZContent")
        val aZcontent: Boolean?,
        val compilationType: String?,
        val size: String?,
        val swipeable: Boolean?,
        val teasers: List<Any?>?,
        val titleVisible: Boolean?,
        val userVisibility: String?,
    )

    data class Image(
        val alt: String,
        val producerName: String,
        val src: String,
        val title: String,
    )

    data class MediaCollection(
        val embedded: Embedded,
        val href: String,
    )

    data class Embedded(
        val id: String,
        val isGeoBlocked: Boolean,
        val meta: Meta,
        val pluginData: PluginData,
        val streams: List<Stream>,
        val subtitles: List<Subtitle>,
    )

    data class Meta(
        val availableToDateTime: String,
        val broadcastedOnDateTime: String,
        val clipSourceName: String,
        val durationSeconds: Long,
        val images: List<Image>,
        val maturityContentRating: MaturityContentRating,
        val publicationService: PublicationService,
        val seriesTitle: String,
        val synopsis: String,
        val title: String,
    )

    data class MaturityContentRating(
        val age: Long,
        val isBlocked: Boolean,
        val kind: String,
    )

    data class PluginData(
        @JsonProperty("recommendation@all")
        val recommendationAll: RecommendationAll,
    )

    data class RecommendationAll(
        val isAutoplay: Boolean,
        val timerSeconds: Long,
        val url: String,
    )

    data class Stream(
        val kind: String,
        val kindName: String,
        val media: List<Medum>,
        val videoLanguageCode: String,
    )

    data class Medum(
        val aspectRatio: String,
        val audios: List<Audio>,
        val forcedLabel: String,
        val hasEmbeddedSubtitles: Boolean,
        val isAdaptiveQualitySelectable: Boolean,
        val isHighDynamicRange: Boolean,
        @JsonProperty("maxHResolutionPx")
        val maxHresolutionPx: Long,
        @JsonProperty("maxVResolutionPx")
        val maxVresolutionPx: Long,
        val mimeType: String,
        val subtitles: List<Any?>,
        val url: String,
        val videoCodec: String,
    )

    data class Audio(
        val kind: String,
        val languageCode: String,
    )

    data class Subtitle(
        val kind: String,
        val languageCode: String,
        val sources: List<Source>,
    )

    data class Source(
        val kind: String,
        val url: String,
    )

    data class Pagination(
        val pageNumber: Long,
        val pageSize: Long,
        val totalElements: Any?,
    )

    data class PublicationService2(
        val name: String,
        val logo: Logo,
        val publisherType: String,
        val partner: String,
        val id: String,
        val coreId: String,
    )

    data class ShowShort(
        val id: String,
        val title: String,
        val image: Image,
        val availableSeasons: Any?,
        val binaryFeatures: List<String>,
        val isChildContent: Boolean,
        val coreAssetType: String,
    )

    data class Editorial(
        @JsonProperty("aZContent")
        val aZcontent: Boolean,
        val compilationType: String,
        val id: String,
        val isChildContent: Boolean,
        val pagination: Pagination,
        val personalized: Boolean,
        val links: Links,
        val size: String,
        val swipeable: Boolean,
        val teasers: List<Teaser>,
        val title: String,
        val titleVisible: Boolean,
        val type: String,
        val userVisibility: String,
    )

    companion object {
        private const val PAGE_SIZE = 30
    }
}