package com.example.circleplayer.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import kotlin.math.sin
import kotlin.math.PI
import kotlin.random.Random

@UnstableApi
class VintageNoiseProcessor : BaseAudioProcessor() {

    @Volatile
    var enabled = false

    @Volatile
    var effectsEnabled = false

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

    private var cracklePhase = 0.0

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

        if (!enabled || !effectsEnabled) {
            output.put(inputBuffer)
            output.flip()
            return
        }

        val sampleCount = remaining / 2
        val random = Random.Default
        val whiteNoiseAmplitude = noiseLevel * 800f
        val currentCrackleIntensity = crackleIntensity
        val crackleAmplitude = currentCrackleIntensity * 1500f
        val spikeProbability = 0.0008f * currentCrackleIntensity
        val spikeAmplitude = 4000f * currentCrackleIntensity
        val twoPi = 2.0 * PI
        var phase = cracklePhase
        for (i in 0 until sampleCount) {
            val sample = inputBuffer.short.toFloat()

            val whiteNoise = (random.nextFloat() * 2f - 1f) * whiteNoiseAmplitude
            val crackle = (sin(phase) * random.nextFloat() * crackleAmplitude).toFloat()
            val crackleSpike = if (random.nextFloat() < spikeProbability) {
                random.nextFloat() * spikeAmplitude * if (random.nextBoolean()) 1f else -1f
            } else {
                0f
            }

            val mixed = (sample + whiteNoise + crackle + crackleSpike)
                .toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            output.putShort(mixed.toShort())
            phase += 0.001
            if (phase >= twoPi) phase -= twoPi
        }
        cracklePhase = phase
        output.flip()
    }

    override fun onFlush() {
        cracklePhase = 0.0
    }

    override fun onReset() {
        onFlush()
    }
}
