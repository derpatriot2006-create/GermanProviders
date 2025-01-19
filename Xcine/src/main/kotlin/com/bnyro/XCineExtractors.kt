package com.bnyro

import com.lagradost.cloudstream3.extractors.DoodLaExtractor
import com.lagradost.cloudstream3.extractors.MixDrop
import com.lagradost.cloudstream3.extractors.Supervideo

class MixDropPs: MixDrop() {
    override var mainUrl = "https://mixdrop.ps"
}

class Dooodster: DoodLaExtractor() {
    override var mainUrl = "https://dooodster.com"
}

class DoodsPro: DoodLaExtractor() {
    override var mainUrl = "https://doods.pro"
}

class DoodRe: DoodLaExtractor() {
    override var mainUrl = "https://dood.re"
}

class MixVideo: Supervideo() {
    override var mainUrl = "https://mixvideo.co"
}