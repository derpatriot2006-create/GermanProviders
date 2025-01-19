
package com.bnyro

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class XCinePlugin: Plugin() {
    override fun load(context: Context) {
        // All providers should be added in this manner. Please don't edit the providers list directly.
        registerMainAPI(XcineIO())
        registerMainAPI(Movie4k())
        registerExtractorAPI(StreamTapeAdblockuser())
        registerExtractorAPI(StreamTapeTo())
        registerExtractorAPI(Mixdrp())
        registerExtractorAPI(DoodReExtractor())
        registerExtractorAPI(Streamzz())
        registerExtractorAPI(Streamcrypt())
    }
}