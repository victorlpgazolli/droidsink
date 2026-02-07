package extensions

import model.UsbSession
import model.peripheral.Peripheral

internal fun UsbSession.findPeripheralBySerialNumber(
    serialNumber: String
): Peripheral? {
    val accessories = listAccessories()
    return accessories.firstOrNull { it.serialNumber == serialNumber }
}