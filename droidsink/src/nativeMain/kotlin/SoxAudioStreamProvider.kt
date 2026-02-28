@file:OptIn(ExperimentalForeignApi::class)

import kotlinx.cinterop.ExperimentalForeignApi
import platform.posix.popen
import model.streaming.*

val soxAudioInputStreamProvider = AudioStreamProvider { audioInterfaceName ->
    val cmd = """
        sox --buffer $USB_WRITE_BUFFER_SIZE \
            -t coreaudio "$audioInterfaceName" \
            -r $SAMPLE_RATE -c $CHANNELS -b $BITS_PER_SAMPLE -e signed-integer -L \
            -t raw - 2>/dev/null
    """.trimIndent()

    val pipe = popen(cmd, "r")
        ?: error("Failed to start sox")

    pipe
}

val soxAudioOutputStreamProvider = AudioStreamProvider { audioInterfaceName ->
    val cmd = """
        sox -t raw -r $SAMPLE_RATE -c $CHANNELS -b $BITS_PER_SAMPLE -e signed-integer -L - \
        -t coreaudio "$audioInterfaceName" 2>/dev/null
    """.trimIndent()

    val pipe = popen(cmd, "w")
        ?: error("Failed to start sox for playback")

    pipe
}
