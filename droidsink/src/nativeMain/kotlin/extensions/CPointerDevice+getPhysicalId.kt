package extensions

import cnames.structs.libusb_device
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.refTo
import libusb.libusb_get_bus_number
import libusb.libusb_get_port_numbers
import model.usb.UsbPhysicalId

@OptIn(ExperimentalForeignApi::class)
typealias CPointerDevice = CPointer<libusb_device>

@OptIn(ExperimentalForeignApi::class)
internal fun CPointerDevice.getPhysicalId(): UsbPhysicalId {
    val bus = libusb_get_bus_number(this).toInt()

    val ports = UByteArray(8)
    val depth = libusb_get_port_numbers(this, ports.refTo(0), ports.size)

    return UsbPhysicalId(
        bus = bus,
        portPath = ports.take(depth).map { it.toInt() }
    )
}