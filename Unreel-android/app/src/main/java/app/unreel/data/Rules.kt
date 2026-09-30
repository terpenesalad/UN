package app.unreel.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDateTime

/**
 * A blocked-hours window. Minutes are from midnight (0..1439). Days use ISO numbering,
 * Monday = 1 ... Sunday = 7. If end <= start the window runs past midnight into the next day;
 * start == end means the whole day.
 */
data class Window(val startMin: Int, val endMin: Int, val days: Set<Int>) {

    fun isActive(now: LocalDateTime): Boolean {
        val minute = now.hour * 60 + now.minute
        val today = now.dayOfWeek.value
        val yesterday = if (today == 1) 7 else today - 1
        return when {
            startMin == endMin -> today in days
            startMin < endMin -> today in days && minute >= startMin && minute < endMin
            else -> (today in days && minute >= startMin) || (yesterday in days && minute < endMin)
        }
    }

    /** When the window currently in effect ends. Only meaningful while [isActive]. */
    fun endsAt(now: LocalDateTime): LocalDateTime {
        val minute = now.hour * 60 + now.minute
        val base = now.toLocalDate().atStartOfDay()
        return when {
            startMin == endMin -> base.plusDays(1)
            startMin < endMin -> base.plusMinutes(endMin.toLong())
            minute >= startMin -> base.plusDays(1).plusMinutes(endMin.toLong())
            else -> base.plusMinutes(endMin.toLong())
        }
    }
}

data class Rule(
    /** Daily limit in minutes, 0 = none. */
    val dailyMinutes: Int = 0,
    /** Longest continuous session in minutes, 0 = none. */
    val sessionMinutes: Int = 0,
    /** Forced break after a session hits its limit, in minutes. */
    val breakMinutes: Int = 15,
    /** Times of day when the app or site is blocked outright. */
    val windows: List<Window> = emptyList(),
) {
    val isEmpty: Boolean get() = dailyMinutes == 0 && sessionMinutes == 0 && windows.isEmpty()
}

object Rules {
    /** A session ends after this long away from the app or site. */
    const val SESSION_GAP_SECONDS = 5 * 60L

    fun get(id: String): Rule {
        val daily = Prefs.limitMinutes(id)
        val raw = Prefs.ruleJson(id) ?: return Rule(dailyMinutes = daily)
        return try {
            val o = JSONObject(raw)
            val arr = o.optJSONArray("windows") ?: JSONArray()
            val windows = (0 until arr.length()).map { i ->
                val w = arr.getJSONObject(i)
                val d = w.getJSONArray("days")
                Window(w.getInt("start"), w.getInt("end"), (0 until d.length()).map { d.getInt(it) }.toSet())
            }
            Rule(
                dailyMinutes = daily,
                sessionMinutes = o.optInt("session", 0),
                breakMinutes = o.optInt("break", 15),
                windows = windows,
            )
        } catch (e: Exception) {
            Rule(dailyMinutes = daily)
        }
    }

    fun set(id: String, rule: Rule) {
        Prefs.setLimitMinutes(id, rule.dailyMinutes)
        val arr = JSONArray()
        rule.windows.forEach { w ->
            arr.put(
                JSONObject()
                    .put("start", w.startMin)
                    .put("end", w.endMin)
                    .put("days", JSONArray(w.days.sorted()))
            )
        }
        val o = JSONObject()
            .put("session", rule.sessionMinutes)
            .put("break", rule.breakMinutes)
            .put("windows", arr)
        Prefs.setRuleJson(id, o.toString())
    }

    fun clear(id: String) {
        Prefs.setLimitMinutes(id, 0)
        Prefs.setRuleJson(id, null)
    }

    /** The blocked-hours window in effect right now, if any. */
    fun activeWindow(id: String, now: LocalDateTime = LocalDateTime.now()): Window? =
        get(id).windows.firstOrNull { it.isActive(now) }

    /** Short human summary for list rows, e.g. "1h 30m a day · 20m sessions · blocked 22:00–07:00". */
    fun summary(rule: Rule): String {
        val parts = mutableListOf<String>()
        if (rule.dailyMinutes > 0) parts += "${fmt(rule.dailyMinutes)} a day"
        if (rule.sessionMinutes > 0) parts += "${fmt(rule.sessionMinutes)} sessions"
        when (rule.windows.size) {
            0 -> {}
            1 -> parts += "blocked ${clock(rule.windows[0].startMin)}–${clock(rule.windows[0].endMin)}"
            else -> parts += "${rule.windows.size} blocked times"
        }
        return if (parts.isEmpty()) "No limits" else parts.joinToString(" · ")
    }

    fun fmt(m: Int): String = when {
        m < 60 -> "${m}m"
        m % 60 == 0 -> "${m / 60}h"
        else -> "${m / 60}h ${m % 60}m"
    }

    fun clock(min: Int): String = "%02d:%02d".format(min / 60, min % 60)
}

/** Apps the user added from their phone (beyond the built-in social apps). */
object CustomApps {
    const val PREFIX = "pkg:"

    fun id(pkg: String): String = PREFIX + pkg
    fun isCustomId(id: String?): Boolean = id?.startsWith(PREFIX) == true
    fun pkgOf(id: String): String = id.removePrefix(PREFIX)

    fun list(): List<String> = Prefs.customApps.sortedBy { Prefs.appLabel(it).lowercase() }

    fun add(pkg: String, label: String) {
        Prefs.setAppLabel(pkg, label)
        Prefs.customApps = Prefs.customApps + pkg
    }

    fun remove(pkg: String) {
        Prefs.customApps = Prefs.customApps - pkg
        Rules.clear(id(pkg))
    }

    /** Tracking id for a foreground package: built-in app id, custom app id, or null. */
    fun targetFor(pkg: String?): String? {
        pkg ?: return null
        Catalog.byPackage(pkg)?.let { return it.id }
        return if (pkg in Prefs.customApps) id(pkg) else null
    }
}

/** Anything Unreel tracks time for: a built-in app, an added app, or a website. */
data class Tracked(val id: String, val name: String)

fun nameOf(id: String): String = when {
    Sites.isSiteId(id) -> Sites.nameOf(id)
    CustomApps.isCustomId(id) -> Prefs.appLabel(CustomApps.pkgOf(id))
    else -> Catalog.byId(id)?.name ?: id
}

fun allTracked(): List<Tracked> =
    Catalog.apps.map { Tracked(it.id, it.name) } +
        CustomApps.list().map { Tracked(CustomApps.id(it), Prefs.appLabel(it)) } +
        Sites.list().map { Tracked(Sites.id(it), it) }
