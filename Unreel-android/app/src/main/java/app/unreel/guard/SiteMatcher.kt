package app.unreel.guard

object SiteMatcher {
    fun normalize(raw: String): String {
        var u = raw.trim().lowercase()
        u = u.substringAfter("://", u)
        for (prefix in listOf("www.", "m.", "mobile.")) {
            if (u.startsWith(prefix)) u = u.removePrefix(prefix)
        }
        return u.trimEnd('/')
    }

    /** Returns the entry that matched [url], or null. Domain entries also match subdomains. */
    fun match(url: String, entries: Collection<String>): String? {
        val n = normalize(url)
        if (n.isEmpty() || n.contains(' ')) return null
        val host = n.substringBefore('/').substringBefore('?').substringBefore(':')
        for (entry in entries) {
            val e = normalize(entry)
            if (e.isEmpty()) continue
            if (e.contains('/')) {
                if (n.startsWith(e)) return entry
            } else if (host == e || host.endsWith(".$e")) {
                return entry
            }
        }
        return null
    }
}
