@file:OptIn(ExperimentalForeignApi::class)


import kotlinx.cinterop.*
import libusb.*
import platform.posix.*
import cnames.structs.*
import model.peripheral.*
import model.streaming.*
import extensions.*
import model.UsbSession
import kotlin.collections.plusAssign
import kotlin.concurrent.AtomicInt

val sessionActive: AtomicInt = AtomicInt(1)

internal class UsbSessionInternal(private val contextPointer: CPointer<libusb_context>) : UsbSession {

    override fun listAccessories(): List<Peripheral> = memScoped {
        listAccessoriesInternal()
    }

    override fun setupAccessoryMode(peripheral: Peripheral) = memScoped {
        setupAccessoryModeInternal(peripheral.vendorId.toUShort(), peripheral.productId.toUShort())
    }

    override fun waitUntilAccessoryReady(expectedVendor: Int, expectedProducts: Set<Int>, timeoutSeconds: Int) = memScoped {
        waitUntilAccessoryReadyInternal(expectedVendor, expectedProducts, timeoutSeconds)
    }

    override fun startStreamingFromPeripheral(peripheral: Peripheral, type: StreamingType) = memScoped {
        peripheral.startStreamingInternal(type)
    }

    override fun getAccessoryInfo(peripheral: Peripheral): Peripheral? = memScoped {
        getAccessoryInfoInternal(peripheral.vendorId.toUShort(), peripheral.productId.toUShort())
    }


    private fun MemScope.listAccessoriesInternal(): List<Peripheral> {
        val list = mutableListOf<Peripheral>()
        val devicesPtr = alloc<CPointerVar<CPointerVar<libusb_device>>>()
        val count = libusb_get_device_list(contextPointer, devicesPtr.ptr)
        val devices = devicesPtr.value ?: return emptyList()

        for (i in 0 until count.toInt()) {
            val device = devices[i] ?: continue
            val descriptor = alloc<libusb_device_descriptor>()
            libusb_get_device_descriptor(device, descriptor.ptr)

            val handlePtr = alloc<CPointerVar<libusb_device_handle>>()
            if (libusb_open(device, handlePtr.ptr) == 0) {
                val h = handlePtr.value
                list += Peripheral(
                    name = readString(h, descriptor.iProduct),
                    serialNumber = readString(h, descriptor.iSerialNumber),
                    vendorId = descriptor.idVendor.toVendorId(),
                    productId = descriptor.idProduct.toProductId()
                )
                libusb_close(h)
            }
        }

        libusb_free_device_list(devices, 1)
        return list
    }


    private fun MemScope.setupAccessoryModeInternal(vendorId: UShort, productId: UShort) {
        val handle = libusb_open_device_with_vid_pid(contextPointer, vendorId, productId)
            ?: return

        libusb_set_auto_detach_kernel_driver(handle, 1)

        val proto = allocArray<UByteVar>(2)

        libusb_control_transfer(
            handle,
            (LIBUSB_ENDPOINT_IN or LIBUSB_REQUEST_TYPE_VENDOR).toUByte(),
            51u,
            0u,
            0u,
            proto,
            2u,
            0u
        )

        sendString(handle, 0, "Victor")
        sendString(handle, 1, "DroidSink")
        sendString(handle, 2, "PCM Audio")
        sendString(handle, 3, "1.0")
        sendString(handle, 4, "http://localhost")
        sendString(handle, 5, "0001")

        val physicalId = libusb_get_device(handle)
            ?.getPhysicalId()

        libusb_control_transfer(
            handle,
            (LIBUSB_ENDPOINT_OUT or LIBUSB_REQUEST_TYPE_VENDOR).toUByte(),
            53u,
            0u,
            0u,
            null,
            0u,
            0u
        )

        libusb_close(handle)

        sleep(2u)

        physicalId?.waitUntilPeripheralReenumerates(
            contextPointer,
            expectedVendor = GOOGLE_VID,
            expectedProducts = AOA_PIDS
        )
    }

    private fun MemScope.getAccessoryInfoInternal(vendorId: UShort, productId: UShort): Peripheral? {
        val handle = libusb_open_device_with_vid_pid(contextPointer, vendorId, productId)
            ?: return null

        val descriptor = alloc<libusb_device_descriptor>()
        libusb_get_device_descriptor(libusb_get_device(handle), descriptor.ptr)

        val info = Peripheral(
            name = readString(handle, descriptor.iProduct),
            serialNumber = readString(handle, descriptor.iSerialNumber),
            vendorId = vendorId.toVendorId(),
            productId = productId.toProductId()
        )

        libusb_close(handle)
        return info
    }
    private fun Peripheral.startStreamingInternal(type: StreamingType) {

        val newPeripheralInfo = findPeripheralBySerialNumber(this.serialNumber)
            ?: error("Peripheral with serial number ${this.serialNumber} not found.")

        val handle = libusb_open_device_with_vid_pid(
            contextPointer,
            newPeripheralInfo.vendorId.toUShort(),
            newPeripheralInfo.productId.toUShort()
        ) ?: error("Failed to open device ${this.name} for streaming.")

        libusb_set_auto_detach_kernel_driver(handle, 1)

        val claim = libusb_claim_interface(handle, AOA_INTERFACE)
        if (claim != 0) {
            error("libusb_claim_interface failed: ${libusb_error_name(claim)}")
        }

        handle.startStreaming(type)

        while (sessionActive.value == 1) {
            val result = libusb_handle_events(contextPointer)
            if (result != 0) {
                fprintf(stderr, "Event handler error: %s\n", libusb_error_name(result))
                break
            }
        }

        println("Stopping USB streaming...")
        libusb_release_interface(handle, AOA_INTERFACE) // Libera a interface 0
        libusb_close(handle)
    }

    private fun MemScope.waitUntilAccessoryReadyInternal(
        expectedVendor: Int = GOOGLE_VID,
        expectedProducts: Set<Int> = AOA_PIDS,
        timeoutSeconds: Int = 30
    ) {
        val start = time(null)

        while (true) {
            if (timeoutSeconds > 0 && time(null) - start > timeoutSeconds) {
                error("Timeout waiting for accessory to be ready")
            }

            val devicesPtr = alloc<CPointerVar<CPointerVar<libusb_device>>>()
            val count = libusb_get_device_list(contextPointer, devicesPtr.ptr)
            val devices = devicesPtr.value ?: continue

            for (i in 0 until count.toInt()) {
                val dev = devices[i] ?: continue

                val descriptor = alloc<libusb_device_descriptor>()
                if (libusb_get_device_descriptor(dev, descriptor.ptr) != 0) continue

                if (descriptor.idVendor.toInt() != expectedVendor) continue
                if (!expectedProducts.contains(descriptor.idProduct.toInt())) continue

                val handlePtr = alloc<CPointerVar<libusb_device_handle>>()
                if (libusb_open(dev, handlePtr.ptr) != 0) continue

                val handle = handlePtr.value ?: continue
                libusb_set_auto_detach_kernel_driver(handle, 1)

                if (libusb_claim_interface(handle, 0) == 0) {
                    libusb_release_interface(handle, 0)
                    libusb_close(handle)
                    libusb_free_device_list(devices, 1)

                    println("Accessory ready for streaming.")
                    return
                }

                libusb_close(handle)
            }

            libusb_free_device_list(devices, 1)
            sleep(1u)
        }
    }
}
