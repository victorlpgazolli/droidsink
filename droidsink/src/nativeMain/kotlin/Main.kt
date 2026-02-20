@file:OptIn(ExperimentalForeignApi::class)

import kotlinx.cinterop.*
import model.UsbSession
import platform.posix.*
import kotlin.time.Duration.Companion.seconds
import model.peripheral.*
import model.streaming.*

@OptIn(ExperimentalForeignApi::class)
fun exec(cmd: String, suppressLogs: Boolean = true): String = memScoped {
    val silentCmd = "$cmd 2>/dev/null"
    val pipe = popen(
        if(suppressLogs) silentCmd else cmd,
        "r"
    ) ?: error("popen failed")

    val buffer = ByteArray(4096)
    val output = StringBuilder()

    while (true) {
        val read = fgets(buffer.refTo(0), buffer.size, pipe) ?: break
        output.append(read.toKString())
    }

    pclose(pipe)
    output.toString()
}

fun help(){
    println("Available commands:")
    println("  install         - Install the accessory app on the connected device.")
    println("  start           - Start the accessory service on the connected device.")
    println("  stop            - Stop the accessory service on the connected device.")
    println("  run             - (alias to run:as:speaker) Install the app, start the service, and begin streaming data.")
    println("  run:as:speaker  - Run the service streaming audio from PC to Android")
    println("  run:as:microphone - Run the service streaming audio from Android to PC.")
    println("  internal:list   - List connected USB accessories.")
    println("  internal:purge  - Uninstall the app from the connected device, clear its data, and remove the downloaded APK from local storage.")
    println("  version         - Print the current version of the app.")
    println("Available parameters:")
    println("  --skip-app-install - Skip the app installation step when running 'run' or 'start' commands.")
}

fun Peripheral.stop() {
    println("Stopping the service...")
    adb("adb shell am force-stop $BUNDLE_ID")
}
private fun Peripheral?.adb(parameters: String): String {
    val customOption = this?.serialNumber.let { serial ->
        "-s $serial"
    }

    return exec("adb $customOption $parameters", suppressLogs = this != null)
}
data class AppNotInstalledException(
    val bundleId: String,
    override val message: String,
    override val cause: Throwable?
) : Exception()

fun Peripheral?.installAppOrThrow() {
    val configPath = "~/.config/droidsink"

    exec("mkdir -p $configPath")

    val apkPath = "$configPath/$APP_VERSION.apk"

    val fileExists = exec("test -f $apkPath && echo exists").trim() == "exists"
    if(fileExists.not()) {
        println("Downloading APK from $APP_URL to $apkPath")
        exec("wget $APP_URL -O $apkPath --show-progress --progress=bar:force:noscroll")
    }

    println("Installing the app...")

    adb("install -r -g -t $apkPath")
}

fun Peripheral.isAppInstalled(): Boolean {
    val output = adb("shell pm list packages $BUNDLE_ID")
    return output.contains(BUNDLE_ID)
}

fun printAllPeripherals() {
    runSession {
        listAccessories().forEachIndexed { index, peripheral ->
            val hasAdb = peripheral.hasAdb()
            val isFirst = index == 0
            val selectedPrefix = if (isFirst) "(auto-selected)" else index
            println("Peripheral ${selectedPrefix}: ${peripheral.name}, Serial: ${peripheral.serialNumber}, Config: ${peripheral.getUsbConfigType()}, Has ADB: $hasAdb")
        }
    }
}

fun Peripheral.getUsbConfigType(): List<String> {
    val configurations = adb("shell getprop sys.usb.config")
        .split(",")
        .map { config -> config.trim() }
    return configurations
}

fun Peripheral.hasAdb(): Boolean {
    val hasAnySerialNumber = serialNumber != "Unknown"
    if(!hasAnySerialNumber) return false

    val hasAdb = getUsbConfigType()
        .firstOrNull { config -> config == "adb" }

    return hasAdb != null
}
fun UsbSession.startService() {
    val peripheral = getFirstPeripheralWithAdbOrThrow()
    peripheral.startService()
}
fun Peripheral.startService() {
    if(isAppInstalled().not()) {
        throw AppNotInstalledException(
            bundleId = BUNDLE_ID,
            message = "Cannot start service because the app is not installed on the device.",
            cause = null
        )
    }
    println("Starting foreground service on device...")
    adb("shell am start-foreground-service -n $BUNDLE_ID/.AccessoryService")
}
data class RequiredSoftwareNotFoundException(
    val softwareName: String,
    override val message: String = "Required software $softwareName not found."
) : Exception(message)


fun ensureSoxIsInstalledOrThrow() {
    val soxBinaryLocation = exec("which sox").trim()
    if (soxBinaryLocation.isEmpty()) {
        throw RequiredSoftwareNotFoundException("sox")
    } else {
        println("Found sox at: $soxBinaryLocation")
    }
}

fun ensureAdbIsInstalledOrThrow() {
    val adbBinaryLocation = exec("which adb").trim()
    if (adbBinaryLocation.isEmpty()) {
        throw RequiredSoftwareNotFoundException("adb")
    } else {
        println("Found adb at: $adbBinaryLocation")
    }
}

fun ensureBlackholeIsInstalledOrThrow() {
    val out = exec("system_profiler SPAudioDataType")
    val isBlackHoleInstalled = out.lowercase().contains("blackhole 2ch")
    if (isBlackHoleInstalled.not()) {
        throw RequiredSoftwareNotFoundException("blackhole-2ch")
    } else {
        println("BlackHole virtual audio device is installed.")
    }
}

fun CommandSession.ensureWgetIsInstalledWhenRequiredOrThrow(): Unit {
    val wgetIsRequired = hasSkipAppInstallParameter.not()
    if(wgetIsRequired.not()) {
        println("Skipping wget check since --skip-app-install parameter is present.")
        return
    }

    val wgetPath = exec("which wget")
    val isWgetInstalled = wgetPath.trim().isNotEmpty()
    if (isWgetInstalled) {
        println("Found wget at: $wgetPath")
    } else {
        throw RequiredSoftwareNotFoundException("wget")
    }
}

data class NoDeviceWithAdbFoundException(
    override val message: String = "No connected accessories with ADB found."
) : Exception(message)

fun <T> runSession(block: UsbSession.() -> T): T {
    val usb = UsbInteropImpl()
    return usb.runSession(block)
}
fun getFirstPeripheralWithAdbOrThrow(): Peripheral {
    return runSession { getFirstPeripheralWithAdbOrThrow() }
}
fun UsbSession.getFirstPeripheralWithAdbOrThrow(): Peripheral {
    val selectedPeripherals = listAccessories().filter { it.hasAdb() }

    if (selectedPeripherals.isEmpty()) {
        throw NoDeviceWithAdbFoundException()
    }
    val selectedPeripheral = selectedPeripherals.firstOrNull() ?: run {
        throw NoDeviceWithAdbFoundException()
    }
    if (selectedPeripherals.size > 1) {
        println("Multiple connected accessories with ADB found. Selecting the first one: ${selectedPeripheral.name}")
    }
    return selectedPeripheral.also {
        println("Selected Peripheral: ${it.name} (VID: ${it.vendorId}, PID: ${it.productId}, Serial: ${it.serialNumber}), configType: ${it.getUsbConfigType()}")
    }
}

private fun UsbSession.deviceInAccessoryModeOrNull(): Peripheral? {
    val selectedPeripheral = getFirstPeripheralWithAdbOrThrow()
    val configType = selectedPeripheral.getUsbConfigType()
    if(configType.contains("accessory")) {
        println("Device is already in Accessory Mode.")
        return selectedPeripheral
    }
    setupAccessoryMode(selectedPeripheral)
    sleep(1.seconds.inWholeSeconds.toUInt())
    selectedPeripheral.getUsbConfigType().let {
        if(it.contains("accessory")) {
            println("Device successfully switched to Accessory Mode.")
            return selectedPeripheral
        } else {
            println("Failed to switch device to Accessory Mode.")
            return null
        }
    }
}

fun Peripheral.installAccessoryAppOrThrow() {
    if(isAppInstalled()) {
        println("Accessory app is already installed.")
        return
    }
    println("Installing accessory app...")
    try {
        installAppOrThrow()
        sleep(2.seconds.inWholeSeconds.toUInt())
        val installed = isAppInstalled()
        if (installed.not()) {
            throw AppNotInstalledException(
                bundleId = BUNDLE_ID,
                message = "App installation command executed but the app is not detected as installed afterwards.",
                cause = null
            )
        }
    } catch (error: AppNotInstalledException) {
        throw error
    } catch (cause: Exception) {
        throw AppNotInstalledException(
            bundleId = BUNDLE_ID,
            message = "Failed to install the accessory app",
            cause = cause
        )
    }
}

fun Peripheral.purge() {
    println("Purging app data and uninstalling the app from the device...")
    try {
        adb("shell pm clear $BUNDLE_ID")
        println("Cleared app data for $BUNDLE_ID on the device.")

        adb("uninstall $BUNDLE_ID")
        println("Uninstalled $BUNDLE_ID from the device.")
    } catch (error: Exception) {
        println("Error during device data purge: ${error.message}")
    }

    println("Attempting to remove the downloaded APK from local storage...")
    try {
        exec("rm -f ~/.config/droidsink/$APP_VERSION.apk")
        println("Removed downloaded APK from local storage.")
    } catch (error: Exception) {
        println("Error during APK file removal: ${error.message}")
    }
}


fun CommandSession.run(type: StreamingType = StreamingType.HostToClient) {
    runSession {
        val peripheral = deviceInAccessoryModeOrNull()
            ?: throw IllegalStateException("No device in Accessory Mode available.")

        if (hasSkipAppInstallParameter) {
            println("Skipping app installation")
        } else {
            peripheral.installAccessoryAppOrThrow()
        }
        waitUntilAccessoryReady()

        peripheral.startService()
        startStreamingFromPeripheral(peripheral, type)
    }
}

data class CommandSession(
    val hasSkipAppInstallParameter: Boolean
)

fun main(args: Array<String>) {
    try {

        ensureSoxIsInstalledOrThrow()
        ensureBlackholeIsInstalledOrThrow()
        ensureAdbIsInstalledOrThrow()

        if(args.isEmpty()) {
            help()
            return
        }
        val (command) = args
        val hasSkipAppInstallParameter = args.contains("--skip-app-install")
        val session = CommandSession(
            hasSkipAppInstallParameter = hasSkipAppInstallParameter
        ).also {
            it.ensureWgetIsInstalledWhenRequiredOrThrow()
        }

        when(command) {
            "version" -> println(APP_VERSION)
            "install" -> getFirstPeripheralWithAdbOrThrow().installAccessoryAppOrThrow()
            "start" -> getFirstPeripheralWithAdbOrThrow().startService()
            "stop" -> getFirstPeripheralWithAdbOrThrow().stop()
            "run" -> session.run()
            "run:as:speaker" -> session.run()
            "run:as:microphone" -> session.run(StreamingType.ClientToHost)
            "internal:list" -> printAllPeripherals()
            "internal:purge" -> getFirstPeripheralWithAdbOrThrow().purge()
            else -> println("Unknown command: $command").also { help() }
        }
    } catch (error: RequiredSoftwareNotFoundException) {
        println(error.message)
        println("Please install ${error.softwareName} and try again.")
    } catch (error: AppNotInstalledException) {
        println(error.message)
        error.cause?.let { println("Original error: $it") }
    } catch (error: NoDeviceWithAdbFoundException) {
        println(error.message)
        println("Please connect a compatible device and try again.")
    } catch (error: Exception) {
        println("An unexpected error occurred: ${error.message}")
    }

}
