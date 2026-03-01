package model.command.softwareRequirements

import model.command.Command
import model.command.Session

sealed class Requirement {
    object Adb : Requirement()
    object Sox : Requirement()
    object AudioDevice : Requirement()
    object Wget : Requirement()
}

fun Command.assertRequirements(session: Session) {
    requirements.forEach {
        when (it) {
            is Requirement.Adb -> ensureAdbIsInstalledOrThrow()
            is Requirement.Sox -> ensureSoxIsInstalledOrThrow()
            is Requirement.AudioDevice -> session.ensureAudioDeviceExistsOrThrow()
            is Requirement.Wget -> session.ensureWgetIsInstalledWhenRequiredOrThrow()
        }
    }
}