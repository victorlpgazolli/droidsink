package model.peripheral

internal value class ProductId(
    val id: String,
)

internal fun UShort.toProductId(): ProductId = ProductId(this.toString())

internal fun ProductId.toUShort(): UShort = id.toUShort()
