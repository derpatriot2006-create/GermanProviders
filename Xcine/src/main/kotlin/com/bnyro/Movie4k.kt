package com.bnyro

import com.lagradost.cloudstream3.mainPageOf

class Movie4k : XCineBase() {
    override var name = "Movie4k"
    override var mainUrl = "https://movie4k.sx"
    override var mainAPI = "https://api.movie4k.sx"

    override val mainPage = mainPageOf(
        "data/browse/?lang=2&keyword=&year=&rating=&votes=&genre=&country=&cast=&directors=&type=movies&order_by=trending" to "Derzeit Beliebt Filme",
        "data/browse/?lang=2&keyword=&year=&rating=&votes=&genre=&country=&cast=&directors=&type=movies&order_by=releases" to "Neu Filme",
        "data/browse/?lang=2&keyword=&year=&rating=&votes=&genre=&country=&cast=&directors=&type=tvseries&order_by=trending" to "Derzeit Beliebt Serien",
        "data/browse/?lang=2&keyword=&year=&rating=&votes=&genre=&country=&cast=&directors=&type=tvseries&order_by=releases" to "Neu Serien",
    )
}