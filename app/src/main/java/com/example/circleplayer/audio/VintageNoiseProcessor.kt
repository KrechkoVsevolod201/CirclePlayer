package com.example.circleplayer.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import kotlin.math.sin
import kotlin.random.Random

@UnstableApi
class VintageNoiseProcessor : BaseAudioProcessor() {

    @Volatile
    var enabled = false

    @Volatile
    var noiseLevel = 0.1f
        set(value) {
            field = value.coerceIn(0f, 1f)
        }

    @Volatile
    var crackleIntensity = 0.3f
        set(value) {
            field = value.coerceIn(0f, 1f)
        }

    private var sampleCounter = 0L

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

        if (!enabled) {
            output.put(inputBuffer)
            output.flip()
            return
        }

        val sampleCount = remaining / 2
        for (i in 0 until sampleCount) {
            val sample = inputBuffer.short.toFloat()

            val whiteNoise = (Random.nextFloat() * 2f - 1f) * noiseLevel * 800f
            val cracklePhase = sampleCounter * 0.001
            val crackle = (sin(cracklePhase) * Random.nextFloat() * crackleIntensity * 1500f).toFloat()
            val crackleSpike = if (Random.nextFloat() < 0.0008f * crackleIntensity) {
                Random.nextFloat() * 4000f * crackleIntensity * if (Random.nextBoolean()) 1f else -1f
            } else {
                0f
            }

            val mixed = (sample + whiteNoise + crackle + crackleSpike)
                .toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            output.putShort(mixed.toShort())
            sampleCounter++
        }
        output.flip()
    }

    override fun onFlush() {
        sampleCounter = 0L
    }

    override fun onReset() {
        onFlush()
    }
}
