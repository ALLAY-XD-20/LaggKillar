package dev.its2pawan.laggkillar

import com.sedmelluq.discord.lavaplayer.player.AudioConfiguration
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager
import dev.arbjerg.lavalink.api.AudioPlayerManagerConfiguration
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/** Applies stability + sound-quality settings once; they then affect every song. */
@Component
class PlayerTuner(
    private val cfg: LaggKillarConfig,
    private val stabilizer: AdaptiveStabilizer
) : AudioPlayerManagerConfiguration {
    private val log = LoggerFactory.getLogger(PlayerTuner::class.java)

    override fun configure(manager: AudioPlayerManager): AudioPlayerManager {
        if (!cfg.enabled) return manager

        manager.setPlayerCleanupThreshold(cfg.playerCleanupThresholdMs.coerceAtLeast(10_000))
        manager.setUseSeekGhosting(cfg.useSeekGhosting)
        manager.setItemLoaderThreadPoolSize(cfg.itemLoaderThreads.coerceIn(10, 2000))
        manager.setTrackStuckThreshold(cfg.trackStuckThresholdMs.coerceIn(2_000, 30_000))

        val quality = runCatching {
            AudioConfiguration.ResamplingQuality.valueOf(cfg.resamplingQuality.uppercase())
        }.getOrDefault(AudioConfiguration.ResamplingQuality.HIGH)

        manager.configuration.apply {
            opusEncodingQuality = cfg.opusEncodingQuality.coerceIn(0, 10)
            resamplingQuality = quality
        }

        stabilizer.attach(manager) // sets the frame buffer and starts adaptive control

        log.info(
            "LaggKillar active: buffer={}ms (adaptive={}) stuck={}ms opus={} resample={}",
            stabilizer.currentBufferMs, cfg.adaptiveEnabled, cfg.trackStuckThresholdMs,
            cfg.opusEncodingQuality, quality
        )
        return manager
    }
}
