package model.command.exceptions

data class RequiredSoftwareNotFoundException(
    val softwareName: String,
    override val message: String = "Required software $softwareName not found."
) : Exception(message)