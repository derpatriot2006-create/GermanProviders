package com.bnyro

class TLC : Dmax() {
    override var name: String = "TLC"
    override var mainUrl: String = "https://tlc.de"
    override var serviceIdentifier: String = "tlc"
    override var mediathekSlug: String = "sendungen"
    override var apiTokenRealm: String = "tlcde"
}
