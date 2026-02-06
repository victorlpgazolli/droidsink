package dev.victorlpgazolli.mobilesink

import LOG_TAG
import android.Manifest.permission.RECORD_AUDIO
import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.usb.UsbAccessory
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.ActivityCompat.requestPermissions
import androidx.core.content.ContextCompat

class AccessoryService : Service() {

    companion object {
        private const val ACTION_USB_PERMISSION = "dev.victorlpgazolli.mobilesink.USB_PERMISSION"
        private const val CHANNEL_ID = "aoa_audio_channel"
        private const val NOTIFICATION_ID = 1
    }

    private var usbManager: UsbManager? = null
    private var fileDescriptor: ParcelFileDescriptor? = null

    private val audioOutputService = AudioOutputService()
    private val audioInputService = AudioInputService()

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (ACTION_USB_PERMISSION == intent.action) {
                val accessory =
                    intent.getParcelableExtra<UsbAccessory>(UsbManager.EXTRA_ACCESSORY)
                        ?: usbManager?.accessoryList?.firstOrNull()
                        ?: return
                if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                    startPlayback(accessory)
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        usbManager = getSystemService(USB_SERVICE) as UsbManager
        val intentFilter = IntentFilter("dev.victorlpgazolli.mobilesink.USB_PERMISSION")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(usbReceiver, intentFilter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(usbReceiver, intentFilter)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        val accessory =
            intent?.getParcelableExtra<UsbAccessory>(UsbManager.EXTRA_ACCESSORY)
                ?: usbManager?.accessoryList?.firstOrNull()
                ?: return START_NOT_STICKY

        if (usbManager?.hasPermission(accessory) == true) {
            startPlayback(accessory)
        } else {
            val permissionIntent = Intent(ACTION_USB_PERMISSION).apply {
                setPackage(packageName)
            }

            val pi = PendingIntent.getBroadcast(
                this,
                0,
                permissionIntent,
                PendingIntent.FLAG_IMMUTABLE
            )

            usbManager?.requestPermission(accessory, pi)
        }
        return START_STICKY
    }

    private fun startPlayback(accessory: UsbAccessory) {
        Log.i(LOG_TAG, "Starting playback for accessory: ${accessory.model}")
        fileDescriptor = usbManager?.openAccessory(accessory) ?: return
        Log.i(LOG_TAG, "File descriptor obtained: ${fileDescriptor?.fileDescriptor}")
        fileDescriptor?.let {
            audioOutputService.startRecording(it)
            audioInputService.startRecording(it)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "USB Audio", NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification = Notification.Builder(this, CHANNEL_ID)
        .setContentTitle("Audio Bridge").setSmallIcon(android.R.drawable.ic_media_play).setOngoing(true).build()

    override fun onDestroy() {
        fileDescriptor?.close()
        fileDescriptor = null
        audioOutputService.stopRecording()
        audioInputService.stopRecording()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}