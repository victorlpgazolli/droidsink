package dev.victorlpgazolli.mobilesink

import android.hardware.usb.UsbAccessory
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.flow.StateFlow


interface AudioSource {
    fun startRecording(fileDescriptor: ParcelFileDescriptor)
    fun stopRecording()
}