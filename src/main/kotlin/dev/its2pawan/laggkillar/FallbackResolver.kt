package dev.its2pawan.laggkillar

import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist
import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs

/** When one source plugin is broken, find the same song on any of 20+ other sources. */
@Component
class FallbackResolver(
    private val cfg: LaggKillarConfig,
    private val stabilizer: AdaptiveStabilizer,
    private val health: SourceHealth
) {
    private val log = LoggerFactory.getLogger(FallbackResolver::class.java)
    val found = AtomicLong()
    val notFound = AtomicLong()

    private class PStat {
        @Volatile var misses = 0
        @Volatile var hits = 0
        @Volatile var everHit = false
        @Volatile var disabledUntil = 0L
    }
    private val pstats = ConcurrentHashMap<String, PStat>()
    private val noise = Regex("[\\(\\[].*?[\\)\\]]")

    private fun sourceOf(prefix: String) = cfg.fallbackPrefixSources[prefix] ?: SourceCatalog.sourceOf(prefix)

    private fun miss(prefix: String) {
        val st = pstats.computeIfAbsent(prefix) { PStat() }
        st.misses++
        if (!st.everHit && st.misses >= cfg.prefixDisableAfter) {
            st.disabledUntil = System.currentTimeMillis() + cfg.prefixDisableMs
            st.misses = 0
            log.info("Fallback prefix '{}' never matched, pausing it for {}s", prefix, cfg.prefixDisableMs / 1000)
        }
    }

    private fun hit(prefix: String) {
        val st = pstats.computeIfAbsent(prefix) { PStat() }
        st.everHit = true
        st.hits++
        st.misses = 0
    }

    private fun usable(prefix: String, failingSource: String?, avoidSame: Boolean): Boolean {
        val src = sourceOf(prefix)
        if (avoidSame && failingSource != null && src == failingSource) return false
        if (src != null && health.degraded(src)) return false
        val st = pstats[prefix]
        if (st != null && System.currentTimeMillis() < st.disabledUntil) return false
        return true
    }

    fun disabledPrefixes(): List<String> =
        pstats.filter { System.currentTimeMillis() < it.value.disabledUntil }.keys.toList()

    fun workingPrefixes(): Map<String, Int> =
        pstats.filter { it.value.hits > 0 }.mapValues { it.value.hits }

    fun resolve(
        original: AudioTrack,
        position: Long,
        avoidSameSource: Boolean,
        onFound: (AudioTrack) -> Unit,
        onFail: () -> Unit
    ) {
        val manager = stabilizer.manager
        if (manager == null) { onFail(); return }

        val failingSource = original.sourceManager?.sourceName
        val prefixes = cfg.fallbackSearchPrefixes
            .filter { usable(it, failingSource, avoidSameSource) }
            .take(cfg.fallbackMaxAttempts.coerceIn(1, 40))

        val title = original.info.title.replace(noise, " ").trim()
        val author = original.info.author.replace(" - Topic", "").trim()
        val query = "$title $author".replace(Regex("\\s+"), " ").trim().take(100)
        if (query.isBlank() || prefixes.isEmpty()) { onFail(); return }

        val startedAt = System.currentTimeMillis()

        fun acceptable(t: AudioTrack): Boolean {
            if (t.identifier == original.identifier) return false
            if (t.info.isStream) return false
            val tol = maxOf(cfg.fallbackDurationToleranceMs, original.duration / 10)
            return abs(t.duration - original.duration) <= tol
        }

        fun finish(prefix: String, t: AudioTrack) {
            try {
                hit(prefix)
                t.userData = original.userData
                if (t.isSeekable && position > 0) t.position = position
                found.incrementAndGet()
                log.info("Fallback: '{}' -> {} via {}", original.info.title, prefix, t.sourceManager?.sourceName)
                onFound(t)
            } catch (e: Throwable) { onFail() }
        }

        fun attempt(i: Int) {
            if (i >= prefixes.size || System.currentTimeMillis() - startedAt > cfg.fallbackTimeoutMs) {
                notFound.incrementAndGet(); onFail(); return
            }
            val prefix = prefixes[i]
            fun next() { miss(prefix); attempt(i + 1) }
            try {
                manager.loadItem(prefix + query, object : AudioLoadResultHandler {
                    override fun trackLoaded(track: AudioTrack) {
                        if (acceptable(track)) finish(prefix, track) else next()
                    }
                    override fun playlistLoaded(playlist: AudioPlaylist) {
                        val pick = playlist.tracks.firstOrNull { acceptable(it) }
                        if (pick != null) finish(prefix, pick) else next()
                    }
                    override fun noMatches() = next()
                    override fun loadFailed(exception: FriendlyException) = next()
                })
            } catch (e: Throwable) { next() }
        }
        attempt(0)
    }
}
