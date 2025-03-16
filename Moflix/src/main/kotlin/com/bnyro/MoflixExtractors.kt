package com.bnyro

import com.lagradost.cloudstream3.extractors.Chillx
import com.lagradost.cloudstream3.extractors.StreamWishExtractor

class MoflixFans : Chillx() {
    override val name = "MoflixFans"
    override val mainUrl = "https://moflix-stream.fans"
}

open class MoflixClick : StreamWishExtractor() {
    override val name = "MoflixClick"
    override val mainUrl = "https://moflix-stream.click"
    override val requiresReferer = true
}