package app.unreel.data

import android.content.Context
import android.content.SharedPreferences

/** User settings. Stored on-device only. */
object Prefs {
    private lateinit var sp: SharedPreferences

    fun init(context: Context) {
        if (!::sp.isInitialized) {
            sp = context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)
        }
    }

    /** For DETECT apps: block the short-form viewer. For WHOLE_APP apps: block the whole app. */
    fun blockShortForm(appId: String): Boolean = sp.getBoolean("sf_$appId", true)
    fun setBlockShortForm(appId: String, value: Boolean) {
        sp.edit().putBoolean("sf_$appId", value).apply()
    }

    /** Daily limit in minutes, 0 = no limit. */
    fun limitMinutes(appId: String): Int = sp.getInt("limit_$appId", 0)
    fun setLimitMinutes(appId: String, minutes: Int) {
        sp.edit().putInt("limit_$appId", minutes).apply()
    }

    var strictMode: Boolean
        get() = sp.getBoolean("strict", false)
        set(v) { sp.edit().putBoolean("strict", v).apply() }

    var browserShortForm: Boolean
        get() = sp.getBoolean("browser_sf", true)
        set(v) { sp.edit().putBoolean("browser_sf", v).apply() }

    var blockedSites: Set<String>
        get() = sp.getStringSet("sites", emptySet())?.toSet() ?: emptySet()
        set(v) { sp.edit().putStringSet("sites", HashSet(v)).apply() }

    var dnsFilter: Boolean
        get() = sp.getBoolean("dns_filter", false)
        set(v) { sp.edit().putBoolean("dns_filter", v).apply() }
}
