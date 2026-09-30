package com.flowdictation

import java.nio.ByteBuffer
import java.nio.ByteOrder

const val SAMPLE_RATE = 16000

/** Helpers for raw 16 kHz mono 16-bit PCM audio. No Android dependencies. */
object Pcm {
    private const val FRAME = 320 // 20 ms at 16 kHz

    /** Root-mean-square loudness of each 20 ms frame. */
    fun frameRms(pcm: ByteArray): DoubleArray {
        val frames = pcm.size / 2 / FRAME
        val out = DoubleArray(frames)
        for (f in 0 until frames) {
            var sum = 0.0
            val base = f * FRAME * 2
            for (i in 0 until FRAME) {
                val lo = pcm[base + i * 2].toInt() and 0xFF
                val hi = pcm[base + i * 2 + 1].toInt() // sign-extended
                val s = (hi shl 8) or lo
                sum += (s * s).toDouble()
            }
            out[f] = Math.sqrt(sum / FRAME)
        }
        return out
    }

    /**
     * Returns the recording trimmed to the spoken part (with generous padding),
     * or null if no speech was detected. Skipping silent recordings avoids Whisper's
     * habit of inventing text ("Thank you.") when it is fed silence.
     */
    fun trimToSpeech(pcm: ByteArray): ByteArray? {
        val rms = frameRms(pcm)
        if (rms.size < 15) return null // shorter than 0.3 s
        val sorted = rms.sortedArray()
        val floor = sorted[(sorted.size * 0.1).toInt()]
        val threshold = Math.min(Math.max(floor * 2.0, 200.0), 700.0)
        var first = -1
        var last = -1
        var loud = 0
        for (i in rms.indices) {
            if (rms[i] > threshold) {
                if (first < 0) first = i
                last = i
                loud++
            }
        }
        if (loud < 5) return null // less than 100 ms of sound
        val startFrame = Math.max(0, first - 20) // keep 400 ms before
        val endFrame = Math.min(rms.size, last + 26) // keep 500 ms after
        val startByte = startFrame * FRAME * 2
        val endByte = Math.min(pcm.size, endFrame * FRAME * 2)
        return pcm.copyOfRange(startByte, endByte)
    }

    /** Wraps raw PCM in a standard 44-byte WAV header. */
    fun toWav(pcm: ByteArray): ByteArray {
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray(Charsets.US_ASCII))
        header.putInt(36 + pcm.size)
        header.put("WAVE".toByteArray(Charsets.US_ASCII))
        header.put("fmt ".toByteArray(Charsets.US_ASCII))
        header.putInt(16)
        header.putShort(1) // PCM
        header.putShort(1) // mono
        header.putInt(SAMPLE_RATE)
        header.putInt(SAMPLE_RATE * 2) // byte rate
        header.putShort(2) // block align
        header.putShort(16) // bits per sample
        header.put("data".toByteArray(Charsets.US_ASCII))
        header.putInt(pcm.size)
        val out = ByteArray(44 + pcm.size)
        System.arraycopy(header.array(), 0, out, 0, 44)
        System.arraycopy(pcm, 0, out, 44, pcm.size)
        return out
    }
}
