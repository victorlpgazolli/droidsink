package model

interface UsbInterop {
    fun runSession(block: UsbSession.() -> Unit)
}