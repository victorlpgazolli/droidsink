@file:OptIn(ExperimentalForeignApi::class)

import kotlinx.cinterop.*
import platform.posix.*
import kotlin.time.Duration.Companion.seconds

const val APK_PATH = "/tmp/droidsink.apk"
@OptIn(ExperimentalForeignApi::class)
fun exec(cmd: String): String = memScoped {
    val silentCmd = "$cmd 2>/dev/null"
    val pipe = popen(silentCmd, "r") ?: error("popen failed")

    val buffer = ByteArray(4096)
    val output = StringBuilder()

    while (true) {
        val read = fgets(buffer.refTo(0), buffer.size, pipe) ?: break
        output.append(read.toKString())
    }

    pclose(pipe)
    output.toString()
}

fun greet() {
    println("Hello from Kotlin/Native!")
}

fun help(){
    println("Available commands:")
    println("  install         - Install the accessory app on the connected device.")
    println("  start           - Start the accessory service on the connected device.")
    println("  stop            - Stop the accessory service on the connected device.")
    println("  run             - Install the app, start the service, and begin streaming data.")
    println("  internal:list   - List connected USB accessories.")
}

fun stop() {
    println("Stopping the service...")
    exec("adb shell am force-stop dev.victorlpgazolli.mobilesink")
}
fun install() {
    println("Installing the app...")
    exec("adb install -r -t $APK_PATH")
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
fun startService() {
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
    val selectedPeripheral = listAccessories().firstOrNull { it.hasAdb() } ?: run {
        println("No connected accessories with ADB found.")
        return null
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

fun installAccessoryAppOrThrow() {
    if(isAppInstalled()) {
        println("Accessory app is already installed.")
        return
    }
    println("Installing accessory app...")
    install()
    sleep(2.seconds.inWholeSeconds.toUInt())
    val installed = isAppInstalled()
    if(!installed) {
        throw IllegalStateException("Failed to install the accessory app.")
    }
}


fun run() {
    installAccessoryAppOrThrow()
    val usb = UsbInteropImpl()
    usb.runSession {
        val peripheral = deviceInAccessoryModeOrNull()
            ?: throw IllegalStateException("No device in Accessory Mode available.")
        waitUntilAccessoryReady()

        startService()
        startStreamingFromPeripheral(peripheral)
    }
}

fun main(args: Array<String>) {
    ensureSoxIsInstalledOrThrow()
    ensureBlackholeIsInstalledOrThrow()

    if(args.isEmpty()) {
        help()
        return
    }
    val (command) = args

    when(command) {
        "install" -> installAccessoryAppOrThrow()
        "start" -> startService()
        "stop" -> stop()
        "run" -> run()
        "internal:list" -> UsbInteropImpl().runSession { listAccessories()?.let { println(it) } }
        else -> println("Unknown command: $command").also { help() }
    }
}
