package model.command.exceptions

data class NoDeviceWithAdbFoundException(
    val selectedSerialNumber: String? = null,
    override val message: String =
        if (selectedSerialNumber != null) {
            "No connected accessory with ADB found with serial number: $selectedSerialNumber"
        } else {
            "No connected accessories with ADB found."
        },
) : Exception(message)
