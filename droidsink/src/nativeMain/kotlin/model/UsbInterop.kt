package model

interface UsbInterop {
    fun <T> runSession(block: UsbSession.() -> T): T
}