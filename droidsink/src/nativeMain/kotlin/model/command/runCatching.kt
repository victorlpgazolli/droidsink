package model.command

import model.command.exceptions.AppNotInstalledException
import model.command.exceptions.AudioInterfaceNotFoundException
import model.command.exceptions.InvalidCommandException
import model.command.exceptions.NoDeviceWithAdbFoundException
import model.command.exceptions.RequiredSoftwareNotFoundException

inline fun runCatching(block: () -> Unit) {
    try {
        block()
    } catch (_: InvalidCommandException) {
        printHelp()
    } catch (error: RequiredSoftwareNotFoundException) {
        println(error.message)
        println("Please install ${error.softwareName} and try again.")
    } catch (error: AppNotInstalledException) {
        println(error.message)
        error.cause?.let { println("Original error: $it") }
    } catch (error: NoDeviceWithAdbFoundException) {
        println(error.message)
        println("Please connect a compatible device and try again.")
    } catch (error: AudioInterfaceNotFoundException) {
        println(error.message)
        println("Please check if the specified audio interface name is correct or that the device is properly connected.")
    } catch (error: Exception) {
        println("An unexpected error occurred: ${error.message}")
    }
}