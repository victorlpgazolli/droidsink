package model.command

interface Session {
    val hasSkipAppInstallParameter: Boolean
    val audioInterfaceName: String
    val runAsMicrophoneMode: Boolean
}
