package app.unreel.guard

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import app.unreel.data.Browsers
import app.unreel.data.Catalog
import app.unreel.data.Mode
import app.unreel.data.Prefs
import app.unreel.data.SocialApp
import app.unreel.data.UsageStore
import app.unreel.ui.BlockedActivity

/**
 * Watches the foreground app and:
 *  - closes Reels / Shorts / Spotlight viewers (sends Back),
 *  - blocks whole apps that are only short-form (TikTok),
 *  - blocks chosen websites in browsers,
 *  - counts time per app and enforces daily limits.
 *
 * It only looks at screen structure to make these decisions; nothing is stored or sent.
 */
class GuardService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private var currentPkg: String? = null
    private var imePackages: Set<String> = emptySet()
    private var lastCheck = 0L
    private var lastAction = 0L
    private var lastKick = 0L
    private var ticks = 0

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

    private fun checkBrowser(pkg: String) {
        val entries = buildList {
            if (Prefs.browserShortForm) addAll(Browsers.shortFormPatterns)
            addAll(Prefs.blockedSites)
        }
        if (entries.isEmpty()) return
        val root = rootInActiveWindow ?: return
        val ids = Browsers.urlBarIds[pkg] ?: return
        for (id in ids) {
            val node = root.findAccessibilityNodeInfosByViewId("$pkg:id/$id").firstOrNull() ?: continue
            // While the user is typing, the bar holds partial text. Only act on loaded pages.
            if (node.isFocused) return
            val url = node.text?.toString() ?: return
            val hit = SiteMatcher.match(url, entries) ?: return
            lastAction = SystemClock.uptimeMillis()
            performGlobalAction(GLOBAL_ACTION_BACK)
            UsageStore.addBlock("browser")
            toast("${SiteMatcher.normalize(hit)} is blocked by Unreel")
            return
        }
    }

    /** Whole-app blocks and daily limits. */
    private fun enforceAppRules(pkg: String) {
        val app = Catalog.byPackage(pkg) ?: return
        if (app.mode == Mode.WHOLE_APP && Prefs.blockShortForm(app.id)) {
            kickOut(app, BlockedActivity.REASON_APP)
        } else if (isOverLimit(app)) {
            kickOut(app, BlockedActivity.REASON_LIMIT)
        }
    }

    private fun isOverLimit(app: SocialApp): Boolean {
        val limit = Prefs.limitMinutes(app.id)
        if (limit <= 0) return false
        val allowedSeconds = (limit + UsageStore.bonusMinutes(app.id)) * 60L
        return UsageStore.seconds(app.id) >= allowedSeconds
    }

    private fun kickOut(app: SocialApp, reason: String) {
        val now = SystemClock.uptimeMillis()
        if (now - lastKick < 2000L) return
        lastKick = now
        currentPkg = packageName
        performGlobalAction(GLOBAL_ACTION_HOME)
        startActivity(BlockedActivity.intent(this, app.id, reason))
    }

    private fun onTick() {
        val power = getSystemService(Context.POWER_SERVICE) as PowerManager
        val keyguard = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        if (power.isInteractive && !keyguard.isKeyguardLocked) {
            val app = Catalog.byPackage(currentPkg)
            if (app != null) {
                UsageStore.addSeconds(app.id, 1L)
                enforceAppRules(app.packages.first())
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
