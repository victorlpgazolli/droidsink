package model.streaming

import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.UByteVar

@OptIn(ExperimentalForeignApi::class)
data class AsyncStreamContext(
    val id: String,
    val endpoint: UByte,
    val bufferSize: Int,
    val onTransferred: ((Pair<CPointer<UByteVar>, Int>) -> Unit)?,
    val shouldKeepInflatingBuffer: ((CPointer<UByteVar>) -> Boolean)?
)