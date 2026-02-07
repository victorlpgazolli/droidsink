package extensions

import AOA_ENDPOINT_OUT
import USB_WRITE_BUFFER_SIZE
import cnames.structs.libusb_device_handle
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.convert
import kotlinx.cinterop.plus
import platform.posix.FILE
import platform.posix.feof
import platform.posix.ferror
import platform.posix.fread
import soxAudioInputStreamProvider


@OptIn(ExperimentalForeignApi::class)
internal fun CPointer<libusb_device_handle>.startStreamingAsyncFromHost() {
    val rawInputStream: CPointer<FILE> by lazy { soxAudioInputStreamProvider() }

    val bufferSize = USB_WRITE_BUFFER_SIZE

    startStreamingAsyncInternal(
        id = "Host->Client",
        bufferSize = bufferSize,
        accessoryEndpoint = AOA_ENDPOINT_OUT,
        shouldKeepInflatingBuffer = { buffer ->
            var total = 0
            while (total < bufferSize) {
                val rawData = fread(buffer + total, 1.convert(), (bufferSize - total).convert(), rawInputStream)
                if (rawData <= 0UL) {
                    if (feof(rawInputStream) != 0 || ferror(rawInputStream) != 0) break
                    continue
                }
                total += rawData.toInt()
            }
            total == bufferSize
        }
    )
}