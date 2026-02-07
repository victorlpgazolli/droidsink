import cnames.structs.libusb_context
import kotlinx.cinterop.CPointerVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import libusb.libusb_exit
import libusb.libusb_init
import model.UsbInterop
import model.UsbSession

class UsbInteropImpl: UsbInterop {

    @OptIn(ExperimentalForeignApi::class)
    override fun runSession(block: UsbSession.() -> Unit) = memScoped {
        val contextPointer = alloc<CPointerVar<libusb_context>>()
        if (libusb_init(contextPointer.ptr) != 0) error("Fatal error using LibUsb")
        val context = contextPointer.value!!

        try {
            val session = UsbSessionInternal(context)
            session.block()
        } finally {
            libusb_exit(context)
        }
    }
}