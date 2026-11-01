package dev.its2pawan.laggkillar

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException
import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import dev.arbjerg.lavalink.api.IPlayer
import dev.arbjerg.lavalink.api.PluginEventHandler
import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.Collections
import java.util.WeakHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * Self-healing for every song and every source plugin:
 *  1. classifies the error (blocked / rate limit / expired url / network / unavailable)
 *  2. retries the same track from the same position when that can help
 *  3. falls back to another source when the plugin is blocked, broken or the song is unavailable
 * Never overwrites a track the client already moved on to.
 */
@Component
class RecoveryHandler(
    private val cfg: LaggKillarConfig,
    private val stabilizer: AdaptiveStabilizer,
    private val health: SourceHealth,
    private val resolver: FallbackResolver
) : PluginEventHandler() {
    private val log = LoggerFactory.getLogger(RecoveryHandler::class.java)

    private class State(
        @Volatile var id: String,
        @Volatile var attempts: Int = 0,
        @Volatile var recovering: Boolean = false,
        @Volatile var pending: Boolean = false,
        @Volatile var fallbackTried: Boolean = false,
        @Volatile var lastAt: Long = 0
    )

    private val states: MutableMap<AudioPlayer, State> = Collections.synchronizedMap(WeakHashMap())
    private val scheduler = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "laggkillar-recovery").apply { isDaemon = true }
    }

    val recovered = AtomicLong()
    val failed = AtomicLong()
    val skipped = AtomicLong()
    val fallbackPlayed = AtomicLong()

    @PreDestroy
    fun shutdown() { scheduler.shutdownNow() }

    override fun onTrackStart(player: IPlayer, track: AudioTrack) {
        val st = states[player.audioPlayer] ?: return
        if (st.recovering) st.recovering = false else states.remove(player.audioPlayer)
    }

    override fun onTrackStuck(player: IPlayer, track: AudioTrack, thresholdMs: Long) {
        handle(player, track, Fault.NETWORK, "stuck ${thresholdMs}ms", true)
    }

    override fun onTrackException(player: IPlayer, track: AudioTrack, exception: FriendlyException) {
        val fault = ErrorClassifier.classify(exception)
        handle(player, track, fault, "$fault: ${exception.message}", fault == Fault.NETWORK || fault == Fault.OTHER)
    }

    private fun handle(player: IPlayer, track: AudioTrack, fault: Fault, reason: String, raiseBuffer: Boolean) {
        if (!cfg.enabled) return
        val source = track.sourceManager?.sourceName ?: "unknown"
        health.record(source, fault)
        if (raiseBuffer) stabilizer.onIncident(reason)
        recover(player, track, reason, fault, source)
    }

    private fun recover(player: IPlayer, track: AudioTrack, reason: String, fault: Fault, source: String) {
        if (!cfg.recoveryEnabled) return
        val ap = player.audioPlayer
        val now = System.currentTimeMillis()

        val st = synchronized(states) {
            val s = states.getOrPut(ap) { State(track.identifier) }
            if (s.id != track.identifier || now - s.lastAt > cfg.attemptResetMs) {
                s.id = track.identifier
                s.attempts = 0
                s.fallbackTried = false
            }
            s
        }
        if (st.pending) return

        val canFallback = cfg.fallbackEnabled && !track.info.isStream && !st.fallbackTried
        val goFallback = canFallback && (
            fault == Fault.BLOCKED ||
            (fault == Fault.UNAVAILABLE && cfg.fallbackOnUnavailable) ||
            health.degraded(source) ||
            st.attempts >= cfg.maxRecoveryAttempts
        )
        val position = (ap.playingTrack ?: track).position
        st.lastAt = now

        if (goFallback) {
            st.fallbackTried = true
            st.pending = true
            val avoid = fault == Fault.BLOCKED || fault == Fault.RATE_LIMIT ||
                fault == Fault.EXPIRED || health.degraded(source)
            log.info("'{}' failed on {} ({}), looking on another source", track.info.title, source, reason)
            resolver.resolve(track, position, avoid, onFound = { nt ->
                scheduler.execute {
                    try {
                        val cur = ap.playingTrack
                        if (cur != null && cur.identifier != track.identifier) { skipped.incrementAndGet(); return@execute }
                        st.recovering = true
                        ap.playTrack(nt)
                        recovered.incrementAndGet()
                        fallbackPlayed.incrementAndGet()
                    } catch (t: Throwable) {
                        st.recovering = false
                        log.error("Fallback play failed", t)
                    } finally { st.pending = false }
                }
            }, onFail = {
                st.pending = false
                failed.incrementAndGet()
                log.warn("No fallback found for '{}'", track.info.title)
            })
            return
        }

        if (fault == Fault.UNAVAILABLE) return // retrying an unavailable song never helps
        if (st.attempts >= cfg.maxRecoveryAttempts) {
            failed.incrementAndGet()
            log.warn("Giving up on '{}' after {} attempts ({})", track.info.title, st.attempts, reason)
            states.remove(ap)
            return
        }
        st.attempts++
        st.pending = true
        val attempt = st.attempts
        val delay = if (fault == Fault.RATE_LIMIT) maxOf(cfg.recoveryDelayMs, cfg.rateLimitDelayMs) else cfg.recoveryDelayMs

        scheduler.schedule({
            try {
                val current = ap.playingTrack
                if (current != null && current.identifier != track.identifier) {
                    skipped.incrementAndGet()
                    return@schedule
                }
                val clone = track.makeClone()
                clone.userData = track.userData
                if (track.isSeekable && position > 0) clone.position = position
                st.recovering = true
                ap.playTrack(clone)
                recovered.incrementAndGet()
                log.info("Recovered '{}' at {}ms (attempt {}/{}; {})",
                    track.info.title, position, attempt, cfg.maxRecoveryAttempts, reason)
            } catch (t: Throwable) {
                st.recovering = false
                log.error("Recovery failed for '{}'", track.info.title, t)
            } finally {
                st.pending = false
            }
        }, delay, TimeUnit.MILLISECONDS)
    }
}
