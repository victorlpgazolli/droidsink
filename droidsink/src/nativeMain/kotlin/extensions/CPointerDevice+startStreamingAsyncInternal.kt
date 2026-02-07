package extensions

import cnames.structs.libusb_device_handle
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.StableRef
import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.asStableRef
import kotlinx.cinterop.nativeHeap
import kotlinx.cinterop.pointed
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.staticCFunction
import libusb.libusb_alloc_transfer
import libusb.libusb_fill_bulk_transfer
import libusb.libusb_submit_transfer
import libusb.libusb_transfer
import libusb.libusb_transfer_status
import model.streaming.AsyncStreamContext


@OptIn(ExperimentalForeignApi::class)
internal fun CPointer<libusb_device_handle>.startStreamingAsyncInternal(
    id: String,
    bufferSize: Int,
    accessoryEndpoint: UByte,
    numBuffers: Int = 1,
    shouldKeepInflatingBuffer: ((CPointer<UByteVar>) -> Boolean)? = null,
    onTransferred: ((Pair<CPointer<UByteVar>, Int>) -> Unit)? = null,
) {
    val context = AsyncStreamContext(id, accessoryEndpoint, bufferSize, onTransferred, shouldKeepInflatingBuffer)
    val stableContext = StableRef.create(context)

    repeat(numBuffers) {
        val transfer = libusb_alloc_transfer(0)
        val buffer = nativeHeap.allocArray<UByteVar>(bufferSize)

        shouldKeepInflatingBuffer?.invoke(buffer)

        libusb_fill_bulk_transfer(
            transfer,
            this,
            accessoryEndpoint,
            buffer.reinterpret(),
            bufferSize,
            staticCFunction(::genericUsbCallback),
            stableContext.asCPointer(),
            1000u
        )

        libusb_submit_transfer(transfer)
    }
    println("[$id] Async streaming initialized with $numBuffers buffers of $bufferSize bytes.")
}

@OptIn(ExperimentalForeignApi::class)
private fun genericUsbCallback(transfer: CPointer<libusb_transfer>?) {
    val usbTransfer = transfer?.pointed ?: return
    val context = usbTransfer.user_data?.asStableRef<AsyncStreamContext>()?.get() ?: return

    if (usbTransfer.status == libusb_transfer_status.LIBUSB_TRANSFER_COMPLETED) {
        val buffer = usbTransfer.buffer!!.reinterpret<UByteVar>()

        context.onTransferred?.invoke(Pair(buffer, usbTransfer.actual_length))

        context.shouldKeepInflatingBuffer?.invoke(buffer)

        libusb_submit_transfer(transfer)
    }
}