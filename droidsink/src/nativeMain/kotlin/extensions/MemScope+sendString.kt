package extensions

import cnames.structs.libusb_device_handle
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.MemScope
import kotlinx.cinterop.cstr
import kotlinx.cinterop.reinterpret
import libusb.LIBUSB_ENDPOINT_OUT
import libusb.LIBUSB_REQUEST_TYPE_VENDOR
import libusb.libusb_control_transfer

@OptIn(ExperimentalForeignApi::class)
internal fun MemScope.sendString(handle: CPointer<libusb_device_handle>?, index: Int, string: String) {
    val data = string.cstr
    libusb_control_transfer(
        handle,
        (LIBUSB_ENDPOINT_OUT or LIBUSB_REQUEST_TYPE_VENDOR).toUByte(),
        52.toUByte(),
        0.toUShort(),
        index.toUShort(),
        data.ptr.reinterpret(),
        (string.length + 1).toUShort(),
        0.toUint()
    )
}