package model.command.exceptions

data class AppNotInstalledException(
    val bundleId: String,
    override val message: String,
    override val cause: Throwable?
) : Exception()