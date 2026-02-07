package model.usb

data class UsbPhysicalId(
    val bus: Int,
    val portPath: List<Int>
)