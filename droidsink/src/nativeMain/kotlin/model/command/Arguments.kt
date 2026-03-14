package model.command

import DEFAULT_AUDIO_DEVICE_NAME
import model.command.exceptions.InvalidCommandException

internal fun Array<String>.toSessionOrThrow(): Session {
    if (isEmpty()) {
        throw InvalidCommandException
    }
    val hasSkipAppInstallParameter = contains(Parameter.SkipAppInstall.name)
    val runAsMicrophoneMode = contains(Parameter.MicrophoneMode.name)
    val hasCustomAudioDevice = contains(Parameter.AudioInterface.name)
    val useFakeAudioInput = contains(Parameter.UseFakeAudioInput.name)
    val hasSelectedSerialNumber = contains(Parameter.UseSpecificSerialNumber.name)

    val selectedSerialNumber =
        if (hasSelectedSerialNumber) {
            getContentFromParameter(Parameter.UseSpecificSerialNumber)
        } else {
            null
        }

    val audioInterfaceName =
        if (hasCustomAudioDevice) {
            getContentFromParameter(Parameter.AudioInterface)
        } else {
            DEFAULT_AUDIO_DEVICE_NAME
        }

    return object : Session {
        override val hasSkipAppInstallParameter: Boolean = hasSkipAppInstallParameter
        override val audioInterfaceName: String = audioInterfaceName
        override val runAsMicrophoneMode: Boolean = runAsMicrophoneMode
        override val useFakeAudioInput: Boolean = useFakeAudioInput
        override val selectedSerialNumber: String? = selectedSerialNumber
    }
}

internal fun Array<String>.getCommandOrThrow(): Command {
    if (isEmpty()) {
        throw InvalidCommandException
    }
    return PrintableCommand.from(first())
}

private fun Array<String>.getContentFromParameter(parameter: Parameter): String {
    val index = indexOf(parameter.name)
    return get(index + 1)
}
