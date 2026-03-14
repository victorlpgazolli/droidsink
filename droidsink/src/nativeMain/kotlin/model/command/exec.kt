package model.command

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.refTo
import kotlinx.cinterop.toKString
import platform.posix.fgets
import platform.posix.pclose
import platform.posix.popen

@OptIn(ExperimentalForeignApi::class)
internal fun exec(cmd: String, suppressLogs: Boolean = true): String = memScoped {
    val silentCmd = "$cmd 2>/dev/null"
    val pipe = popen(
        if(suppressLogs) silentCmd else cmd,
        "r"
    ) ?: error("popen failed")

    val buffer = ByteArray(4096)
    val output = StringBuilder()

    while (true) {
        val read = fgets(buffer.refTo(0), buffer.size, pipe) ?: break
        output.append(read.toKString())
    }

    pclose(pipe)
    output.toString()
}