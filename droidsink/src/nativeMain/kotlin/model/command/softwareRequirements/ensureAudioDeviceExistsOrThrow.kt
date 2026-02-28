package model.command.softwareRequirements

import exec
import model.command.Session
import model.command.exceptions.AudioInterfaceNotFoundException

internal fun Session.ensureAudioDeviceExistsOrThrow() {
    val out = exec("system_profiler SPAudioDataType")
    val hasAudioDevice = out.contains(audioInterfaceName)
    if (hasAudioDevice.not()) {
        throw AudioInterfaceNotFoundException(audioInterfaceName)
    } else {
        println("$audioInterfaceName audio interface found on the system.")
    }
}