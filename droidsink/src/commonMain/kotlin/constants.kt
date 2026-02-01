

const val SAMPLE_RATE = 48000
const val CHANNELS = 2
const val BYTES_PER_SAMPLE = 2
const val FRAMES_PER_CHUNK = 960 // 20 ms

const val BITS_PER_SAMPLE = 16

// bytes per second calculation:
// 48.000 * 2 * 2 = 192.000 bytes per second
// 192.000 / 100 = 192 bytes per 1ms

// capacity factor to sox's read buffer
// buffer = 15 * 128 = 1920 bytes (10ms)
// 1920 / 192 = 10ms of buffered audio
const val AUDIO_READ_BUFFER_CAPACITY_FACTOR = 15

// USB data transfer buffer size in bytes
// current: 3840 bytes
// 3840 / 192 = 20ms of buffered audio
val USB_READ_BUFFER_SIZE =
    FRAMES_PER_CHUNK * CHANNELS * BYTES_PER_SAMPLE

// capacity factor to Android AudioTrack buffer
// 3 * 20ms = 60ms of buffered audio
const val ANDROID_AUDIO_TRACK_BUFFER_CAPACITY_FACTOR = 3

// latency summary:
// [PC] Audio read: ~10ms
// [PC] USB transfer: ~20ms
// [Android] AudioTrack buffer: ~60ms

// total = ~100ms of audio latency from PC to Android playback


const val APP_VERSION = "1.0.0-alpha-2"
const val APP_URL = "https://github.com/victorlpgazolli/droidsink/releases/download/$APP_VERSION/droidsink-release.apk"