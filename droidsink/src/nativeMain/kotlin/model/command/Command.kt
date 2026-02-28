package model.command

import DEFAULT_AUDIO_DEVICE_NAME
import model.command.softwareRequirements.Requirement

val allCommands: List<Command> = listOf(
    PrintableCommand.Install(),
    PrintableCommand.Start(),
    PrintableCommand.Stop(),
    PrintableCommand.Run(),
    PrintableCommand.Devices(),
    PrintableCommand.Purge(),
    PrintableCommand.Version()
)

public sealed class Parameter(
    val name: String,
    val description: String
) {
    object SkipAppInstall : Parameter(
        name = "--skip-app-install",
        description = "Skip the installation of the .apk on the connected device (default: false)"
    )
    object AudioInterface : Parameter(
        name = "--audio-interface",
        description = "Specify the name of the audio interface to use for streaming (default: $DEFAULT_AUDIO_DEVICE_NAME)"
    )

    object MicrophoneMode : Parameter(
        name = "--run-as-microphone",
        description = "Run the application in microphone mode, which configures the device to provide audio data as if it were a microphone peripheral (default: false)"
    )

}

interface Command {
    val name: String
    val description: String
    val parameters: List<Parameter>
    val requirements: List<Requirement>
}

sealed class PrintableCommand: Command {
    data class Install(
        override val name: String = "install",
        override val description: String = "Install the accessory app on the connected device.",
        override val parameters: List<Parameter> = emptyList(),
        override val requirements: List<Requirement> = listOf(Requirement.Adb, Requirement.Wget)
    ): PrintableCommand()

    data class Start(
        override val name: String = "start",
        override val description: String = "Start the accessory service on the connected device.",
        override val parameters: List<Parameter> = listOf(Parameter.SkipAppInstall),
        override val requirements: List<Requirement> = listOf(Requirement.Adb, Requirement.Wget)
    ): PrintableCommand()

    data class Stop(
        override val name: String = "stop",
        override val description: String = "Stop the accessory service on the connected device.",
        override val parameters: List<Parameter> = listOf(Parameter.SkipAppInstall),
        override val requirements: List<Requirement> = listOf(Requirement.Adb, Requirement.Wget)
    ): PrintableCommand()

    data class Run(
        override val name: String = "run",
        override val description: String = "Install the app, start the service, and begin streaming data.",
        override val parameters: List<Parameter> = listOf(Parameter.SkipAppInstall, Parameter.AudioInterface, Parameter.MicrophoneMode),
        override val requirements: List<Requirement> = listOf(Requirement.Adb, Requirement.AudioDevice, Requirement.Sox, Requirement.Wget)
    ): PrintableCommand()

    data class Devices(
        override val name: String = "devices",
        override val description: String = "List all connected devices and their statuses.",
        override val parameters: List<Parameter> = emptyList(),
        override val requirements: List<Requirement> = listOf(Requirement.Adb)
    ): PrintableCommand()

    data class Purge(
        override val name: String = "purge",
        override val description: String = "Uninstall the app from the connected device, clear its data, and remove the downloaded APK from local storage.",
        override val parameters: List<Parameter> = emptyList(),
        override val requirements: List<Requirement> = listOf(Requirement.Adb)
    ): PrintableCommand()

    data class Version(
        override val name: String = "version",
        override val description: String = "Print the current version of this application.",
        override val parameters: List<Parameter> = emptyList(),
        override val requirements: List<Requirement> = emptyList()
    ): PrintableCommand()

    companion object {
        fun from(commandName: String): PrintableCommand = when (commandName.lowercase()) {
            Install::class.simpleName?.lowercase() -> Install()
            Start::class.simpleName?.lowercase() -> Start()
            Stop::class.simpleName?.lowercase() -> Stop()
            Run::class.simpleName?.lowercase() -> Run()
            Devices::class.simpleName?.lowercase() -> Devices()
            Purge::class.simpleName?.lowercase() -> Purge()
            Version::class.simpleName?.lowercase() -> Version()
            else -> throw IllegalArgumentException("Unknown command: $commandName")
        }
    }
}
