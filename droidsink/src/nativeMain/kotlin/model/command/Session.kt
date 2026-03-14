package model.command

public interface Session {
    val hasSkipAppInstallParameter: Boolean
    val audioInterfaceName: String
    val runAsMicrophoneMode: Boolean
    val useFakeAudioInput: Boolean
}
