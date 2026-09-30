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

    /** Session limit, break length and blocked hours for an app or site, as JSON. */
    fun ruleJson(id: String): String? = sp.getString("rule_$id", null)
    fun setRuleJson(id: String, json: String?) {
        if (json == null) sp.edit().remove("rule_$id").apply()
        else sp.edit().putString("rule_$id", json).apply()
    }

    /** Apps added from the phone's app list, by package name. */
    var customApps: Set<String>
        get() = sp.getStringSet("custom_apps", emptySet())?.toSet() ?: emptySet()
        set(v) { sp.edit().putStringSet("custom_apps", HashSet(v)).apply() }

    fun appLabel(pkg: String): String = sp.getString("label_$pkg", null) ?: pkg
    fun setAppLabel(pkg: String, label: String) {
        sp.edit().putString("label_$pkg", label).apply()
    }

    /** Minutes granted by the "more time" button on the stop screen; 0 hides the button. */
    var extraMinutes: Int
        get() = sp.getInt("extra_minutes", 5)
        set(v) { sp.edit().putInt("extra_minutes", v).apply() }

    /** How many times a day "more time" can be used per app or site; 0 = unlimited. */
    var maxExtensions: Int
        get() = sp.getInt("max_extensions", 3)
        set(v) { sp.edit().putInt("max_extensions", v).apply() }

    var strictMode: Boolean
        get() = sp.getBoolean("strict", false)
        set(v) { sp.edit().putBoolean("strict", v).apply() }

    var browserShortForm: Boolean
        get() = sp.getBoolean("browser_sf", true)
        set(v) { sp.edit().putBoolean("browser_sf", v).apply() }

    var blockedSites: Set<String>
        get() = sp.getStringSet("sites", emptySet())?.toSet() ?: emptySet()
        set(v) { sp.edit().putStringSet("sites", HashSet(v)).apply() }

    /** Websites with a daily time limit (limit stored under "site:<domain>"). */
    var limitedSites: Set<String>
        get() = sp.getStringSet("limited_sites", emptySet())?.toSet() ?: emptySet()
        set(v) { sp.edit().putStringSet("limited_sites", HashSet(v)).apply() }

    var dnsFilter: Boolean
        get() = sp.getBoolean("dns_filter", false)
        set(v) { sp.edit().putBoolean("dns_filter", v).apply() }
}
