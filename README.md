<div align="center">

# LaggKillar

**Self-healing, lag-fighting stability plugin for Lavalink v4**

[![JitPack](https://jitpack.io/v/ALLAY-XD-20/LaggKillar.svg)](https://jitpack.io/#ALLAY-XD-20/LaggKillar)
![Lavalink](https://img.shields.io/badge/Lavalink-v4-blueviolet)
![Java](https://img.shields.io/badge/Java-17%2B-orange)

Made by **Its2Pawan** · GitHub: [ALLAY-XD-20](https://github.com/ALLAY-XD-20)

</div>

---

## What is LaggKillar?

LaggKillar is a Lavalink plugin that keeps music playing when things go wrong. It tunes audio quality and buffering, watches every track, and automatically repairs playback problems: stuck tracks, dropped streams, expired links, rate limits, and broken or blocked source plugins. It works on **every song, on every source**, and needs **no changes in your bot code**.

## Features

| Area | What it does |
|---|---|
| **Sound quality** | Opus encoding quality 10 and HIGH resampling by default, plus smooth seek ghosting |
| **Adaptive stabilizer** | Raises the audio buffer after lag, lowers it again when stable. Every new song uses the current value automatically |
| **Fast stuck detection** | Detects stuck tracks in 5 seconds (Lavalink default is 10) |
| **Auto recovery** | Resumes stuck or failed tracks from the exact same position, keeps your track user data |
| **Error understanding** | Recognises blocked/login-required, rate limit (429), expired URL (403/410), network, and unavailable errors from any source plugin |
| **22 fallback sources** | If a source is blocked or broken, the same song is found on another source, with a length check so the wrong song is never played |
| **Source health** | A source failing repeatedly is marked degraded and skipped automatically |
| **Auto-pausing prefixes** | Fallback prefixes you do not have installed pause themselves, so they cost no time |
| **Health watchdog** | Warns about long GC pauses, high heap usage and JVM stalls, the real causes of server-side lag |
| **Monitoring** | JSON stats and Prometheus metrics endpoints |
| **Race safe** | Never overwrites a song your bot already skipped to |

## Requirements

- Lavalink **v4.x**
- Java **17** or newer
- For fallback sources: the source plugins you want to use (YouTube plugin, LavaSrc, etc.)

## Installation

1. Add LaggKillar to the plugins list in your Lavalink `application.yml`:

```yaml
lavalink:
  plugins:
    - dependency: "com.github.ALLAY-XD-20:LaggKillar:1.4.1"
      repository: "https://jitpack.io"
```

2. Restart Lavalink. On start you should see a log line starting with `LaggKillar active`.
3. Done. The defaults work out of the box. See [`example.yml`](example.yml) for a complete ready-to-edit configuration.

> Replace `1.4.0` with the latest release tag shown on the JitPack badge above.

## Configuration

All options live under `plugins.laggkillar` and are optional.

### General and sound quality

| Option | Default | Description |
|---|---|---|
| `enabled` | `true` | Master switch for the whole plugin |
| `frameBufferDurationMs` | `10000` | Base audio buffer. Larger survives network hiccups but uses more RAM (range 1000 to 60000) |
| `opusEncodingQuality` | `10` | Opus quality 0 to 10. 10 is best |
| `resamplingQuality` | `HIGH` | `LOW`, `MEDIUM` or `HIGH` |
| `useSeekGhosting` | `true` | Smoother seeking |
| `playerCleanupThresholdMs` | `120000` | Idle players are cleaned after this time |
| `itemLoaderThreads` | `500` | Threads used for loading tracks |
| `trackStuckThresholdMs` | `5000` | How long without audio before a track counts as stuck |

### Adaptive stabilizer

| Option | Default | Description |
|---|---|---|
| `adaptiveEnabled` | `true` | Turn automatic buffer adjustment on or off |
| `adaptiveMaxBufferMs` | `30000` | The buffer never grows past this |
| `adaptiveStepMs` | `3000` | How much the buffer changes each time |
| `adaptiveDecayMs` | `600000` | Calm time before the buffer shrinks back |

The buffer also shrinks immediately, and never grows, while heap usage is above `heapWarnPercent`.

### Recovery

| Option | Default | Description |
|---|---|---|
| `recoveryEnabled` | `true` | Auto-resume stuck or failed tracks |
| `maxRecoveryAttempts` | `3` | Retries of the same track before switching source |
| `recoveryDelayMs` | `400` | Wait before a retry |
| `attemptResetMs` | `60000` | The attempt counter resets after this long without trouble |
| `rateLimitDelayMs` | `3000` | Longer wait after a rate limit (429) |

### Fallback sources

| Option | Default | Description |
|---|---|---|
| `fallbackEnabled` | `true` | Find the song on another source when one fails |
| `fallbackOnUnavailable` | `true` | Also do this for unavailable, region-blocked or login-gated songs |
| `fallbackSearchPrefixes` | 22 prefixes | Search prefixes, tried in order |
| `fallbackPrefixSources` | `{}` | Map your own prefix to a source name |
| `fallbackMaxAttempts` | `12` | Maximum sources tried per song |
| `fallbackTimeoutMs` | `20000` | Maximum total search time per song |
| `fallbackDurationToleranceMs` | `10000` | Allowed length difference (or 10 percent, whichever is larger) |
| `prefixDisableAfter` | `6` | Misses with zero hits before a prefix is paused |
| `prefixDisableMs` | `600000` | How long a missing prefix stays paused |
| `degradeAfter` | `3` | Failures within the window that mark a source degraded |
| `degradeWindowMs` | `300000` | The window for the degraded check |

### Health watchdog

| Option | Default | Description |
|---|---|---|
| `healthMonitorEnabled` | `true` | Enable the watchdog |
| `healthIntervalMs` | `5000` | Check interval |
| `gcPauseWarnMs` | `500` | Warn if garbage collection took longer than this per interval |
| `heapWarnPercent` | `90` | Warn above this heap usage |

## Fallback sources

Tried in this order (sources you do not have are skipped automatically):

| # | Prefix | Source | # | Prefix | Source |
|---|---|---|---|---|---|
| 1 | `ytmsearch:` | YouTube Music | 12 | `bcsearch:` | Bandcamp |
| 2 | `scsearch:` | SoundCloud | 13 | `pdsearch:` | Pandora |
| 3 | `dzsearch:` | Deezer | 14 | `gnsearch:` | Gaana |
| 4 | `spsearch:` | Spotify | 15 | `mcsearch:` | Mixcloud |
| 5 | `amsearch:` | Apple Music | 16 | `amksearch:` | Audiomack |
| 6 | `tdsearch:` | Tidal | 17 | `nndsearch:` | NicoNico |
| 7 | `qbsearch:` | Qobuz | 18 | `bilisearch:` | Bilibili |
| 8 | `ymsearch:` | Yandex Music | 19 | `vmsearch:` | Vimeo |
| 9 | `vksearch:` | VK Music | 20 | `twsearch:` | Twitch |
| 10 | `jssearch:` | JioSaavn | 21 | `nssearch:` | Napster |
| 11 | `ytsearch:` | YouTube | 22 | `ttsearch:` | TikTok |

Prefixes 1 to 12 are the standard names used by the YouTube plugin, Lavaplayer and LavaSrc. Prefixes 13 to 22 are best-effort names; plugins differ. If your plugin uses another prefix, add it:

```yaml
plugins:
  laggkillar:
    fallbackSearchPrefixes: ["ytmsearch:", "scsearch:", "mysearch:"]
    fallbackPrefixSources:
      "mysearch:": "mysource"
```

## How errors are handled

| Problem | What LaggKillar does |
|---|---|
| Track stuck / stream dropped | Raises the buffer for new songs, restarts the track at the same position |
| Network error or timeout | Retries the same track at the same position |
| Expired link (403 / 410), signature or cipher errors | Retries, then switches source |
| Rate limit (429) | Waits longer, then retries |
| Login required / bot check | Switches source immediately |
| Unavailable or region-blocked song | Finds the song on another source |
| A source keeps failing | Marks it degraded and bypasses it for a while |
| Song was skipped by your bot meanwhile | Does nothing, never interferes |

## Monitoring

Both endpoints need your normal Lavalink `Authorization` header.

```bash
curl -H "Authorization: youshallnotpass" http://localhost:2333/v4/laggkillar
```

Example response:

```json
{
  "plugin": "LaggKillar",
  "author": "Its2Pawan",
  "enabled": true,
  "recovery": { "recovered": 12, "failed": 1, "skipped": 0, "fallbackPlayed": 4, "fallbackFound": 4, "fallbackNotFound": 1 },
  "fallbackSources": { "configured": 22, "working": { "scsearch:": 3 }, "paused": ["ttsearch:"] },
  "sources": { "degraded": ["youtube"], "faults": { "youtube:BLOCKED": 5 } },
  "stabilizer": { "currentBufferMs": 13000, "raises": 1, "lowers": 0 },
  "health": { "heapPercent": 41, "lastGcPauseMs": 12, "lastStallMs": 0, "cpuLoad": 0.08, "gcWarnings": 0, "heapWarnings": 0, "stallWarnings": 0 }
}
```

Prometheus metrics: `GET /v4/laggkillar/metrics`

`laggkillar_recovered_total`, `laggkillar_recovery_failed_total`, `laggkillar_recovery_skipped_total`, `laggkillar_fallback_played_total`, `laggkillar_degraded_sources`, `laggkillar_buffer_ms`, `laggkillar_buffer_raises_total`, `laggkillar_heap_percent`, `laggkillar_gc_pause_ms`, `laggkillar_stall_ms`, `laggkillar_cpu_load`, `laggkillar_gc_warnings_total`, `laggkillar_stall_warnings_total`

## Tuning guide

- **Small VPS (1 to 2 GB RAM):** set `frameBufferDurationMs: 5000` and `adaptiveMaxBufferMs: 15000`.
- **Weak or unstable network:** raise `frameBufferDurationMs` to `15000` and `adaptiveMaxBufferMs` to `40000`.
- **Many players:** give Lavalink enough memory (`-Xmx`) and watch the heap warnings in the log.
- **Slow fallbacks:** reduce `fallbackSearchPrefixes` to only the sources you actually installed.
- **Do not want any song replaced by another source:** set `fallbackEnabled: false`.

## Troubleshooting

| Symptom | Fix |
|---|---|
| No `LaggKillar active` line in the log | Check the `dependency` line and that the server can reach `https://jitpack.io` |
| Plugin is not downloading | Make sure the version exists on the JitPack page |
| `Heap at 9x%` warnings | Increase Lavalink `-Xmx` or lower `frameBufferDurationMs` |
| `GC spent ... ms` or `JVM stall` warnings | Your host is overloaded. More CPU or RAM helps more than any plugin setting |
| Fallback never triggers | Install other source plugins and check `fallbackSources.working` in the stats endpoint |
| Fallback picked a different version of a song | Lower `fallbackDurationToleranceMs` for stricter matching |

## FAQ

**Does this need changes in my bot?** No. It works on the server side for every client.

**Will it change how my songs sound?** No filters or effects are added. It only sets quality options and fixes playback problems.

**Can it fix a bad network or a very weak server?** No. It reduces the damage and recovers automatically, but your hardware and network still set the limits.

**Does it work with live streams?** Stuck streams are retried. Streams are never swapped to another source.

**Can it repair a plugin that fails while loading a playlist or link?** No. That happens inside the source plugin before playback starts. LaggKillar acts once a song is playing or fails to play.

**Is it safe to run next to other plugins?** Yes. It only adds recovery logic and does not replace any source plugin.

## Credits

- **Developer:** Its2Pawan
- **GitHub:** [ALLAY-XD-20](https://github.com/ALLAY-XD-20)
- Built on [Lavalink](https://github.com/lavalink-devs/Lavalink) and Lavaplayer

## License

See the `LICENSE` file in this repository.
