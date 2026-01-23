package dev.victorlpgazolli.mobilesink

import android.app.Activity
import android.content.Intent
import android.hardware.usb.UsbAccessory
import android.hardware.usb.UsbManager
import android.os.Bundle
import android.util.Log

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val accessory = intent.getParcelableExtra<UsbAccessory>(UsbManager.EXTRA_ACCESSORY)

        if (accessory != null) {
            Log.d("AOA_Audio", "Activity iniciada pelo USB: ${accessory.model}")
            startBridgeService(accessory)
        } else {
            Log.d("AOA_Audio", "sem extra")
            val usbManager = getSystemService(USB_SERVICE) as UsbManager
            val currentAccessory = usbManager.accessoryList?.firstOrNull()
            if (currentAccessory != null) {
                Log.d("AOA_Audio", "Acessório USB encontrado: ${currentAccessory.model}")
                startBridgeService(currentAccessory)
            } else {
                Log.d("AOA_Audio", "Nenhum acessório USB conectado")
            }
        }

        finish()
    }

    private fun startBridgeService(accessory: UsbAccessory) {
        val serviceIntent = Intent(this, AccessoryService::class.java)
        serviceIntent.putExtra(UsbManager.EXTRA_ACCESSORY, accessory)

        startForegroundService(serviceIntent)
    }
}