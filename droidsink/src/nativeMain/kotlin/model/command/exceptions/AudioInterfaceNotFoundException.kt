package model.command.exceptions

data class AudioInterfaceNotFoundException(
    val audioInterfaceName: String,
    override val message: String = "The specified audio interface ($audioInterfaceName) was not found on the system."
) : Exception(message)