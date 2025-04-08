package com.bnyro

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.extractors.Chillx
import com.lagradost.cloudstream3.extractors.StreamWishExtractor
import com.lagradost.cloudstream3.extractors.VidStack
import com.lagradost.cloudstream3.extractors.Vidguardto
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.M3u8Helper
import com.lagradost.cloudstream3.utils.getAndUnpack

class MoflixFans : Chillx() {
    override val name = "MoflixFans"
    override val mainUrl = "https://moflix-stream.fans"
}

class MoflixDay : Vidguardto() {
    override val name = "MoflixDay"
    override val mainUrl = "https://moflix-stream.day"
}

class MoflixUpns : VidStack() {
    override var name: String = "Moflix UPNS"
    override var mainUrl: String = "https://moflix.upns.xyz"
}

class MoflixRpmplay : VidStack() {
    override var name: String = "Moflix UPNS"
    override var mainUrl: String = "https://moflix.rpmplay.xyz"
}

open class MoflixClick : ExtractorApi() {
    override val name = "MoflixClick"
    override val mainUrl = "https://moflix-stream.click"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val response = app.get(url, referer = referer)
        val script = getAndUnpack(response.text)

        val sources = Regex(":\"(.*?m3u8.*?)\"").find(script)?.groupValues?.drop(1).orEmpty()
        for (m3u8 in sources) {
            M3u8Helper.generateM3u8(name, m3u8, mainUrl).forEach(callback)
        }
    }
}