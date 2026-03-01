@file:OptIn(ExperimentalForeignApi::class)

package model.streaming

import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import platform.posix.FILE

interface AudioStreamProvider {
    operator fun invoke(audioInterfaceName: String? = null): CPointer<FILE>
}

fun AudioStreamProvider(start: (audioInterfaceName: String?) -> CPointer<FILE>): AudioStreamProvider = object : AudioStreamProvider {
    override fun invoke(audioInterfaceName: String?): CPointer<FILE> = start(audioInterfaceName)
}