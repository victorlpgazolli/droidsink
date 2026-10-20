# Latency Study — DroidSink

A study of the full audio pipeline (host → USB → Android) with the goal of reducing
end-to-end latency, which was in the **~100–200 ms** range as documented in the README.

This document describes the original latency budget, the changes implemented in Phase 1,
and the roadmap for the following phases.

## Analyzed pipeline

### Playback (Host → Device), `run` command

```
BlackHole 2ch → sox (coreaudio→raw) → pipe → fread (Kotlin/Native)
  → libusb bulk OUT (AOA) → UsbManager.openAccessory fd (Android)
  → AudioTrack.write → speaker
```

### Microphone (Device → Host), `run --run-as-microphone` command

```
AudioRecord → FileOutputStream (accessory fd) → libusb bulk IN
  → fwrite (Kotlin/Native) → sox (raw→coreaudio) → BlackHole/SAI
```

## Original latency budget (playback)

| # | Stage | Code | Delay |
|---|-------|------|-------|
| 1 | CoreAudio HAL (BlackHole) | outside the project | ~10 ms |
| 2 | sox `--buffer 3840` (20 ms batch) | `SoxAudioStreamProvider.kt` | 0–20 ms |
| 3 | `fread` waits for the full chunk (3840 B = 20 ms) | `CPointerDevice+startStreamingAsyncFromHost.kt` | ~20 ms |
| 4 | USB with **1 buffer in flight**: the blocking refill runs inside the callback and delays the next `libusb_submit_transfer`, creating gaps on the wire | `CPointerDevice+startStreamingAsyncInternal.kt` (`numBuffers = 1`) | ~2–20 ms + jitter |
| 5 | Android accumulates the whole chunk (3840 B) before `AudioTrack.write` | `AudioOutputService.kt` | ~20 ms |
| 6 | AudioTrack buffer 3×20 ms = 60 ms; with clock drift (host faster than the device) the queue fills and stays pinned at the maximum | `AudioOutputService.kt` | ~40–60 ms |

**Theoretical total: ~110–150 ms + jitter**, consistent with the ~100–200 ms measured in the README.

### Microphone mode (additional problems found)

| Stage | Problem | Delay |
|-------|---------|-------|
| sox for playback (host) | `soxAudioOutputStreamProvider` **did not pass `--buffer`** → default of 8192 B | ~43 ms |
| `AudioRecord.read` | read the entire minimum buffer (blocking until complete) | ~20–40 ms |
| USB bulk IN | 4096 B transfers (`USB_READ_BUFFER_SIZE = 16384 / 4`) | ~21 ms |
| `FileOutputStream` | the getter created a **new** `FileOutputStream` on every access (2×/loop of 20 ms) | overhead/GC |

## Phase 1 — implemented

Low-risk changes, keeping the architecture (sox + pipe + libusb):

### 1. USB transfer pipelining (`USB_IN_FLIGHT_BUFFERS = 4`)

`numBuffers` from 1 → 4 in both directions (`CPointerDevice+startStreamingAsyncFromHost.kt`,
`CPointerDevice+startStreamingAsyncFromClient.kt`). The refill of one buffer now happens
while the other 3 are in flight, eliminating the gaps on the wire. With the wire always
fed, it is safe to shrink the buffers of the following stages without underrun.

### 2. Chunk from 20 ms → 5 ms (`FRAMES_PER_CHUNK = 240`)

`FRAMES_PER_CHUNK` dropped from 960 (20 ms) to 240 (5 ms), propagating to:

- `USB_WRITE_BUFFER_SIZE` / `USB_READ_BUFFER_SIZE`: 3840 → **960 B** (5 ms per transfer)
- sox `--buffer`: 3840 → **960 B** in both providers (capture **and** playback — the
  playback provider did not even have `--buffer`, costing ~43 ms in mic mode)

Locally validated that sox accepts `--buffer 960` and produces continuous data
(1 s @ 48 kHz/stereo/16-bit = exactly 192000 B).

### 3. Low-latency AudioTrack + partial writes (`AudioOutputService.kt`)

- `setPerformanceMode(PERFORMANCE_MODE_LOW_LATENCY)` → uses Android's fast mixer
  (available since minSdk 26, which is the project's minSdk), reducing the mixer buffer
  from tens of ms to ~5–20 ms.
- The playback loop no longer accumulates the whole chunk before writing: it now writes
  to the `AudioTrack` **any bytes read** from the accessory fd. The 20 ms (then 5 ms)
  accumulation delay disappears; the blocking `AudioTrack.write` applies natural
  backpressure.
- The power (RMS) calculation now operates on the bytes read, not on the full buffer.

### 4. Fixed microphone path

- `AudioInputService`: `FileOutputStream` created **only once** in `startRecording`
  (previously it was created on every loop iteration via the getter).
- `AudioRecord.read` reads 960 B slices (5 ms) instead of the entire minimum buffer
  (~20–40 ms), blocking only for what is needed.
- IN transfers of 4096 → 960 B, draining the device faster.

### Budget after Phase 1 (theoretical)

| Stage | Before | After |
|-------|--------|-------|
| CoreAudio HAL | ~10 ms | ~10 ms (fixed, outside the project's control) |
| sox `--buffer` | 0–20 ms | 0–5 ms |
| Host accumulation (`fread`) | ~20 ms | ~5 ms |
| USB (pipelined, 4 in flight) | 2–20 ms + gaps | ~2–5 ms continuous |
| Android accumulation | ~20 ms | ~0 ms (partial writes) |
| AudioTrack | ~40–60 ms | ~5–20 ms (fast track) |
| **Total** | **~110–150 ms** | **~25–50 ms** |

**Theoretical numbers — validation pending** (see "Measurement" below).

## Phase 2 — roadmap

1. **Clock drift (mandatory before tightening further)**: the host (BlackHole) and the
   device have independent oscillators. With small buffers, the difference accumulates and
   causes underrun (device faster) or a full queue (host faster). Mitigations: use the
   `AudioTrack` `AudioTimestamp` to adaptively drop/duplicate blocks, or a resampler with
   fine adjustment (e.g. libsamplerate in dynamic mode).
2. **AAudio/Oboe on Android**: replace `AudioTrack` with AAudio
   (`AAUDIO_PERFORMANCE_MODE_LOW_LATENCY`) for more aggressive fast-track routes and
   real-time callbacks (~2–5 ms mixer path).
3. **Direct CoreAudio/ALSA on the host**: replace sox + `popen` with AUHAL (macOS) and
   ALSA (Linux) with 256-frame callbacks (~5 ms). Eliminates two copies per pipe and sox's
   processing buffer. This is the change with the greatest structural payoff.
4. **Streaming thread priority on the host**: it currently runs at the worker's default
   priority; raising it (nice −20 / `pthread_setschedparam`) reduces jitter.
5. **Fix**: `genericUsbCallback` ignores the return value of `shouldKeepInflatingBuffer`
   (`CPointerDevice+startStreamingAsyncInternal.kt`) — at sox EOF it resubmits the old
   buffer indefinitely.

## Measurement (needed before publishing numbers)

The project has no latency instrumentation. Suggested harness:

1. **Impulse loopback**: inject a click (1 frame at maximum amplitude) on the host and
   detect the playback instant on the device via timestamped logcat; repeat N times and
   compute the mean/percentiles.
2. **Per-stage measurement**: mark timestamps on entering/leaving each stage (fread,
   transfer submit, callback, `AudioTrack.write`) for ~5 s and correlate by the audio
   content (the click works as a marker).
3. Compare before/after on the same device and cable; USB full-speed has scheduling jitter
   that varies across hubs/cables.

## Risks and caveats

- **Underruns**: small buffers without pipelining cause clicks; Phase 1 solves this with
  the 4 transfers in flight, but a stress test with a loaded host CPU is worthwhile.
- **Small sox `--buffer`**: increases sox's wakeup frequency (negligible on CPU: ~200
  cycles/s). Validated that it accepts 960 B.
- **Compatibility**: `PERFORMANCE_MODE_LOW_LATENCY` falls back to normal mode on devices
  without support/fast track unavailable — degraded behavior, never worse than the current
  one.
