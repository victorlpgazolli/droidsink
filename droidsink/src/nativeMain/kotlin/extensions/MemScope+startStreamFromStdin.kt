package extensions

import USB_WRITE_BUFFER_SIZE
import kotlinx.cinterop.CArrayPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.MemScope
import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.convert
import kotlinx.cinterop.plus
import platform.posix.STDIN_FILENO
import platform.posix.read

@OptIn(ExperimentalForeignApi::class)
internal fun MemScope.startStreamFromStdin(buffer: CArrayPointer<UByteVar>): Int {
    var total = 0
    while (total < USB_WRITE_BUFFER_SIZE) {
        val rawData = read(
            STDIN_FILENO,
            buffer + total,
            (USB_WRITE_BUFFER_SIZE - total).convert()
        )
        if (rawData <= 0) break
        total += rawData.toInt()
    }
    return total
}