package dev.victorlpgazolli.mobilesink

import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbAccessory
import android.hardware.usb.UsbManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.os.Process.*
import android.util.Log
import android.util.Log.e
import java.io.FileInputStream

class AccessoryService : Service() {

    companion object {
        private const val TAG = "AOA_Audio"
        private const val ACTION_USB_PERMISSION = "dev.victorlpgazolli.mobilesink.USB_PERMISSION"
        private const val CHANNEL_ID = "aoa_audio_channel"
        private const val NOTIFICATION_ID = 1
    }

    private var usbManager: UsbManager? = null
    private var fileDescriptor: ParcelFileDescriptor? = null
    private var playbackThread: Thread? = null

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
        if (playbackThread != null) return

        fileDescriptor = usbManager?.openAccessory(accessory) ?: return

        playbackThread = Thread {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO)
            val sampleRate = 48000
            val frameBytes = 4 // stereo 16-bit
            val framesPerChunk = 960 // 20ms
            val chunkSize = framesPerChunk * frameBytes // 3840
            val minBufferSize = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_STEREO,
                AudioFormat.ENCODING_PCM_16BIT
            )

            val bufferSizeInBytes = maxOf(minBufferSize, chunkSize * 4)

            val audioTrack = AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build())
                .setAudioFormat(AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                    .build())
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setBufferSizeInBytes(bufferSizeInBytes)
                .build()


            val inputStream = FileInputStream(fileDescriptor!!.fileDescriptor)
            val tempBuffer = ByteArray(chunkSize)
            try {
                audioTrack.play()

                while (!Thread.interrupted()) {
                    var total = 0
                    while (total < chunkSize) {
                        val r = inputStream.read(tempBuffer, total, chunkSize - total)
                        if (r <= 0) break
                        total += r
                    }

                    if (total == chunkSize) {
                        audioTrack.write(tempBuffer, 0, total)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Erro no streaming: ${e.message}")
            } finally {
                audioTrack.stop()
                audioTrack.release()
                fileDescriptor?.close()
                fileDescriptor = null
                playbackThread = null
            }
        }
        playbackThread?.start()
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
        playbackThread?.interrupt()
        playbackThread = null
        fileDescriptor?.close()
        fileDescriptor = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}