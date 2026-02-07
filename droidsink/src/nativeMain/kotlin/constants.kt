


internal const val ACCESSORY_PID: UShort = 0x2D00u
internal const val ACCESSORY_ADB_PID: UShort = 0x2D01u

internal const val AMAZON_VID = 0x1949
internal const val ECHO_PID = 0x2048
internal const val SAMSUNG_VID = 0x04E8
internal const val XIAOMI_VID = 0x2717
internal const val GOOGLE_VID = 0x18D1
internal val AOA_PIDS = setOf(
    0x2D00, // accessory
    0x2D01, // accessory + adb
    0x2D02, // audio
    0x2D03, // audio + adb
    0x2D04, // accessory + audio
    0x2D05  // accessory + audio + adb
)

internal const val AUDIO_READ_BUFFER_SIZE = AUDIO_READ_BUFFER_CAPACITY_FACTOR * 128
internal const val AOA_INTERFACE = 0
internal const val AOA_ENDPOINT_OUT: UByte = 0x01u
internal const val AOA_ENDPOINT_IN: UByte = 0x81u
internal const val AUDIO_DEVICE_NAME = "BlackHole 2ch"