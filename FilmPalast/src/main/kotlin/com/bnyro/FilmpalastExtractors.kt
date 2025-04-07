package com.bnyro

import com.lagradost.cloudstream3.extractors.StreamWishExtractor
import com.lagradost.cloudstream3.extractors.Supervideo

class BigwarpIO: Supervideo() {
    override var name = "Bigwarp"
    override var mainUrl = "https://bigwarp.io"
}

class Ryderjet: Supervideo() {
    override var name = "Ryderjet"
    override var mainUrl = "https://ryderjet.com"
}

class Dhtpre : StreamWishExtractor() {
    override var name = "EarnVids"
    override var mainUrl = "https://dhtpre.com"
}

class Peytonepre : StreamWishExtractor() {
    override var name = "EarnVids"
    override var mainUrl = "https://peytonepre.com"
}

class AbstreamTo : Supervideo() {
    override var name = "Abstream"
    override var mainUrl = "https://abstream.to"
}