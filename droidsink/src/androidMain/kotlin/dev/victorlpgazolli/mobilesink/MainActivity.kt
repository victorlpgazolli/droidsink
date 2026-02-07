package dev.victorlpgazolli.mobilesink

import android.Manifest.permission.RECORD_AUDIO
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.usb.UsbAccessory
import android.hardware.usb.UsbManager
import android.os.Bundle
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val accessory = intent.getParcelableExtra<UsbAccessory>(UsbManager.EXTRA_ACCESSORY)

        checkMicPermission()
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

    fun checkMicPermission() {
        if (ContextCompat.checkSelfPermission(this, RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(this, arrayOf(RECORD_AUDIO), 101)
        }
    }
    private fun startBridgeService(accessory: UsbAccessory) {
        val serviceIntent = Intent(this, AccessoryService::class.java)
        serviceIntent.putExtra(UsbManager.EXTRA_ACCESSORY, accessory)

        startForegroundService(serviceIntent)
    }
}