package model.command.softwareRequirements

import exec
import model.command.Session
import model.command.Parameter
import model.command.exceptions.RequiredSoftwareNotFoundException

internal fun Session.ensureWgetIsInstalledWhenRequiredOrThrow(): Unit {
    val wgetIsRequired = hasSkipAppInstallParameter.not()
    if(wgetIsRequired.not()) {
        println("Skipping wget check since ${Parameter.SkipAppInstall.name} parameter is present.")
        return
    }

    val wgetPath = exec("which wget").trim()
    val isWgetInstalled = wgetPath.isNotEmpty()
    if (isWgetInstalled) {
        println("Found wget at: $wgetPath")
    } else {
        throw RequiredSoftwareNotFoundException("wget")
    }
}