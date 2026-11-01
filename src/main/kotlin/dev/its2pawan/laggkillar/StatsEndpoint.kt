package dev.its2pawan.laggkillar

import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class StatsEndpoint(
    private val handler: RecoveryHandler,
    private val health: HealthMonitor,
    private val stabilizer: AdaptiveStabilizer,
    private val sources: SourceHealth,
    private val resolver: FallbackResolver,
    private val cfg: LaggKillarConfig
) {
    @GetMapping("/v4/laggkillar")
    fun stats() = mapOf(
        "plugin" to "LaggKillar",
        "author" to "Its2Pawan",
        "enabled" to cfg.enabled,
        "recovery" to mapOf(
            "recovered" to handler.recovered.get(),
            "failed" to handler.failed.get(),
            "skipped" to handler.skipped.get(),
            "fallbackPlayed" to handler.fallbackPlayed.get(),
            "fallbackFound" to resolver.found.get(),
            "fallbackNotFound" to resolver.notFound.get()
        ),
        "fallbackSources" to mapOf(
            "configured" to cfg.fallbackSearchPrefixes.size,
            "working" to resolver.workingPrefixes(),
            "paused" to resolver.disabledPrefixes()
        ),
        "sources" to mapOf(
            "degraded" to sources.degradedSources(),
            "faults" to sources.snapshot()
        ),
        "stabilizer" to mapOf(
            "currentBufferMs" to stabilizer.currentBufferMs,
            "raises" to stabilizer.raises.get(),
            "lowers" to stabilizer.lowers.get()
        ),
        "health" to mapOf(
            "heapPercent" to health.heapPercent,
            "lastGcPauseMs" to health.lastGcPauseMs,
            "lastStallMs" to health.lastStallMs,
            "cpuLoad" to health.cpuLoad,
            "gcWarnings" to health.gcWarnings.get(),
            "heapWarnings" to health.heapWarnings.get(),
            "stallWarnings" to health.stallWarnings.get()
        )
    )

    /** Prometheus text format, like NodeLink's /v4/metrics. */
    @GetMapping("/v4/laggkillar/metrics", produces = [MediaType.TEXT_PLAIN_VALUE])
    fun metrics(): String = buildString {
        fun m(n: String, v: Number) = append("laggkillar_").append(n).append(' ').append(v).append('\n')
        m("recovered_total", handler.recovered.get())
        m("recovery_failed_total", handler.failed.get())
        m("recovery_skipped_total", handler.skipped.get())
        m("fallback_played_total", handler.fallbackPlayed.get())
        m("degraded_sources", sources.degradedSources().size)
        m("buffer_ms", stabilizer.currentBufferMs)
        m("buffer_raises_total", stabilizer.raises.get())
        m("heap_percent", health.heapPercent)
        m("gc_pause_ms", health.lastGcPauseMs)
        m("stall_ms", health.lastStallMs)
        m("cpu_load", health.cpuLoad)
        m("gc_warnings_total", health.gcWarnings.get())
        m("stall_warnings_total", health.stallWarnings.get())
    }
}
