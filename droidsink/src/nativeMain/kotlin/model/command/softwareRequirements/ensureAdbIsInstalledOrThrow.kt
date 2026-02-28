package model.command.softwareRequirements

import exec
import model.command.exceptions.RequiredSoftwareNotFoundException

internal fun ensureAdbIsInstalledOrThrow() {
    val adbBinaryLocation = exec("which adb").trim()
    if (adbBinaryLocation.isEmpty()) {
        throw RequiredSoftwareNotFoundException("adb")
    } else {
        println("Found adb at: $adbBinaryLocation")
    }
}