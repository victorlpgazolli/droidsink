@file:OptIn(ExperimentalForeignApi::class)

package extensions

import AOA_ENDPOINT_IN
import USB_READ_BUFFER_SIZE
import cnames.structs.libusb_device_handle
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.convert
import platform.posix.FILE
import platform.posix.fflush
import platform.posix.fprintf
import platform.posix.fwrite
import platform.posix.stderr
import soxAudioOutputStreamProvider

internal fun CPointer<libusb_device_handle>.startStreamingAsyncFromClient() {
    val rawOutputStream: CPointer<FILE> by lazy { soxAudioOutputStreamProvider() }

    startStreamingAsyncInternal(
        id = "Client->Host",
        bufferSize = USB_READ_BUFFER_SIZE,
        accessoryEndpoint = AOA_ENDPOINT_IN,
        onTransferred = { (buffer, length) ->
            val written = fwrite(buffer, 1.convert(), length.convert(), rawOutputStream)
            if (written.toInt() < length) {
                fprintf(stderr, "Error sending data to sox playback\n")
            }
            fflush(rawOutputStream)
        }
    )
}