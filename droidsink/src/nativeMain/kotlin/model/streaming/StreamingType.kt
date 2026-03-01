package model.streaming

sealed class StreamingType(
    audioInterface: String,
    useFakeAudioInput: Boolean = false
) {
    data class ClientToHost(
        val audioInterface: String,
        val useFakeAudioInput: Boolean
    ) : StreamingType(audioInterface, useFakeAudioInput) // microphone data from Android to PC
    data class HostToClient(
        val audioInterface: String,
        val useFakeAudioInput: Boolean // whether to use a fake audio input stream that generates some audio data instead of reading from the host.
    ) : StreamingType(audioInterface, useFakeAudioInput) // audio playback data from PC to Android
}