package model.peripheral

internal data class Peripheral(
    val name: String,
    val serialNumber: String,
    val vendorId: VendorId,
    val productId: ProductId,
)
