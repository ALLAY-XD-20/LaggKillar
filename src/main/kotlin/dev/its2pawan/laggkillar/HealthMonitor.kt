package dev.its2pawan.laggkillar

import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.lang.management.ManagementFactory
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/** Detects the real causes of audio lag on the server: long GC pauses, heap pressure, scheduler stalls. */
@Component
class HealthMonitor(private val cfg: LaggKillarConfig) {
    private val log = LoggerFactory.getLogger(HealthMonitor::class.java)
    private val exec = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "laggkillar-health").apply { isDaemon = true }
    }
    private val gcBeans = ManagementFactory.getGarbageCollectorMXBeans()
    private val mem = ManagementFactory.getMemoryMXBean()

    @Volatile var lastGcPauseMs = 0L
    @Volatile var heapPercent = 0
    @Volatile var lastStallMs = 0L
    @Volatile var cpuLoad = -1.0
    val gcWarnings = AtomicLong()
    val heapWarnings = AtomicLong()
    val stallWarnings = AtomicLong()

    private var prevGc = 0L
    private var prevTick = 0L

    @PostConstruct
    fun start() {
        if (!cfg.enabled || !cfg.healthMonitorEnabled) return
        prevGc = totalGcMs()
        prevTick = System.nanoTime()
        exec.scheduleAtFixedRate(::tick, cfg.healthIntervalMs, cfg.healthIntervalMs, TimeUnit.MILLISECONDS)
    }

    @PreDestroy
    fun stop() { exec.shutdownNow() }

    private fun totalGcMs() = gcBeans.sumOf { maxOf(it.collectionTime, 0L) }

    private fun tick() = runCatching {
        // Scheduler stall = how late this tick ran. >1s late means the JVM was frozen/starved.
        val now = System.nanoTime()
        val late = (now - prevTick) / 1_000_000 - cfg.healthIntervalMs
        prevTick = now
        lastStallMs = maxOf(late, 0)
        if (late > 1000) {
            stallWarnings.incrementAndGet()
            log.warn("JVM stall detected: tick {}ms late (GC, CPU starvation or noisy host)", late)
        }

        val gc = totalGcMs()
        lastGcPauseMs = gc - prevGc
        prevGc = gc
        if (lastGcPauseMs > cfg.gcPauseWarnMs) {
            gcWarnings.incrementAndGet()
            log.warn("GC spent {}ms in the last {}ms window - raise -Xmx or lower buffers", lastGcPauseMs, cfg.healthIntervalMs)
        }

        val heap = mem.heapMemoryUsage
        if (heap.max > 0) {
            heapPercent = (heap.used * 100 / heap.max).toInt()
            if (heapPercent >= cfg.heapWarnPercent) {
                heapWarnings.incrementAndGet()
                log.warn("Heap at {}% - playback may stutter, increase -Xmx", heapPercent)
            }
        }

        val os = ManagementFactory.getOperatingSystemMXBean()
        if (os is com.sun.management.OperatingSystemMXBean) cpuLoad = os.processCpuLoad
    }.onFailure { log.debug("health tick failed", it) }
}
