package extensions

import model.command.exec
import model.peripheral.Peripheral

internal fun Peripheral?.adb(parameters: String): String {
    val customOption = this?.serialNumber.let { serial ->
        "-s $serial"
    }

    return exec("adb $customOption $parameters", suppressLogs = this != null)
}
