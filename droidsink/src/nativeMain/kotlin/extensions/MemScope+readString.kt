package extensions

import cnames.structs.libusb_device_handle
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.MemScope
import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.toKString
import libusb.libusb_get_string_descriptor_ascii

@OptIn(ExperimentalForeignApi::class)
internal fun MemScope.readString(handle: CPointer<libusb_device_handle>?, index: UByte): String {
    if (handle == null || index == 0.toUByte()) return "Unknown"
    val buffer = allocArray<UByteVar>(256)
    val stringDescriptor = libusb_get_string_descriptor_ascii(handle, index, buffer, 256)
    return if (stringDescriptor > 0) buffer.reinterpret<ByteVar>().toKString() else "Unknown"
}