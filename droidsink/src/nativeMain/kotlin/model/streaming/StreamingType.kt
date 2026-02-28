package model.streaming

sealed class StreamingType(
    audioInterface: String
) {
    data class ClientToHost(
        val audioInterface: String
    ) : StreamingType(audioInterface) // microphone data from Android to PC
    data class HostToClient(
        val audioInterface: String
    ) : StreamingType(audioInterface) // audio playback data from PC to Android
}