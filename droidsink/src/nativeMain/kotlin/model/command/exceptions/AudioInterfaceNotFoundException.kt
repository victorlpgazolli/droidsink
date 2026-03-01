package model.command.exceptions

data class AudioInterfaceNotFoundException(
    val audioInterfaceName: String,
    val partialMatches: List<String> = emptyList(),
    override val message: String = "he specified audio interface ($audioInterfaceName) was not found on the system."
) : Exception(message)