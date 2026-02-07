package model.peripheral

import kotlin.text.toUShort

value class ProductId(val id: String)


fun UShort.toProductId(): ProductId = ProductId(this.toString())
fun ProductId.toUShort(): UShort = id.toUShort()