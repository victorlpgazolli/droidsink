@file:OptIn(ExperimentalForeignApi::class)

package model.streaming

import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import platform.posix.FILE

interface AudioStreamProvider {
    operator fun invoke(): CPointer<FILE>
}

fun AudioStreamProvider(start: () -> CPointer<FILE>): AudioStreamProvider = object : AudioStreamProvider {
    override fun invoke(): CPointer<FILE> = start()
}