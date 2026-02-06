@file:OptIn(ExperimentalForeignApi::class)

import kotlinx.cinterop.*
import platform.posix.*
import kotlin.time.Duration.Companion.seconds

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
    println("  version         - Print the current version of the app.")
    println("Available parameters:")
    println("  --skip-app-install - Skip the app installation step when running 'run' or 'start' commands.")
}

fun CommandSession.stop() {
    println("Stopping the service...")
    exec("adb shell am force-stop dev.victorlpgazolli.mobilesink")
}
fun Peripheral?.installApp() {
    val configPath = "~/.config/droidsink"

    exec("mkdir -p $configPath")

    val apkPath = "$configPath/$APP_VERSION.apk"

    val fileExists = exec("test -f $apkPath && echo exists").trim() == "exists"
    if(fileExists.not()) {
        println("Downloading APK from $APP_URL to $apkPath")
        exec("wget $APP_URL -O $apkPath --show-progress --progress=bar:force:noscroll")
    }

    println("Installing the app...")

    val customOption = this?.serialNumber?.let { serial ->
        "-s $serial"
    } ?: ""

    exec("adb $customOption install -r -t $apkPath", suppressLogs = this != null)
}

fun isAppInstalled(): Boolean {
    val output = exec("adb shell pm list packages dev.victorlpgazolli.mobilesink")
    return output.contains("dev.victorlpgazolli.mobilesink")
}

fun Peripheral.getUsbConfigType(): List<String> {
    val configurations = exec("adb -s $serialNumber shell getprop sys.usb.config")
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
fun CommandSession.startService() {
    println("Starting foreground service on device...")
    exec("adb shell am start-foreground-service -n dev.victorlpgazolli.mobilesink/.AccessoryService")
}
data class RequiredSoftwareNotFoundException(
    val softwareName: String,
    override val message: String = "Required software: $softwareName not found."
) : Exception(message)

fun ensureSoxIsInstalledOrThrow() {
    val soxBinaryLocation = exec("which sox").trim()
    if (soxBinaryLocation.isEmpty()) {
        throw RequiredSoftwareNotFoundException("sox")
    } else {
        println("Found sox at: $soxBinaryLocation")
    }
}

fun ensureBlackholeIsInstalledOrThrow() {
    val out = exec("system_profiler SPAudioDataType")
    val isBlackHoleInstalled = out.contains("BlackHole 2ch")
    if (isBlackHoleInstalled.not()) {
        throw RequiredSoftwareNotFoundException("blackhole-2ch")
    } else {
        println("BlackHole virtual audio device is installed.")
    }
}

private fun UsbSession.deviceInAccessoryModeOrNull(): Peripheral? {
    val selectedPeripherals = listAccessories().filter { it.hasAdb() }


    if (selectedPeripherals.isEmpty()) {
        println("No connected accessories with ADB found.")
        return null
    }
    val selectedPeripheral = selectedPeripherals.firstOrNull() ?: run {
        println("No connected accessories with ADB found.")
        return null
    }
    if (selectedPeripherals.size > 1) {
        println("Multiple connected accessories with ADB found. Selecting the first one: ${selectedPeripheral.name}")
    }
    println("Selected Peripheral: ${selectedPeripheral.name} (VID: ${selectedPeripheral.vendorId}, PID: ${selectedPeripheral.productId}, Serial: ${selectedPeripheral.serialNumber}), configType: ${selectedPeripheral.getUsbConfigType()}")
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

fun CommandSession.installAccessoryAppOrThrow(peripheral: Peripheral? = null) {
    if(isAppInstalled()) {
        println("Accessory app is already installed.")
        return
    }
    println("Installing accessory app...")
    peripheral.installApp()
    sleep(2.seconds.inWholeSeconds.toUInt())
    val installed = isAppInstalled()
    if(!installed) {
        throw IllegalStateException("Failed to install the accessory app.")
    }
}


fun CommandSession.run(type: StreamingType = StreamingType.HostToClient) {

    val usb = UsbInteropImpl()
    usb.runSession {
        val peripheral = deviceInAccessoryModeOrNull()
            ?: throw IllegalStateException("No device in Accessory Mode available.")

        if (hasSkipAppInstallParameter) {
            println("Skipping app installation")
        } else {
            installAccessoryAppOrThrow(peripheral)
        }
        waitUntilAccessoryReady()

        startService()
        startStreamingFromPeripheral(peripheral, type)
    }
}

data class CommandSession(
    val hasSkipAppInstallParameter: Boolean
)

fun main(args: Array<String>) {
    ensureSoxIsInstalledOrThrow()
    ensureBlackholeIsInstalledOrThrow()

    if(args.isEmpty()) {
        help()
        return
    }
    val (command) = args
    val hasSkipAppInstallParameter = args.contains("--skip-app-install")
    val session = CommandSession(
        hasSkipAppInstallParameter = hasSkipAppInstallParameter
    )

    when(command) {
        "install" -> session.installAccessoryAppOrThrow()
        "start" -> session.startService()
        "version" -> println(APP_VERSION)
        "stop" -> session.stop()
        "run" -> session.run()
        "run:as:speaker" -> session.run()
        "run:as:microphone" -> session.run(StreamingType.ClientToHost)
        "internal:list" -> UsbInteropImpl().runSession { listAccessories()?.let { println(it) } }
        else -> println("Unknown command: $command").also { help() }
    }
}
