package dev.its2pawan.laggkillar

import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager
import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.lang.management.ManagementFactory
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Self-applying stabilizer. Lavaplayer reads the frame buffer size every time a track starts,
 * so every new song automatically gets the current adaptive value - no client config needed.
 *  - lag/stuck/error seen  -> buffer grows (unless the heap is nearly full)
 *  - long calm period      -> buffer shrinks back to the base value
 */
@Component
class AdaptiveStabilizer(private val cfg: LaggKillarConfig) {
    private val log = LoggerFactory.getLogger(AdaptiveStabilizer::class.java)
    private val exec = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "laggkillar-stabilizer").apply { isDaemon = true }
    }
    private val started = AtomicBoolean(false)

    @Volatile var manager: AudioPlayerManager? = null
    @Volatile var currentBufferMs: Int = 0
    @Volatile private var lastIncident = 0L
    val raises = AtomicLong()
    val lowers = AtomicLong()

    private fun base() = cfg.frameBufferDurationMs.coerceIn(1_000, 60_000)
    private fun max() = maxOf(base(), cfg.adaptiveMaxBufferMs.coerceIn(1_000, 60_000))
    private fun step() = cfg.adaptiveStepMs.coerceIn(500, 15_000)

    private fun heapPercent(): Int {
        val h = ManagementFactory.getMemoryMXBean().heapMemoryUsage
        return if (h.max > 0) (h.used * 100 / h.max).toInt() else 0
    }

    @Synchronized
    fun attach(m: AudioPlayerManager) {
        manager = m
        currentBufferMs = base()
        m.setFrameBufferDuration(currentBufferMs)
        if (cfg.adaptiveEnabled && started.compareAndSet(false, true)) {
            exec.scheduleAtFixedRate({ runCatching { decay() } }, 30, 30, TimeUnit.SECONDS)
        }
    }

    @Synchronized
    fun onIncident(reason: String) {
        if (!cfg.enabled || !cfg.adaptiveEnabled) return
        lastIncident = System.currentTimeMillis()
        val m = manager ?: return
        if (heapPercent() >= cfg.heapWarnPercent) {
            log.warn("Not raising buffer: heap is at {}%", heapPercent())
            return
        }
        val next = minOf(max(), currentBufferMs + step())
        if (next != currentBufferMs) {
            currentBufferMs = next
            m.setFrameBufferDuration(next)
            raises.incrementAndGet()
            log.info("Stabilizer: buffer raised to {}ms ({}). New songs use it automatically.", next, reason)
        }
    }

    @Synchronized
    private fun decay() {
        val m = manager ?: return
        if (currentBufferMs <= base()) return
        val calm = System.currentTimeMillis() - lastIncident > cfg.adaptiveDecayMs
        // also shrink right away if memory is tight
        if (calm || heapPercent() >= cfg.heapWarnPercent) {
            val next = maxOf(base(), currentBufferMs - step())
            currentBufferMs = next
            m.setFrameBufferDuration(next)
            lowers.incrementAndGet()
            log.info("Stabilizer: buffer lowered to {}ms", next)
        }
    }

    @PreDestroy
    fun shutdown() { exec.shutdownNow() }
}
