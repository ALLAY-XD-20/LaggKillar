package dev.its2pawan.laggkillar

import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.atomic.AtomicLong

/** Tracks which source plugin (youtube, spotify, soundcloud...) is failing right now. */
@Component
class SourceHealth(private val cfg: LaggKillarConfig) {
    private val recent = ConcurrentHashMap<String, ConcurrentLinkedDeque<Long>>()
    private val totals = ConcurrentHashMap<String, AtomicLong>()

    fun record(source: String, fault: Fault) {
        totals.computeIfAbsent("$source:$fault") { AtomicLong() }.incrementAndGet()
        if (fault == Fault.UNAVAILABLE) return // one bad video is not a broken plugin
        recent.computeIfAbsent(source) { ConcurrentLinkedDeque() }.add(System.currentTimeMillis())
    }

    fun degraded(source: String): Boolean {
        val q = recent[source] ?: return false
        val cutoff = System.currentTimeMillis() - cfg.degradeWindowMs
        while (true) {
            val head = q.peekFirst() ?: break
            if (head < cutoff) q.pollFirst() else break
        }
        return q.size >= cfg.degradeAfter
    }

    fun snapshot(): Map<String, Long> = totals.mapValues { it.value.get() }
    fun degradedSources(): List<String> = recent.keys.filter { degraded(it) }
}
