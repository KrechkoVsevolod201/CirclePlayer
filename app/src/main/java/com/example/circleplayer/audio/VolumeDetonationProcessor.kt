package com.example.circleplayer.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import kotlin.math.abs
import kotlin.math.sign
import kotlin.math.tanh

@UnstableApi
class VolumeDetonationProcessor : BaseAudioProcessor() {

    @Volatile
    var enabled = false

    @Volatile
    var amount = 0.3f
        set(value) {
            field = value.coerceIn(0f, 1f)
        }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        val output = replaceOutputBuffer(remaining)

        if (!enabled || amount <= 0f) {
            output.put(inputBuffer)
            output.flip()
            return
        }

        val drive = 1f + amount * 8f
        val sampleCount = remaining / 2
        for (i in 0 until sampleCount) {
            val sample = inputBuffer.short.toFloat() / 32768f
            val absSample = abs(sample)
            val processed = if (absSample > 0.4f) {
                val overdriven = tanh(sample * drive)
                val blend = amount.coerceIn(0f, 1f)
                sample * (1f - blend * 0.35f) + overdriven * (blend * 0.35f)
            } else {
                sample
            }
            val out = (processed * 32767f)
                .toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            output.putShort(out.toShort())
        }
        output.flip()
    }
}
