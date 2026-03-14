package model.command.softwareRequirements

import model.command.exceptions.RequiredSoftwareNotFoundException
import model.command.exec

internal fun ensureSoxIsInstalledOrThrow() {
    val soxBinaryLocation = exec("which sox").trim()
    if (soxBinaryLocation.isEmpty()) {
        throw RequiredSoftwareNotFoundException("sox")
    } else {
        println("Found sox at: $soxBinaryLocation")
    }
}