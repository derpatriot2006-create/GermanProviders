package com.bnyro

import com.lagradost.cloudstream3.extractors.Chillx
import com.lagradost.cloudstream3.extractors.Supervideo
import com.lagradost.cloudstream3.extractors.Vidguardto

class KinogerRu : Chillx() {
    override val name = "Kinoger"
    override val mainUrl = "https://kinoger.ru"
}

class KinogerBe : Supervideo() {
    override var name = "KinogerBe"
    override var mainUrl = "https://kinoger.be"
}

class KinogerPw : Vidguardto() {
    override var name = "KinogerPw"
    override var mainUrl = "https://kinoger.pw"
}

class KinogerRe : Vidguardto() {
    override var name = "KinogerRe"
    override var mainUrl = "https://kinoger.re"
}