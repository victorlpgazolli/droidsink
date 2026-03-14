package model.usb

internal data class UsbPhysicalId(
    val bus: Int,
    val portPath: List<Int>
)