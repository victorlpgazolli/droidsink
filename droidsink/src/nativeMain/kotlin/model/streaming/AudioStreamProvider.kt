@file:OptIn(ExperimentalForeignApi::class)

package model.streaming

import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import platform.posix.FILE

internal interface AudioStreamProvider {
    operator fun invoke(audioInterfaceName: String? = null): CPointer<FILE>
}

internal fun AudioStreamProvider(start: (audioInterfaceName: String?) -> CPointer<FILE>): AudioStreamProvider = object : AudioStreamProvider {
    override fun invoke(audioInterfaceName: String?): CPointer<FILE> = start(audioInterfaceName)
}