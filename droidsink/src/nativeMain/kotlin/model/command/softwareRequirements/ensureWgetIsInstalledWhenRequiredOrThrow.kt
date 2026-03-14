package model.command.softwareRequirements

import model.command.Parameter
import model.command.Session
import model.command.exceptions.RequiredSoftwareNotFoundException
import model.command.exec

internal fun Session.ensureWgetIsInstalledWhenRequiredOrThrow() {
    val wgetIsRequired = hasSkipAppInstallParameter.not()
    if (wgetIsRequired.not()) {
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
