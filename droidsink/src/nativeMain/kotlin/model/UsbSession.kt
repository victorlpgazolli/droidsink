package model

import AOA_PIDS
import GOOGLE_VID
import model.peripheral.Peripheral
import model.streaming.StreamingType

interface UsbSession {
    fun listAccessories(): List<Peripheral>
    fun setupAccessoryMode(peripheral: Peripheral)
    fun getAccessoryInfo(peripheral: Peripheral): Peripheral?
    fun waitUntilAccessoryReady(
        expectedVendor: Int = GOOGLE_VID,
        expectedProducts: Set<Int> = AOA_PIDS,
        timeoutSeconds: Int = 30
    )
    fun startStreamingFromPeripheral(peripheral: Peripheral, type: StreamingType)
}