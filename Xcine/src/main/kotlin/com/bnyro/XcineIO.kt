package com.bnyro

import com.lagradost.cloudstream3.mainPageOf

class XcineIO : XCineBase() {
    override var name = "XcineIO"
    override var mainUrl = "https://www3.xcine.io"
    override var mainAPI = "https://api.xcine.io"

    override val mainPage = mainPageOf(
        "data/browse/?lang=2&keyword=&year=&rating=&votes=&genre=&country=&cast=&directors=&type=movies&order_by=trending" to "Derzeit Beliebt Filme",
        "data/browse/?lang=2&keyword=&year=&rating=&votes=&genre=&country=&cast=&directors=&type=movies&order_by=releases" to "Neu Filme",
        "data/browse/?lang=2&keyword=&year=&rating=&votes=&genre=&country=&cast=&directors=&type=tvseries&order_by=trending" to "Derzeit Beliebt Serien",
        "data/browse/?lang=2&keyword=&year=&rating=&votes=&genre=&country=&cast=&directors=&type=tvseries&order_by=releases" to "Neu Serien",
    )
}