package dev.victorlpgazolli.mobilesink

import ANDROID_AUDIO_TRACK_BUFFER_CAPACITY_FACTOR
import BITS_PER_SAMPLE
import CHANNELS
import FRAMES_PER_CHUNK
import LOG_TAG
import SAMPLE_RATE
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.ParcelFileDescriptor
import android.os.Process.THREAD_PRIORITY_URGENT_AUDIO
import android.os.Process.setThreadPriority
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.log10
import kotlin.math.sqrt

data class StereoPower(val left: Float, val right: Float)

class AudioOutputService : AudioSource {

    private var playbackThread: Thread? = null
    private val _powerLevel = MutableStateFlow(StereoPower(0f, 0f))
    val powerLevel: StateFlow<StereoPower> = _powerLevel.asStateFlow()

    private val _throughputBps = MutableStateFlow(0L)
    val throughputBps: StateFlow<Long> = _throughputBps.asStateFlow()

    private var lastL = 0f
    private var lastR = 0f

    override fun startRecording(fileDescriptor: ParcelFileDescriptor) {
        if (playbackThread != null) return

        playbackThread = Thread {
            Log.i(LOG_TAG, "AudioOutputService: Monitoring started with enhanced sensitivity")
            setThreadPriority(THREAD_PRIORITY_URGENT_AUDIO)
            
            val frameBytes = (BITS_PER_SAMPLE / 8) * CHANNELS
            val chunkSize = FRAMES_PER_CHUNK * frameBytes
            val buffer = ByteArray(chunkSize)

            val audioTrack = AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build())
                .setAudioFormat(AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                    .build())
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setBufferSizeInBytes(maxOf(
                    AudioTrack.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT),
                    chunkSize * ANDROID_AUDIO_TRACK_BUFFER_CAPACITY_FACTOR
                ))
                .build()

            val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
            
            var bytesInSecond = 0L
            var lastSecondTime = System.currentTimeMillis()

            try {
                audioTrack.play()
                while (!Thread.interrupted()) {
                    var bytesRead = 0
                    while (bytesRead < chunkSize) {
                        val r = inputStream.read(buffer, bytesRead, chunkSize - bytesRead)
                        if (r <= 0) break
                        bytesRead += r
                    }

                    if (bytesRead == chunkSize) {
                        updatePowerLevel(buffer)
                        audioTrack.write(buffer, 0, bytesRead)
                        
                        bytesInSecond += bytesRead
                        val currentTime = System.currentTimeMillis()
                        if (currentTime - lastSecondTime >= 1000) {
                            _throughputBps.value = bytesInSecond
                            bytesInSecond = 0
                            lastSecondTime = currentTime
                        }
                    } else if (bytesRead == 0) {
                        _powerLevel.value = StereoPower(0f, 0f)
                        _throughputBps.value = 0
                    }
                }
            } catch (e: Exception) {
                Log.e(LOG_TAG, "Playback error: ${e.message}")
            } finally {
                try { audioTrack.stop() } catch (e: Exception) {}
                audioTrack.release()
                try { fileDescriptor.close() } catch (e: Exception) {}
                playbackThread = null
                _powerLevel.value = StereoPower(0f, 0f)
                _throughputBps.value = 0
            }
        }
        playbackThread?.start()
    }

    private fun updatePowerLevel(buffer: ByteArray) {
        val shorts = ByteBuffer.wrap(buffer).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        var sumL = 0.0
        var sumR = 0.0
        val count = shorts.capacity()
        
        for (i in 0 until count step 2) {
            val l = shorts.get(i).toDouble()
            sumL += l * l
            if (i + 1 < count) {
                val r = shorts.get(i + 1).toDouble()
                sumR += r * r
            }
        }
        
        val rmsL = sqrt(sumL / (count / 2))
        val rmsR = sqrt(sumR / (count / 2))
        
        val dbL = if (rmsL > 0.01) 20 * log10(rmsL / 32767.0) else -65.0
        val dbR = if (rmsR > 0.01) 20 * log10(rmsR / 32767.0) else -65.0
        
        var targetL = ((dbL + 65.0) / 65.0 * 100.0).toFloat().coerceIn(0f, 100f)
        var targetR = ((dbR + 65.0) / 65.0 * 100.0).toFloat().coerceIn(0f, 100f)

        if (targetL < lastL) targetL = lastL * 0.85f + targetL * 0.15f
        if (targetR < lastR) targetR = lastR * 0.85f + targetR * 0.15f
        
        lastL = targetL
        lastR = targetR
        
        _powerLevel.value = StereoPower(targetL, targetR)
    }

    override fun stopRecording() {
        playbackThread?.interrupt()
        playbackThread = null
    }
}
