package dev.victorlpgazolli.mobilesink

import CHANNELS
import LOG_TAG
import SAMPLE_RATE
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.ParcelFileDescriptor
import android.os.Process
import android.util.Log
import java.io.FileOutputStream
import java.io.IOException


class AudioInputService: AudioSource {
    private var audioRecord: AudioRecord? = null
    private var recordingThread: Thread? = null
    private lateinit var audioBuffer: ByteArray

    private var fileDescriptor: ParcelFileDescriptor? = null
    private val fileOutputStream: FileOutputStream?
        get() = FileOutputStream(fileDescriptor?.fileDescriptor).takeIf { fileDescriptor?.fileDescriptor != null }

    private fun initialize() {
        try {
            val bufferSize = getBufferSize()
            audioBuffer = ByteArray(bufferSize)
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.DEFAULT,
                SAMPLE_RATE,
                CHANNELS,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )

            Log.d(LOG_TAG, "AudioRecord created ${audioRecord?.state} initialized_state=${AudioRecord.STATE_INITIALIZED}")
        } catch (e: Exception) {
            Log.e(LOG_TAG, "AudioRecord not created: ${e.message}")
        }
    }

    override fun startRecording(hostFileDescriptor: ParcelFileDescriptor) {

        fileDescriptor = hostFileDescriptor
        initialize()

        if (audioRecord != null && audioRecord?.state == AudioRecord.STATE_INITIALIZED) {
            recordingThread = Thread {
                Log.i(LOG_TAG, "Starting audio input service with fd: ${fileDescriptor?.fileDescriptor}")
                Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
                audioRecord?.startRecording()

                while (!Thread.interrupted()) {
                    val numberRead = audioRecord!!.read(audioBuffer, 0, audioBuffer.size)

                    if (numberRead > 0) {
                        try {
                            fileOutputStream?.write(audioBuffer, 0, numberRead)
                        } catch (e: IOException) {
                            Log.e(LOG_TAG, "Error writing audio data in file: $e")
                            stopRecording()
                        }
                    }
                }
            }
            recordingThread?.start()
        } else {
            Log.d(LOG_TAG, "start not executed: AudioRecord is null")
        }
    }

    override fun stopRecording() {
        if (recordingThread != null) {
            recordingThread?.interrupt()
            recordingThread = null
        }
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
        fileOutputStream?.flush()
        fileOutputStream?.close()
        fileDescriptor?.close()
        fileDescriptor = null
    }

    private fun getBufferSize(): Int {
        val bufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            CHANNELS,
            AudioFormat.ENCODING_PCM_16BIT
        )
        Log.d(LOG_TAG, "bufferSize: $bufferSize")

        return bufferSize
    }
}