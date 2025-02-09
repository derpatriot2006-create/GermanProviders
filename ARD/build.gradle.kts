// use an integer for version numbers
version = 3


cloudstream {
    language = "de"
    // All of these properties are optional, you can safely remove them

    description = "German public media"
    authors = listOf("Bnyro")

    /**
     * Status int as the following:
     * 0: Down
     * 1: Ok
     * 2: Slow
     * 3: Beta only
     * */
    status = 1 // will be 3 if unspecified
    tvTypes = listOf(
        "Movie",
        "TvSeries",
        "Live"
    )

    iconUrl = "https://upload.wikimedia.org/wikipedia/commons/d/d1/ARD_Dachmarke_2014.svg"
}
