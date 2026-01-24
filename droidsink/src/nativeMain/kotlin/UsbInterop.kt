@file:OptIn(ExperimentalForeignApi::class)

import kotlinx.cinterop.*
import libusb.*
import platform.posix.*
import cnames.structs.*

const val ACCESSORY_PID: UShort = 0x2D00u
const val ACCESSORY_ADB_PID: UShort = 0x2D01u

const val AMAZON_VID = 0x1949
const val ECHO_PID = 0x2048
const val SAMSUNG_VID = 0x04E8
const val XIAOMI_VID = 0x2717
const val GOOGLE_VID = 0x18D1
private val AOA_PIDS = setOf(
    0x2D00, // accessory
    0x2D01, // accessory + adb
    0x2D02, // audio
    0x2D03, // audio + adb
    0x2D04, // accessory + audio
    0x2D05  // accessory + audio + adb
)

fun UShort.toVendorId(): VendorId = VendorId(this.toString())
fun UShort.toProductId(): ProductId = ProductId(this.toString())

value class VendorId(val id: String) {
    fun toUShort(): UShort = id.toUShort()
}

value class ProductId(val id: String) {
    fun toUShort(): UShort = id.toUShort()
}

data class Peripheral(
    val name: String,
    val serialNumber: String,
    val vendorId: VendorId,
    val productId: ProductId
)

private fun <T> withContext(block: MemScope.(CPointer<libusb_context>) -> T): T =
    memScoped {
        val ctxPtr = alloc<CPointerVar<libusb_context>>()
        libusb_init(ctxPtr.ptr)
        val ctx = ctxPtr.value!!
        try {
            block(ctx)
        } finally {
            libusb_exit(ctx)
        }
    }

class UsbInterop {

    data class UsbPhysicalId(
        val bus: Int,
        val portPath: List<Int>
    )

    private fun getPhysicalId(device: CPointer<libusb_device>): UsbPhysicalId {
        val bus = libusb_get_bus_number(device).toInt()

        val ports = UByteArray(8)
        val depth = libusb_get_port_numbers(device, ports.refTo(0), ports.size)

        return UsbPhysicalId(
            bus = bus,
            portPath = ports.take(depth).map { it.toInt() }
        )
    }

    fun listAccessories(): List<Peripheral> = withContext { ctx ->
        val list = mutableListOf<Peripheral>()
        val devicesPtr = alloc<CPointerVar<CPointerVar<libusb_device>>>()
        val count = libusb_get_device_list(ctx, devicesPtr.ptr)
        val devices = devicesPtr.value ?: return@withContext emptyList()

        for (i in 0 until count.toInt()) {
            val dev = devices[i] ?: continue
            val desc = alloc<libusb_device_descriptor>()
            libusb_get_device_descriptor(dev, desc.ptr)

            val handlePtr = alloc<CPointerVar<libusb_device_handle>>()
            if (libusb_open(dev, handlePtr.ptr) == 0) {
                val h = handlePtr.value
                list += Peripheral(
                    name = readString(h, desc.iProduct),
                    serialNumber = readString(h, desc.iSerialNumber),
                    vendorId = desc.idVendor.toVendorId(),
                    productId = desc.idProduct.toProductId()
                )
                libusb_close(h)
            }
        }

        libusb_free_device_list(devices, 1)
        list
    }

    fun setupAccessoryMode(peripheral: Peripheral) =
        setupAccessoryMode(peripheral.vendorId.toUShort(), peripheral.productId.toUShort())

    fun getAccessoryInfo(peripheral: Peripheral): Peripheral? =
        getAccessoryInfo(peripheral.vendorId.toUShort(), peripheral.productId.toUShort())

    private fun sendString(handle: CPointer<libusb_device_handle>?, index: Int, string: String) = memScoped {
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
    private fun setupAccessoryMode(vendorId: UShort, productId: UShort) = withContext { ctx ->

        val handle = libusb_open_device_with_vid_pid(ctx, vendorId, productId)
            ?: return@withContext

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

        val physicalId = libusb_get_device(handle)?.let { getPhysicalId(it) }

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

        physicalId?.let {
            waitUntilPeripheralReenumerates(
                ctx,
                it,
                expectedVendor = GOOGLE_VID,
                expectedProducts = AOA_PIDS
            )
        }
    }

    private fun getAccessoryInfo(vendorId: UShort, productId: UShort) = withContext<Peripheral?> { ctx ->
        val handle = libusb_open_device_with_vid_pid(ctx, vendorId, productId)
            ?: return@withContext null

        val desc = alloc<libusb_device_descriptor>()
        libusb_get_device_descriptor(libusb_get_device(handle), desc.ptr)

        val info = Peripheral(
            name = readString(handle, desc.iProduct),
            serialNumber = readString(handle, desc.iSerialNumber),
            vendorId = vendorId.toVendorId(),
            productId = productId.toProductId()
        )

        libusb_close(handle)
        info
    }
    private fun waitUntilPeripheralReenumerates(
        ctx: CPointer<libusb_context>?,
        original: UsbPhysicalId,
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
                val dev = devices[i] ?: continue

                val phys = getPhysicalId(dev)
                if (phys != original) continue

                val desc = nativeHeap.alloc<libusb_device_descriptor>()
                if (libusb_get_device_descriptor(dev, desc.ptr) != 0) continue

                if (desc.idVendor.toInt() != expectedVendor) continue
                if (!expectedProducts.contains(desc.idProduct.toInt())) continue

                val hVar = nativeHeap.alloc<CPointerVar<libusb_device_handle>>()
                if (libusb_open(dev, hVar.ptr) != 0) continue

                val handle = hVar.value ?: continue
                libusb_set_auto_detach_kernel_driver(handle, 1)

                libusb_free_device_list(devices, 1)
                return handle
            }

            libusb_free_device_list(devices, 1)
            sleep(1u)
        }
    }

    fun waitUntilAccessoryReady(
        expectedVendor: Int = GOOGLE_VID,
        expectedProducts: Set<Int> = AOA_PIDS,
        timeoutSeconds: Int = 30
    ) {
        withContext { ctx ->
            val start = time(null)

            while (true) {
                if (timeoutSeconds > 0 && time(null) - start > timeoutSeconds) {
                    error("Timeout waiting for accessory to be ready")
                }

                val devicesPtr = alloc<CPointerVar<CPointerVar<libusb_device>>>()
                val count = libusb_get_device_list(ctx, devicesPtr.ptr)
                val devices = devicesPtr.value ?: continue

                for (i in 0 until count.toInt()) {
                    val dev = devices[i] ?: continue

                    val desc = alloc<libusb_device_descriptor>()
                    if (libusb_get_device_descriptor(dev, desc.ptr) != 0) continue

                    if (desc.idVendor.toInt() != expectedVendor) continue
                    if (!expectedProducts.contains(desc.idProduct.toInt())) continue

                    val handlePtr = alloc<CPointerVar<libusb_device_handle>>()
                    if (libusb_open(dev, handlePtr.ptr) != 0) continue

                    val handle = handlePtr.value ?: continue
                    libusb_set_auto_detach_kernel_driver(handle, 1)

                    if (libusb_claim_interface(handle, 0) == 0) {
                        libusb_release_interface(handle, 0)
                        libusb_close(handle)
                        libusb_free_device_list(devices, 1)

                        println("Accessory ready for streaming.")
                        return@withContext
                    }

                    libusb_close(handle)
                }

                libusb_free_device_list(devices, 1)
                sleep(1u)
            }
        }
    }

    fun startStreamingFromPeripheral(peripheral: Peripheral) {
        withContext { ctx ->

            val handle = libusb_open_device_with_vid_pid(
                ctx,
                peripheral.vendorId.toUShort(),
                peripheral.productId.toUShort()
            ) ?: error("Failed to open device ${peripheral.name} for streaming.")

            libusb_set_auto_detach_kernel_driver(handle, 1)

            startStreaming(handle)

            libusb_close(handle)
        }
    }
    private fun startStreaming(handle: CPointer<libusb_device_handle>) = memScoped {
        val AOA_INTERFACE = 0
        val AOA_ENDPOINT_OUT: UByte = 0x01u

        val SAMPLE_RATE = 48000
        val CHANNELS = 2
        val BYTES_PER_SAMPLE = 2
        val FRAMES_PER_CHUNK = 960          // 20 ms
        val BUFFER_SIZE =
            FRAMES_PER_CHUNK * CHANNELS * BYTES_PER_SAMPLE // 3840 bytes

        libusb_set_auto_detach_kernel_driver(handle, 1)

        val claim = libusb_claim_interface(handle, AOA_INTERFACE)
        if (claim != 0) {
            error("libusb_claim_interface failed: ${libusb_error_name(claim)}")
        }

        println("Streaming is active (using buffer with ${BUFFER_SIZE} bytes)...")

        val buffer = allocArray<UByteVar>(BUFFER_SIZE)
        val transferred = alloc<IntVar>()

        while (true) {

            var total = 0
            while (total < BUFFER_SIZE) {
                val r = read(
                    STDIN_FILENO,
                    buffer + total,
                    (BUFFER_SIZE - total).convert()
                )
                if (r <= 0) break
                total += r.toInt()
            }

            if (total != BUFFER_SIZE) continue

            val r = libusb_bulk_transfer(
                handle,
                AOA_ENDPOINT_OUT,
                buffer,
                BUFFER_SIZE,
                transferred.ptr,
                0.toUint()
            )

            if (r != 0) {
                fprintf(stderr, "USB error: %s\n", libusb_error_name(r))
                break
            }
        }

        libusb_release_interface(handle, AOA_INTERFACE)
    }


    private fun MemScope.readString(handle: CPointer<libusb_device_handle>?, index: UByte): String {
        if (handle == null || index == 0.toUByte()) return "Unknown"
        val buffer = allocArray<UByteVar>(256)
        val r = libusb_get_string_descriptor_ascii(handle, index, buffer, 256)
        return if (r > 0) buffer.reinterpret<ByteVar>().toKString() else "Unknown"
    }
    private fun Int.toUint() = this.toUInt()
    private fun Int.toUlong() = this.toULong()
}

