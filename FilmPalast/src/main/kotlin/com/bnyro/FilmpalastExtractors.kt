package com.bnyro

import com.lagradost.cloudstream3.extractors.Supervideo

class BigwarpIO: Supervideo() {
    override var name = "BitwarpIO"
    override var mainUrl = "https://bigwarp.io"
}

class Ryderjet: Supervideo() {
    override var name = "Ryderjet"
    override var mainUrl = "https://ryderjet.com"
}