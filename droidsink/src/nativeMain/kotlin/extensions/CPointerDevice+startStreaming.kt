package extensions

import cnames.structs.libusb_device_handle
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.toCPointer
import kotlinx.cinterop.toLong
import model.streaming.StreamingType
import kotlin.native.concurrent.TransferMode
import kotlin.native.concurrent.withWorker


@OptIn(ExperimentalForeignApi::class)
fun CPointer<libusb_device_handle>.startStreaming(
    type: StreamingType
) = withWorker {
    execute(TransferMode.SAFE, { Pair(this@startStreaming.toLong(), type) }) { (handlePtr, type) ->
        val handle = handlePtr.toCPointer<libusb_device_handle>()
        memScoped {
            handle?.let {
                when(type) {
                    is StreamingType.ClientToHost -> handle.startStreamingAsyncFromClient()
                    is StreamingType.HostToClient -> handle.startStreamingAsyncFromHost()
                }
            }
        }
    }
}