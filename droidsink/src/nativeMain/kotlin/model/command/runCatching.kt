package model.command

import DEFAULT_AUDIO_DEVICE_NAME
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
        println("Please check if you have installed in your system, if the interface name is correct. \nSpecify the interface name in quotes if it contains spaces, example: --audio-interface \"$DEFAULT_AUDIO_DEVICE_NAME\"".trimMargin())
        if(error.partialMatches.isNotEmpty()) {
            println("Partial matches found:")
            error.partialMatches.forEach { println("   $it") }
        }
    } catch (error: Exception) {
        println("An unexpected error occurred: ${error.message}")
    }
}