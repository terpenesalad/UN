package app.unreel.data

/**
 * Websites with a daily time limit. Time is counted in any supported browser
 * while the site is the page on screen.
 */
object Sites {
    const val PREFIX = "site:"

    /** Offered as one-tap adds on the Limits screen. */
    val suggestions = listOf("reddit.com", "youtube.com", "x.com", "instagram.com", "facebook.com", "tiktok.com")

    fun id(site: String): String = PREFIX + site
    fun isSiteId(id: String?): Boolean = id?.startsWith(PREFIX) == true
    fun nameOf(id: String): String = id.removePrefix(PREFIX)

    fun list(): List<String> = Prefs.limitedSites.sorted()

    fun add(site: String) {
        Prefs.limitedSites = Prefs.limitedSites + site
        if (Prefs.limitMinutes(id(site)) == 0) Prefs.setLimitMinutes(id(site), 30)
    }

    fun remove(site: String) {
        Prefs.limitedSites = Prefs.limitedSites - site
        Rules.clear(id(site))
    }
}
