package com.flowdictation

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import java.io.ByteArrayOutputStream

/** Captures 16 kHz mono 16-bit PCM from the microphone into memory. */
class Recorder {
    companion object {
        const val MAX_SECONDS = 300
    }

    @Volatile private var running = false
    private var record: AudioRecord? = null
    private var thread: Thread? = null
    private val buffer = ByteArrayOutputStream()

    /** Called (on the recorder thread) when the maximum length is reached. */
    var onMaxLength: (() -> Unit)? = null

    @SuppressLint("MissingPermission")
    fun start(): Boolean {
        if (running) return false
        val minBuf = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuf <= 0) return false
        val bufSize = Math.max(minBuf * 2, SAMPLE_RATE) // at least 0.5 s of audio

        val rec = try {
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufSize
            )
        } catch (e: Exception) {
            return false
        }
        if (rec.state != AudioRecord.STATE_INITIALIZED) {
            rec.release()
            return false
        }
        try {
            rec.startRecording()
        } catch (e: Exception) {
            rec.release()
            return false
        }
        if (rec.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
            rec.release()
            return false
        }

        synchronized(buffer) { buffer.reset() }
        record = rec
        running = true
        val maxBytes = MAX_SECONDS * SAMPLE_RATE * 2
        thread = Thread({
            val chunk = ByteArray(3200) // 100 ms
            while (running) {
                val n = rec.read(chunk, 0, chunk.size)
                if (n > 0) {
                    var full = false
                    synchronized(buffer) {
                        buffer.write(chunk, 0, n)
                        full = buffer.size() >= maxBytes
                    }
                    if (full) {
                        running = false
                        onMaxLength?.invoke()
                    }
                } else if (n < 0) {
                    break
                }
            }
        }, "flow-recorder").also { it.start() }
        return true
    }

    /** Stops recording and returns everything captured. */
    fun stop(): ByteArray {
        running = false
        try {
            thread?.join(1500)
        } catch (e: InterruptedException) {
            // ignore
        }
        thread = null
        val rec = record
        record = null
        if (rec != null) {
            try { rec.stop() } catch (e: Exception) { /* ignore */ }
            try { rec.release() } catch (e: Exception) { /* ignore */ }
        }
        return synchronized(buffer) { buffer.toByteArray() }
    }

    fun cancel() {
        stop()
        synchronized(buffer) { buffer.reset() }
    }
}
