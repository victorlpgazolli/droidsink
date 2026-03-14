package model.peripheral

internal value class VendorId(val id: String)


internal fun UShort.toVendorId(): VendorId = VendorId(this.toString())
internal fun VendorId.toUShort(): UShort = id.toUShort()