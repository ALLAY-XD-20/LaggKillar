package dev.its2pawan.laggkillar

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventListener

/** Delegates everything to Lavalink's manager, but attaches LaggKillar to every new player. */
class HookedManager(
    private val inner: AudioPlayerManager,
    private val listener: AudioEventListener
) : AudioPlayerManager by inner {
    override fun createPlayer(): AudioPlayer =
        inner.createPlayer().also { it.addListener(listener) }
}
