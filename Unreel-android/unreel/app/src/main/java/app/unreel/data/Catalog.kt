package app.unreel.data

enum class Mode {
    /** Detect the short-form viewer inside the app and close it. */
    DETECT,
    /** The app is entirely short-form; block the whole app. */
    WHOLE_APP,
}

data class SocialApp(
    val id: String,
    val name: String,
    val packages: List<String>,
    val shortFormName: String,
    val mode: Mode,
    val experimental: Boolean = false,
    /** Fully qualified view IDs that only exist while the short-form viewer is on screen. */
    val viewIds: List<String> = emptyList(),
    /** Tab labels that, when selected, mean the short-form tab is open. */
    val tabLabels: List<String> = emptyList(),
)

/**
 * The apps Unreel knows about and how to spot short-form video in each one.
 *
 * View IDs come from the apps' own layouts and can change when those apps update.
 * If blocking stops working after an app update, this is the place to fix it:
 * use Android Studio's Layout Inspector (or `adb shell uiautomator dump`) on the
 * Reels/Shorts screen to find the new ID.
 */
object Catalog {
    val apps: List<SocialApp> = listOf(
        SocialApp(
            id = "instagram",
            name = "Instagram",
            packages = listOf("com.instagram.android"),
            shortFormName = "Reels",
            mode = Mode.DETECT,
            viewIds = listOf(
                "com.instagram.android:id/clips_viewer_view_pager",
                "com.instagram.android:id/clips_viewer_container",
                "com.instagram.android:id/root_clips_layout",
            ),
        ),
        SocialApp(
            id = "youtube",
            name = "YouTube",
            packages = listOf("com.google.android.youtube"),
            shortFormName = "Shorts",
            mode = Mode.DETECT,
            viewIds = listOf(
                "com.google.android.youtube:id/reel_recycler",
                "com.google.android.youtube:id/reel_watch_player",
                "com.google.android.youtube:id/reel_player_page_container",
                "com.google.android.youtube:id/reel_watch_fragment_root",
            ),
        ),
        SocialApp(
            id = "facebook",
            name = "Facebook",
            packages = listOf("com.facebook.katana"),
            shortFormName = "Reels",
            mode = Mode.DETECT,
            experimental = true,
            tabLabels = listOf("Reels"),
        ),
        SocialApp(
            id = "snapchat",
            name = "Snapchat",
            packages = listOf("com.snapchat.android"),
            shortFormName = "Spotlight",
            mode = Mode.DETECT,
            experimental = true,
            tabLabels = listOf("Spotlight"),
        ),
        SocialApp(
            id = "tiktok",
            name = "TikTok",
            packages = listOf("com.zhiliaoapp.musically", "com.ss.android.ugc.trill"),
            shortFormName = "TikTok",
            mode = Mode.WHOLE_APP,
        ),
    )

    private val byPkg: Map<String, SocialApp> =
        apps.flatMap { app -> app.packages.map { it to app } }.toMap()

    fun byPackage(pkg: String?): SocialApp? = pkg?.let { byPkg[it] }
    fun byId(id: String?): SocialApp? = apps.firstOrNull { it.id == id }
}

private val FIREFOX_IDS = listOf("mozac_browser_toolbar_url_view", "mozac_browser_toolbar_edit_url_view")

/**
 * Browsers and the view ID of their address bar. If an ID stops matching after a
 * browser update, UrlReader falls back to scanning the toolbar for the address.
 */
object Browsers {
    val urlBarIds: Map<String, List<String>> = mapOf(
        "com.android.chrome" to listOf("url_bar"),
        "com.chrome.beta" to listOf("url_bar"),
        "com.brave.browser" to listOf("url_bar"),
        "com.microsoft.emmx" to listOf("url_bar"),
        "com.vivaldi.browser" to listOf("url_bar"),
        "com.kiwibrowser.browser" to listOf("url_bar"),
        "org.mozilla.firefox" to FIREFOX_IDS,
        "org.mozilla.firefox_beta" to FIREFOX_IDS,
        "org.mozilla.fenix" to FIREFOX_IDS,
        "org.mozilla.fennec_fdroid" to FIREFOX_IDS,
        "io.github.forkmaintainers.iceraven" to FIREFOX_IDS,
        "org.mozilla.focus" to listOf("display_url", "mozac_browser_toolbar_url_view"),
        "org.mozilla.klar" to listOf("display_url", "mozac_browser_toolbar_url_view"),
        "com.sec.android.app.sbrowser" to listOf("location_bar_edit_text"),
        "com.opera.browser" to listOf("url_field"),
        "com.duckduckgo.mobile.android" to listOf("omnibarTextInput"),
    )

    /** Short-form pages blocked in browsers when "Block short-form sites" is on. */
    val shortFormPatterns: List<String> = listOf(
        "youtube.com/shorts",
        "instagram.com/reels",
        "instagram.com/reel/",
        "facebook.com/reel",
        "tiktok.com",
    )
}
