@file:OptIn(ExperimentalForeignApi::class)

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.refTo
import kotlinx.cinterop.toKString
import model.UsbSession
import model.command.Parameter
import model.command.PrintableCommand
import model.command.Session
import model.command.exceptions.AppNotInstalledException
import model.command.exceptions.InvalidCommandException
import model.command.exceptions.NoDeviceWithAdbFoundException
import model.command.getCommandOrThrow
import model.command.runCatching
import model.command.softwareRequirements.assertRequirements
import model.command.toSessionOrThrow
import model.peripheral.Peripheral
import model.streaming.StreamingType
import platform.posix.fgets
import platform.posix.pclose
import platform.posix.popen
import platform.posix.sleep
import kotlin.time.Duration.Companion.seconds

fun main(args: Array<String>) = runCatching {
    val session = args.toSessionOrThrow()

    val command = args.getCommandOrThrow()

    command.assertRequirements(session)

    when (command) {
        is PrintableCommand.Version -> println(APP_VERSION)
        is PrintableCommand.Install -> session.installApp()
        is PrintableCommand.Start  -> getFirstPeripheralWithAdbOrThrow().startService()
        is PrintableCommand.Stop -> getFirstPeripheralWithAdbOrThrow().stop()
        is PrintableCommand.Run -> session.run()
        is PrintableCommand.Devices -> printAllPeripherals()
        is PrintableCommand.Purge -> getFirstPeripheralWithAdbOrThrow().purge()
        else -> throw InvalidCommandException
    }
}


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

fun Session.installApp() {
    if (hasSkipAppInstallParameter) {
        println("Skipping app installation as ${Parameter.SkipAppInstall.name} parameter is present.")
        return
    }
    getFirstPeripheralWithAdbOrThrow().installAccessoryAppOrThrow()
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


private fun Peripheral?.installAppOrThrow() {
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

private fun Peripheral.isAppInstalled(): Boolean {
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

private fun Peripheral.getUsbConfigType(): List<String> {
    val configurations = adb("shell getprop sys.usb.config")
        .split(",")
        .map { config -> config.trim() }
    return configurations
}

private fun Peripheral.hasAdb(): Boolean {
    val hasAnySerialNumber = serialNumber != "Unknown"
    if(!hasAnySerialNumber) return false

    val hasAdb = getUsbConfigType()
        .firstOrNull { config -> config == "adb" }

    return hasAdb != null
}

private fun Peripheral.startService() {
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


private fun <T> runSession(block: UsbSession.() -> T): T {
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

private fun Peripheral.installAccessoryAppOrThrow() {
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

private fun Peripheral.purge() {
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

private fun Session.run() {
    if (runAsMicrophoneMode) {
        run(type = StreamingType.ClientToHost(
            audioInterface = audioInterfaceName,
            useFakeAudioInput = useFakeAudioInput,
        ))
    } else {
        run(type = StreamingType.HostToClient(
            audioInterface = audioInterfaceName,
            useFakeAudioInput = useFakeAudioInput,
        ))
    }
}

private fun Session.run(type: StreamingType) {
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