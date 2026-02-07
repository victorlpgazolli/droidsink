package model.peripheral

value class VendorId(val id: String)


fun UShort.toVendorId(): VendorId = VendorId(this.toString())
fun VendorId.toUShort(): UShort = id.toUShort()