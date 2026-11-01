package dev.its2pawan.laggkillar

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties(prefix = "plugins.laggkillar")
class LaggKillarConfig {
    var enabled: Boolean = true

    // Quality / buffering
    var frameBufferDurationMs: Int = 10_000
    var playerCleanupThresholdMs: Long = 120_000
    var opusEncodingQuality: Int = 10
    var resamplingQuality: String = "HIGH"
    var useSeekGhosting: Boolean = true
    var itemLoaderThreads: Int = 500

    /** Lavaplayer marks a track stuck after this long without audio (Lavalink default is 10000). Lower = faster self-heal. */
    var trackStuckThresholdMs: Long = 5_000

    // Adaptive stabilizer: auto-raises buffer after lag, auto-lowers when stable. Applies to every song.
    var adaptiveEnabled: Boolean = true
    var adaptiveMaxBufferMs: Int = 30_000
    var adaptiveStepMs: Int = 3_000
    var adaptiveDecayMs: Long = 600_000

    // Recovery
    var recoveryEnabled: Boolean = true
    var maxRecoveryAttempts: Int = 3
    var recoveryDelayMs: Long = 400
    /** If the last recovery was longer ago than this, the attempt counter resets. */
    var attemptResetMs: Long = 60_000

    // Cross-plugin bug resolver (YouTube / LavaSrc / SoundCloud / any source plugin)
    var fallbackEnabled: Boolean = true
    /** Songs that are unavailable/blocked/login-gated get re-found on another source. */
    var fallbackOnUnavailable: Boolean = true
    /** Tried in order (22 sources by default, see SourceCatalog). Prefixes you do not have are skipped automatically. */
    var fallbackSearchPrefixes: MutableList<String> = SourceCatalog.ORDER.toMutableList()
    /** Teach LaggKillar your own prefixes: "mysearch:" -> "mysource" (source name as the plugin reports it). */
    var fallbackPrefixSources: MutableMap<String, String> = mutableMapOf()
    /** Max sources tried per song, and max total time. */
    var fallbackMaxAttempts: Int = 12
    var fallbackTimeoutMs: Long = 20_000
    /** A prefix with this many misses and zero hits is switched off for a while (it is not installed). */
    var prefixDisableAfter: Int = 6
    var prefixDisableMs: Long = 600_000
    /** A fallback result must have about the same length, so the wrong song is never played. */
    var fallbackDurationToleranceMs: Long = 10_000
    var rateLimitDelayMs: Long = 3_000
    /** A source with this many failures inside the window is treated as broken and skipped. */
    var degradeAfter: Int = 3
    var degradeWindowMs: Long = 300_000

    // Health monitor (JVM lag detection)
    var healthMonitorEnabled: Boolean = true
    var healthIntervalMs: Long = 5_000
    var gcPauseWarnMs: Long = 500
    var heapWarnPercent: Int = 90
}
