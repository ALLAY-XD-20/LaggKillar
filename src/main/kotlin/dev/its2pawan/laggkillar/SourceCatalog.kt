package dev.its2pawan.laggkillar

/**
 * 22 fallback sources, in priority order (best quality / most likely to exist first).
 * Unknown prefixes cost almost nothing: Lavalink answers "no matches" instantly and
 * LaggKillar auto-disables prefixes that never work on your server.
 *
 * Group A = standard prefixes used by YouTube plugin, Lavaplayer and LavaSrc.
 * Group B = best-effort names used by community / NodeLink-style plugins. If your plugin
 * uses a different prefix, set it in plugins.laggkillar.fallbackSearchPrefixes.
 */
object SourceCatalog {
    val ENTRIES: List<Pair<String, String>> = listOf(
        // Group A
        "ytmsearch:" to "youtube",      // 1  YouTube Music
        "scsearch:" to "soundcloud",    // 2  SoundCloud
        "dzsearch:" to "deezer",        // 3  Deezer (LavaSrc)
        "spsearch:" to "spotify",       // 4  Spotify (LavaSrc)
        "amsearch:" to "applemusic",    // 5  Apple Music (LavaSrc)
        "tdsearch:" to "tidal",         // 6  Tidal (LavaSrc)
        "qbsearch:" to "qobuz",         // 7  Qobuz (LavaSrc)
        "ymsearch:" to "yandexmusic",   // 8  Yandex Music (LavaSrc)
        "vksearch:" to "vkmusic",       // 9  VK Music (LavaSrc)
        "jssearch:" to "jiosaavn",      // 10 JioSaavn
        "ytsearch:" to "youtube",       // 11 YouTube
        "bcsearch:" to "bandcamp",      // 12 Bandcamp
        // Group B
        "pdsearch:" to "pandora",       // 13 Pandora
        "gnsearch:" to "gaana",         // 14 Gaana
        "mcsearch:" to "mixcloud",      // 15 Mixcloud
        "amksearch:" to "audiomack",    // 16 Audiomack
        "nndsearch:" to "nicovideo",    // 17 NicoNico
        "bilisearch:" to "bilibili",    // 18 Bilibili
        "vmsearch:" to "vimeo",         // 19 Vimeo
        "twsearch:" to "twitch",        // 20 Twitch
        "nssearch:" to "napster",       // 21 Napster
        "ttsearch:" to "tiktok"         // 22 TikTok
    )

    val ORDER: List<String> = ENTRIES.map { it.first }
    private val map = ENTRIES.toMap()
    fun sourceOf(prefix: String): String? = map[prefix]
}
