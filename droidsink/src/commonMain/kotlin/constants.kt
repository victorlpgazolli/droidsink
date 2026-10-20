

const val SAMPLE_RATE = 48000
const val CHANNELS = 2
const val BYTES_PER_SAMPLE = 2
const val FRAMES_PER_CHUNK = 240 // 5 ms

const val BITS_PER_SAMPLE = 16

// bytes per second calculation:
// 48.000 * 2 * 2 = 192.000 bytes per second
// 192.000 / 1000 = 192 bytes per 1ms
// 192 * 5 = 960 bytes per 5ms

// sox internal processing buffer
// 5ms of audio per buffer, applied to both capture and playback sox instances
const val SOX_PROCESSING_BUFFER_SIZE = FRAMES_PER_CHUNK * CHANNELS * BYTES_PER_SAMPLE // 960

// USB data transfer buffer size in bytes
// current: 960 bytes
// 960 / 192 = 5ms of buffered audio per transfer
val USB_WRITE_BUFFER_SIZE =
    FRAMES_PER_CHUNK * CHANNELS * BYTES_PER_SAMPLE

// USB data transfer buffer size in bytes for the microphone (client->host) path
// 960 / 192 = 5ms of buffered audio per transfer
val USB_READ_BUFFER_SIZE = FRAMES_PER_CHUNK * CHANNELS * BYTES_PER_SAMPLE

// capacity factor to Android AudioTrack buffer
// max(getMinBufferSize(...), 2 * 5ms chunk)
// on top of that, PERFORMANCE_MODE_LOW_LATENCY keeps the mixer path short
const val ANDROID_AUDIO_TRACK_BUFFER_CAPACITY_FACTOR = 2

// theoretical latency summary (see docs/latency-study.md):
// [PC] sox processing: ~5ms
// [PC] USB transfer: ~5ms (4 transfers in flight, pipelined)
// [Android] AudioTrack fast track buffer: ~5-20ms
//
// total = ~25-50ms of audio latency from PC to Android playback
// (plus ~10ms of CoreAudio HAL buffer, outside of this project's control)


const val APP_VERSION = "1.1.0"
const val APP_URL = "https://github.com/victorlpgazolli/droidsink/releases/download/$APP_VERSION/droidsink-release.apk"

const val LOG_TAG = "DroidSink"

const val BUNDLE_ID = "dev.victorlpgazolli.mobilesink"