package model.command.exceptions

data class NoDeviceWithAdbFoundException(
    override val message: String = "No connected accessories with ADB found."
) : Exception(message)