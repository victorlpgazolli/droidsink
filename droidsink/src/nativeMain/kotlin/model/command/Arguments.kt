package model.command

import DEFAULT_AUDIO_DEVICE_NAME
import model.command.exceptions.InvalidCommandException

fun Array<String>.toSessionOrThrow(): Session {
    if(isEmpty()) {
        throw InvalidCommandException
    }
    val hasSkipAppInstallParameter = contains(Parameter.SkipAppInstall.name)
    val runAsMicrophoneMode = contains(Parameter.MicrophoneMode.name)
    val hasCustomAudioDevice = contains(Parameter.AudioInterface.name)

    val audioInterfaceName = if (hasCustomAudioDevice) {
        val index = indexOf(Parameter.AudioInterface.name)
        println(this.joinToString(" "))

        drop(index + 1).joinToString(" ").split("\"").first()
    } else {
        DEFAULT_AUDIO_DEVICE_NAME
    }

    return object : Session {
        override val hasSkipAppInstallParameter: Boolean = hasSkipAppInstallParameter
        override val audioInterfaceName: String = audioInterfaceName
        override val runAsMicrophoneMode: Boolean = runAsMicrophoneMode
    }
}

fun Array<String>.getCommandOrThrow(): Command {
    if(isEmpty()) {
        throw InvalidCommandException
    }
    return PrintableCommand.from(first())
}
