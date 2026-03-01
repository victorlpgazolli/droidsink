package model.command.softwareRequirements

import exec
import model.command.Session
import model.command.exceptions.AudioInterfaceNotFoundException

internal fun Session.ensureAudioDeviceExistsOrThrow() {
    val out = exec("system_profiler SPAudioDataType")
    val partialMatches = mutableListOf<String>()
    val hasAudioDevice = out.lines().any { line ->
        val currentLine = line.trim()
        val hasPartialMatch = currentLine.contains(audioInterfaceName, ignoreCase = true)
        if(hasPartialMatch) partialMatches.add(currentLine.replace(":", ""))

        currentLine == "$audioInterfaceName:"
    }

    if (hasAudioDevice.not()) {
        throw AudioInterfaceNotFoundException(
            audioInterfaceName = audioInterfaceName,
            partialMatches = partialMatches
        )
    } else {
        println("$audioInterfaceName audio interface found on the system.")
    }
}