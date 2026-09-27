package app.unreel.guard

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import app.unreel.data.Browsers
import app.unreel.data.Catalog
import app.unreel.data.CustomApps
import app.unreel.data.Mode
import app.unreel.data.Prefs
import app.unreel.data.Rules
import app.unreel.data.Sites
import app.unreel.data.SocialApp
import app.unreel.data.UsageStore
import app.unreel.ui.BlockedActivity
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Watches the foreground app and:
 *  - closes Reels / Shorts / Spotlight viewers (sends Back),
 *  - blocks whole apps that are only short-form (TikTok),
 *  - blocks chosen websites in browsers,
 *  - counts time for any tracked app or website and enforces its rules:
 *    blocked hours, session limits with forced breaks, and daily limits.
 *
 * It only looks at screen structure to make these decisions; nothing is stored or sent.
 */
class GuardService : AccessibilityService() {

    companion object {
        /** Where a browser is sent when a website's time is up. */
        private const val NEUTRAL_PAGE = "https://www.google.com/"
    }

    private val handler = Handler(Looper.getMainLooper())
    private var currentPkg: String? = null
    private var imePackages: Set<String> = emptySet()
    private var lastCheck = 0L
    private var lastAction = 0L
    private var lastKick = 0L
    private var ticks = 0

    /** The limited website currently on screen, and the browser showing it. */
    private var currentSite: String? = null
    private var currentSitePkg: String? = null

    private class Session(var used: Long, var lastSeen: Long)
    private val sessions = HashMap<String, Session>()

    private val tick = object : Runnable {
        override fun run() {
            onTick()
            handler.postDelayed(this, 1000L)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Prefs.init(this)
        UsageStore.init(this)
        imePackages = try {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.enabledInputMethodList.map { it.packageName }.toSet()
        } catch (e: Exception) {
            emptySet()
        }
        handler.removeCallbacks(tick)
        handler.post(tick)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        val pkg = event.packageName?.toString() ?: return
        val isWindowChange = event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED

        if (isWindowChange && pkg != "com.android.systemui" && pkg !in imePackages) {
            currentPkg = pkg
            if (pkg != currentSitePkg) {
                currentSite = null
                currentSitePkg = null
            }
            enforceAppRules(pkg)
        }

        val now = SystemClock.uptimeMillis()
        if (now - lastAction < 1200L) return
        if (!isWindowChange && now - lastCheck < 350L) return

        val app = Catalog.byPackage(pkg)
        if (app != null) {
            lastCheck = now
            checkShortForm(app)
        } else if (Browsers.urlBarIds.containsKey(pkg)) {
            lastCheck = now
            checkBrowser(pkg)
        }
    }

    private fun checkShortForm(app: SocialApp) {
        if (app.mode != Mode.DETECT || !Prefs.blockShortForm(app.id)) return
        val root = rootInActiveWindow ?: return
        val rootPkg = root.packageName?.toString() ?: return
        if (rootPkg !in app.packages) return
        if (Detector.isShortForm(app, root)) {
            lastAction = SystemClock.uptimeMillis()
            performGlobalAction(GLOBAL_ACTION_BACK)
            UsageStore.addBlock(app.id)
            toast("${app.shortFormName} blocked by Unreel")
        }
    }

    /** Reads the address bar, or null if it can't be read right now (hidden, or being typed in). */
    private fun readUrl(pkg: String): String? {
        val root = rootInActiveWindow ?: return null
        if (root.packageName?.toString() != pkg) return null
        val ids = Browsers.urlBarIds[pkg] ?: return null
        for (id in ids) {
            val node = root.findAccessibilityNodeInfosByViewId("$pkg:id/$id").firstOrNull() ?: continue
            // While the user is typing, the bar holds partial text. Only act on loaded pages.
            if (node.isFocused) return null
            val text = node.text?.toString()?.trim()
            return if (text.isNullOrEmpty()) null else text
        }
        return null
    }

    private fun checkBrowser(pkg: String) {
        val limited = Prefs.limitedSites
        val blocked = buildList {
            if (Prefs.browserShortForm) addAll(Browsers.shortFormPatterns)
            addAll(Prefs.blockedSites)
        }
        if (limited.isEmpty() && blocked.isEmpty()) return

        // When the toolbar scrolls out of view we can't read it; keep the last known site.
        val url = readUrl(pkg) ?: return

        val hit = SiteMatcher.match(url, blocked)
        if (hit != null) {
            lastAction = SystemClock.uptimeMillis()
            performGlobalAction(GLOBAL_ACTION_BACK)
            UsageStore.addBlock("browser")
            toast("${SiteMatcher.normalize(hit)} is blocked by Unreel")
            return
        }

        val site = SiteMatcher.match(url, limited)
        currentSite = site
        currentSitePkg = if (site != null) pkg else null
        if (site != null) enforce(Sites.id(site), pkg)
    }

    /** Whole-app blocks for short-form-only apps, then the app's own rules. */
    private fun enforceAppRules(pkg: String) {
        val app = Catalog.byPackage(pkg)
        if (app != null && app.mode == Mode.WHOLE_APP && Prefs.blockShortForm(app.id)) {
            kick(app.id, BlockedActivity.REASON_APP, 0L, null)
            return
        }
        val id = CustomApps.targetFor(pkg) ?: return
        enforce(id, null)
    }

    /**
     * Applies blocked hours, forced breaks and the daily limit for [id].
     * [browserPkg] is set when [id] is a website open in that browser.
     * Returns true if the user was stopped.
     */
    private fun enforce(id: String, browserPkg: String?): Boolean {
        val now = LocalDateTime.now()
        val window = Rules.activeWindow(id, now)
        if (window != null) {
            val until = window.endsAt(now).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            kick(id, BlockedActivity.REASON_SCHEDULE, until, browserPkg)
            return true
        }
        val breakUntil = UsageStore.breakUntil(id)
        // A break only applies while a session limit is set; clearing the limit lifts it.
        if (breakUntil > System.currentTimeMillis() && Rules.get(id).sessionMinutes > 0) {
            kick(id, BlockedActivity.REASON_BREAK, breakUntil, browserPkg)
            return true
        }
        if (isOverLimit(id)) {
            kick(id, BlockedActivity.REASON_LIMIT, 0L, browserPkg)
            return true
        }
        return false
    }

    private fun isOverLimit(id: String): Boolean {
        val limit = Prefs.limitMinutes(id)
        if (limit <= 0) return false
        val allowedSeconds = (limit + UsageStore.bonusMinutes(id)) * 60L
        return UsageStore.seconds(id) >= allowedSeconds
    }

    /** Counts one second of continuous use; starts a forced break when the session limit is hit. */
    private fun countSession(id: String, browserPkg: String?) {
        val rule = Rules.get(id)
        if (rule.sessionMinutes <= 0) return
        val nowS = SystemClock.elapsedRealtime() / 1000L
        val session = sessions.getOrPut(id) { Session(0L, nowS) }
        if (nowS - session.lastSeen > Rules.SESSION_GAP_SECONDS) session.used = 0L
        session.used += 1L
        session.lastSeen = nowS
        if (session.used >= rule.sessionMinutes * 60L) {
            session.used = 0L
            val until = System.currentTimeMillis() + rule.breakMinutes * 60_000L
            UsageStore.setBreakUntil(id, until)
            kick(id, BlockedActivity.REASON_BREAK, until, browserPkg)
        }
    }

    /**
     * Stops the user. Apps: go to the home screen. Websites: move the browser to a
     * neutral page in a new tab (so the browser stays usable for other sites).
     * Then show the stop screen on top.
     */
    private fun kick(id: String, reason: String, until: Long, browserPkg: String?) {
        val now = SystemClock.uptimeMillis()
        if (now - lastKick < 2000L) return
        lastKick = now
        lastAction = now
        // If the limited site is the neutral page itself, opening it would just trigger another kick.
        val neutralIsLimited = Sites.isSiteId(id) &&
            SiteMatcher.match(NEUTRAL_PAGE, listOf(Sites.nameOf(id))) != null
        if (browserPkg != null && !neutralIsLimited) {
            currentSite = null
            currentSitePkg = null
            try {
                startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse(NEUTRAL_PAGE))
                        .setPackage(browserPkg)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (e: Exception) {
                performGlobalAction(GLOBAL_ACTION_HOME)
            }
            handler.postDelayed({
                startActivity(BlockedActivity.intent(this, id, reason, until))
            }, 400L)
        } else {
            currentPkg = packageName
            performGlobalAction(GLOBAL_ACTION_HOME)
            startActivity(BlockedActivity.intent(this, id, reason, until))
        }
    }

    private fun onTick() {
        val power = getSystemService(Context.POWER_SERVICE) as PowerManager
        val keyguard = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        if (power.isInteractive && !keyguard.isKeyguardLocked) {
            val pkg = currentPkg
            val appId = CustomApps.targetFor(pkg)
            if (pkg != null && appId != null) {
                UsageStore.addSeconds(appId, 1L)
                countSession(appId, null)
                enforceAppRules(pkg)
            }
            val site = currentSite
            val sitePkg = currentSitePkg
            if (site != null && sitePkg != null && sitePkg == currentPkg) {
                val siteId = Sites.id(site)
                UsageStore.addSeconds(siteId, 1L)
                countSession(siteId, sitePkg)
                enforce(siteId, sitePkg)
            }
        }
        ticks++
        if (ticks % 15 == 0) UsageStore.flush()
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        UsageStore.flush()
        super.onDestroy()
    }
}
