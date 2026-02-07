package model.streaming

sealed class StreamingType {
    object ClientToHost : StreamingType() // microphone data from Android to PC
    object HostToClient : StreamingType() // audio playback data from PC to Android
}