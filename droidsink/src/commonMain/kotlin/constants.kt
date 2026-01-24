

const val SAMPLE_RATE = 48000
const val CHANNELS = 2
const val BYTES_PER_SAMPLE = 2
const val FRAMES_PER_CHUNK = 960 // 20 ms

const val BITS_PER_SAMPLE = 16

// capacity factor to sox's read buffer
// buffer = 15 * 128 = 1920 bytes (10ms)
const val AUDIO_READ_BUFFER_CAPACITY_FACTOR = 15


// capacity factor to Android AudioTrack buffer
// 3 * 20ms = 60ms total
const val ANDROID_AUDIO_TRACK_BUFFER_CAPACITY_FACTOR = 3