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
import java.io.FileInputStream

class AudioOutputService : AudioSource {

    private var playbackThread: Thread? = null

    override fun startRecording(fileDescriptor: ParcelFileDescriptor) {
        if (playbackThread != null) return

        playbackThread = Thread {
            Log.i(LOG_TAG, "Starting audio output service with fd: ${fileDescriptor.fileDescriptor}")
            setThreadPriority(THREAD_PRIORITY_URGENT_AUDIO)
            val frameBytes = (BITS_PER_SAMPLE / 8) * CHANNELS
            val chunkSize = FRAMES_PER_CHUNK * frameBytes
            val minBufferSize = AudioTrack.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_STEREO,
                AudioFormat.ENCODING_PCM_16BIT
            )

            val bufferSizeInBytes = maxOf(minBufferSize, chunkSize * ANDROID_AUDIO_TRACK_BUFFER_CAPACITY_FACTOR)

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
                .setBufferSizeInBytes(bufferSizeInBytes)
                .build()


            val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
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
                Log.e(LOG_TAG, "Streaming error: ${e.message}")
            } finally {
                audioTrack.stop()
                audioTrack.release()
                fileDescriptor.close()
                playbackThread = null
            }
        }
        playbackThread?.start()
    }
    override fun stopRecording() {
        if (playbackThread != null) {
            playbackThread?.interrupt()
            playbackThread = null
        }
    }

}