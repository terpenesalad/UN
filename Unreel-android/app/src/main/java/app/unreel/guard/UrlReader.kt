package app.unreel.guard

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import app.unreel.data.Browsers

/**
 * Reads the web address a browser is showing.
 *
 * 1. Looks for the address bar by its known view ID (fast and exact).
 * 2. If that fails (browsers rename these, and newer Firefox builds its toolbar in Jetpack
 *    Compose, which has no view IDs), scans the toolbar area at the top or bottom of the
 *    screen for text that looks like a web address. Web page content is skipped, so a
 *    link to reddit.com inside a page doesn't count as being on reddit.com.
 */
object UrlReader {

    sealed class Result {
        data class Url(val url: String, val how: String) : Result()
        /** The user is typing in the address bar; ignore until they finish. */
        object Typing : Result()
        /** Nothing readable, e.g. the toolbar has scrolled away. */
        object Unknown : Result()
    }

    private val URL_REGEX = Regex(
        "^(https?://)?([a-z0-9-]+\\.)+[a-z]{2,}(:\\d+)?([/?#].*)?$",
        RegexOption.IGNORE_CASE,
    )

    private const val MAX_NODES = 600

    fun read(root: AccessibilityNodeInfo, pkg: String, screenHeight: Int): Result {
        // 1. Known view IDs.
        val ids = Browsers.urlBarIds[pkg].orEmpty()
        for (id in ids) {
            val node = root.findAccessibilityNodeInfosByViewId("$pkg:id/$id").firstOrNull() ?: continue
            if (node.isFocused && node.isEditable) return Result.Typing
            val text = node.text?.toString()?.trim()
            if (!text.isNullOrEmpty() && looksLikeUrl(text)) return Result.Url(text, "address bar")
        }

        // 2. Scan the toolbar bands (top and bottom 22% of the screen), skipping page content.
        val band = (screenHeight * 0.22f).toInt()
        val bounds = Rect()
        var best: String? = null
        var bestScore = -1
        var visited = 0
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty() && visited < MAX_NODES) {
            val node = queue.removeFirst()
            visited++
            val cls = node.className?.toString().orEmpty()
            // Page content: Chromium/WebView content and Firefox's GeckoView.
            if (cls.contains("WebView", ignoreCase = true) || cls.contains("GeckoView", ignoreCase = true)) continue

            node.getBoundsInScreen(bounds)
            val inToolbar = bounds.height() > 0 && (bounds.top < band || bounds.bottom > screenHeight - band)
            if (inToolbar && node.isVisibleToUser) {
                if (node.isFocused && node.isEditable) return Result.Typing
                val candidate = urlIn(node.text) ?: urlIn(node.contentDescription)
                if (candidate != null) {
                    val idName = node.viewIdResourceName.orEmpty().lowercase()
                    var score = 1
                    if ("url" in idName || "address" in idName || "toolbar" in idName) score += 2
                    if (node.isEditable) score += 1
                    if (score > bestScore) {
                        best = candidate
                        bestScore = score
                    }
                }
            }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }
        return best?.let { Result.Url(it, "toolbar scan") } ?: Result.Unknown
    }

    private fun urlIn(cs: CharSequence?): String? {
        val s = cs?.toString()?.trim() ?: return null
        if (s.isEmpty() || s.length > 2048) return null
        if (looksLikeUrl(s)) return s
        // e.g. "reddit.com, Tap to edit" or "Secure connection reddit.com"
        return s.split(' ', ',', '\n')
            .map { it.trim().trimEnd('.') }
            .firstOrNull { it.length >= 4 && looksLikeUrl(it) }
    }

    fun looksLikeUrl(s: String): Boolean = !s.contains(' ') && URL_REGEX.matches(s)
}
