package model

internal interface UsbInterop {
    fun <T> runSession(block: UsbSession.() -> T): T
}