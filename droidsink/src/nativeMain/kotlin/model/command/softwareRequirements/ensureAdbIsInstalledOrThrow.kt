package model.command.softwareRequirements

import model.command.exceptions.RequiredSoftwareNotFoundException
import model.command.exec

internal fun ensureAdbIsInstalledOrThrow() {
    val adbBinaryLocation = exec("which adb").trim()
    if (adbBinaryLocation.isEmpty()) {
        throw RequiredSoftwareNotFoundException("adb")
    } else {
        println("Found adb at: $adbBinaryLocation")
    }
}