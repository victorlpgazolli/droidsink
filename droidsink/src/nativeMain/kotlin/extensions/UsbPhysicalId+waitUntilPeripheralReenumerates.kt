package extensions

import cnames.structs.libusb_context
import cnames.structs.libusb_device
import cnames.structs.libusb_device_handle
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CPointerVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.get
import kotlinx.cinterop.nativeHeap
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import libusb.libusb_device_descriptor
import libusb.libusb_free_device_list
import libusb.libusb_get_device_descriptor
import libusb.libusb_get_device_list
import libusb.libusb_open
import libusb.libusb_set_auto_detach_kernel_driver
import model.usb.UsbPhysicalId
import platform.posix.sleep
import platform.posix.time


@OptIn(ExperimentalForeignApi::class)
internal fun UsbPhysicalId.waitUntilPeripheralReenumerates(
    ctx: CPointer<libusb_context>?,
    expectedVendor: Int,
    expectedProducts: Set<Int>,
    timeoutSeconds: Int = 30
): CPointer<libusb_device_handle>? {

    val start = time(null)

    while (true) {
        if (timeoutSeconds > 0 && time(null) - start > timeoutSeconds) {
            return null
        }

        val listPtr = nativeHeap.alloc<CPointerVar<CPointerVar<libusb_device>>>()
        val count = libusb_get_device_list(ctx, listPtr.ptr)
        val devices = listPtr.value ?: continue

        for (i in 0 until count.toInt()) {
            val device = devices[i] ?: continue

            val phys = device.getPhysicalId()
            if (phys != this) continue

            val descriptor = nativeHeap.alloc<libusb_device_descriptor>()
            if (libusb_get_device_descriptor(device, descriptor.ptr) != 0) continue

            if (descriptor.idVendor.toInt() != expectedVendor) continue
            if (!expectedProducts.contains(descriptor.idProduct.toInt())) continue

            val handleVarPointer = nativeHeap.alloc<CPointerVar<libusb_device_handle>>()
            if (libusb_open(device, handleVarPointer.ptr) != 0) continue

            val handle = handleVarPointer.value ?: continue
            libusb_set_auto_detach_kernel_driver(handle, 1)

            libusb_free_device_list(devices, 1)
            return handle
        }

        libusb_free_device_list(devices, 1)
        sleep(1u)
    }
}
