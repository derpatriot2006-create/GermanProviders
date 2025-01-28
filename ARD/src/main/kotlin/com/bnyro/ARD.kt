package com.bnyro

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.addDate
import com.lagradost.cloudstream3.apmap
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
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
        "1FdQ5oz2JK6o2qmyqMsqiI:-4573418300315789064" to "Jetzt Live",
        "1FdQ5oz2JK6o2qmyqMsqiI:7024894723483797725" to "Film-Empfehlungen",
        "d08a2b5f-8133-4bdd-8ea9-64b6172ce5ee:5379188208992982100" to "Aktuelle Debatten",
        "d08a2b5f-8133-4bdd-8ea9-64b6172ce5ee:-1083078599273736954" to "Exklusive Recherchen",
        "3JvraZLz6r8E9VJOSjxe0m:5345608557251872358" to "Derzeit beliebte Dokus",
        "3JvraZLz6r8E9VJOSjxe0m:3945748791191973508" to "Spannende Dokus und Reportagen",
        "3JvraZLz6r8E9VJOSjxe0m:-4951729414550313310" to "Dokumentarfilme",
        "1FdQ5oz2JK6o2qmyqMsqiI:-8035917636575745435" to "Politik-Talks und Politik-Magazine"
    )

    private fun getImageUrl(url: String): String {
        return url.replace("{width}", "200")
    }

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val response =
            app.get("${mainUrl}/page-gateway/widgets/ard/editorials/${request.data}?pageSize=${PAGE_SIZE}&pageNumber=${page - 1}")
                .parsed<Editorial>()

        return newHomePageResponse(
            request.name,
            response.teasers.mapNotNull { it.toSearchResponse() },
            hasNext = false
        )
    }

    private fun getType(coreAssetType: String?): TvType {
        return when {
            coreAssetType?.endsWith("LIVESTREAM") == true -> TvType.Live
            coreAssetType?.endsWith("SERIES") == true -> TvType.TvSeries
            else -> TvType.Movie
        }
    }

    private fun Teaser.toSearchResponse(): SearchResponse? {
        val type = getType(this.coreAssetType)

        return newMovieSearchResponse(
            name = this.mediumTitle ?: this.shortTitle ?: return null,
            url = ItemInfo(this.links?.target?.id ?: this.id, type).toJson(),
            type = type,
        ) {
            this.posterUrl =
                this@toSearchResponse.images.values.firstOrNull()?.src?.let { getImageUrl(it) }
            this.year = this@toSearchResponse.broadcastedOn?.take(4)?.toIntOrNull()
        }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun search(query: String): List<SearchResponse> {
        return listOf("shows", "vods").apmap { type ->
            app.get("$mainUrl/search-system/search/${type}/ard?query=${query}&pageSize=${PAGE_SIZE}&platform=MEDIA_THEK&sortingCriteria=SCORE_DESC")
                .parsed<Search>()
                .teasers
                .mapNotNull { it.toSearchResponse() }
        }.flatten()
    }

    private suspend fun loadSeries(url: String, seriesId: String): LoadResponse {
        val response = app.get(
            "${mainUrl}/page-gateway/pages/ard/grouping/${seriesId}?seasoned=true&embedded=true"
        )
            .parsed<GroupingResponse>()

        val type = getType(response.coreAssetType)
        val episodes =
            response.widgets.filter { it.compilationType == "itemsOfSeason" }.map { season ->
                season.teasers.mapIndexed { index, episode ->
                    newEpisode(ItemInfo(episode.links?.target?.id ?: episode.id, type)) {
                        this.name = episode.mediumTitle ?: episode.longTitle
                        this.season = season.seasonNumber?.toIntOrNull()
                        this.runTime = episode.duration?.div(60)?.toInt()
                        this.episode = index + 1
                        this.posterUrl = episode.images.values.firstOrNull()?.src
                        addDate(episode.broadcastedOn?.take(10))
                    }
                }
            }.flatten()

        return newTvSeriesLoadResponse(
            name = response.title,
            url = url,
            type = type,
            episodes = episodes
        ) {
            this.posterUrl = (response.heroImage ?: response.image)?.src
            this.plot = response.synopsis
            response.trailer?.links?.target?.id?.let {
                addTrailer(ItemInfo(it, TvType.TvSeries).toJson())
            }
        }
    }

    private suspend fun fetchEpisodeInfo(episodeId: String): DetailsResponse {
        return app.get(
            "${mainUrl}/page-gateway/pages/ard/item/${episodeId}?embedded=false&mcV6=true"
        )
            .parsed<DetailsResponse>()
    }

    private fun findPlayerInfo(widgets: List<Widget>): Widget? {
        return widgets.firstOrNull { it.type.startsWith("player") }
    }

    private suspend fun loadEpisode(url: String, episodeId: String): LoadResponse {
        val response = fetchEpisodeInfo(episodeId)

        val streamInfo = findPlayerInfo(response.widgets)
        val embedded = streamInfo?.mediaCollection?.embedded
        return newMovieLoadResponse(
            name = response.title,
            url = url,
            type = getType(response.coreAssetType),
            data = embedded?.toJson()
        ) {
            this.posterUrl = streamInfo?.image?.src?.let { getImageUrl(it) }
            this.plot = streamInfo?.synopsis
            this.duration = embedded?.meta?.durationSeconds?.div(60)?.toInt()
            this.comingSoon = embedded?.streams?.isEmpty() == true
            this.year = streamInfo?.broadcastedOn?.take(4)?.toIntOrNull()
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        val (id, type) = parseJson<ItemInfo>(url)

        return when (type) {
            TvType.TvSeries -> loadSeries(url, id)
            else -> loadEpisode(url, id)
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        var embedded = tryParseJson<Embedded>(data)
        if (embedded == null) {
            val (id, _) = parseJson<ItemInfo>(data)

            embedded = findPlayerInfo(fetchEpisodeInfo(id).widgets)?.mediaCollection?.embedded
        }

        if (embedded == null) throw IllegalArgumentException("Couldn't find episode info!")
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
                    SubtitleFile(
                        lang = subtitle.languageCode.orEmpty(),
                        url = source.url ?: continue
                    )
                )
            }
        }

        return true
    }

    data class ItemInfo(
        val id: String,
        val type: TvType
    )

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
        val links: Links?,
        val coreAssetType: String?
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
        val coreAssetType: String?
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
        val compilationType: String?,
        val size: String?,
        val seasonNumber: String?,
        val teasers: List<Teaser> = emptyList()
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
        @JsonProperty("_mediaArray") val mediaArray: List<MediaStreamArray> = emptyList(),
        val streams: List<Stream> = emptyList(),
        val subtitles: List<Subtitle> = emptyList(),
    )

    data class MediaStreamArray(
        @JsonProperty("_mediaStreamArray") val mediaStreamArray: List<MediaStream>
    )

    data class MediaStream(
        @JsonProperty("_quality") val quality: String?,
        @JsonProperty("_stream") val stream: String,
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

    data class GroupingResponse(
        val id: String?,
        val title: String,
        val synopsis: String?,
        val heroImage: Image?,
        val image: Image?,
        val widgets: List<Widget> = emptyList(),
        val coreAssetType: String?,
        val trailer: Trailer?,
    )

    data class Trailer(
        val duration: Long?,
        val links: Links?,
        val title: String?,
        val synopsis: String?,
    )

    companion object {
        private const val PAGE_SIZE = 30
    }
}