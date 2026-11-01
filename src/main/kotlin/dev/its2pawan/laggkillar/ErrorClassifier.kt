package dev.its2pawan.laggkillar

import com.sedmelluq.discord.lavaplayer.tools.FriendlyException

enum class Fault { BLOCKED, RATE_LIMIT, EXPIRED, NETWORK, UNAVAILABLE, OTHER }

/** Reads the whole exception chain so errors from any source plugin are understood. */
object ErrorClassifier {
    private val rate = Regex("\\b429\\b")
    private val expired = Regex("\\b(403|410)\\b")

    private fun has(t: String, vararg keys: String) = keys.any { t.contains(it) }

    fun classify(e: FriendlyException): Fault {
        val sb = StringBuilder()
        var c: Throwable? = e
        var i = 0
        while (c != null && i < 6) { sb.append(c.message ?: "").append(' '); c = c.cause; i++ }
        val t = sb.toString().lowercase()

        return when {
            has(t, "sign in to confirm", "not a bot", "login required", "requires login",
                "log in to", "sign in", "po token", "potoken") -> Fault.BLOCKED
            rate.containsMatchIn(t) || has(t, "too many requests", "rate limit", "rate-limit", "ratelimit") -> Fault.RATE_LIMIT
            expired.containsMatchIn(t) || has(t, "forbidden", "expired", "signature", "cipher", "sabr",
                "unable to extract", "could not extract", "playback url") -> Fault.EXPIRED
            has(t, "timeout", "timed out", "connection reset", "unexpected end", "eof", "ssl",
                "socket", "broken pipe", "connection closed", "unreachable") -> Fault.NETWORK
            e.severity == FriendlyException.Severity.COMMON ||
                has(t, "unavailable", "not available", "private", "country", "region",
                    "copyright", "deleted", "removed", "not found") -> Fault.UNAVAILABLE
            else -> Fault.OTHER
        }
    }
}
