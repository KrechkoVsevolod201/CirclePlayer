package com.example.circleplayer.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import kotlin.math.PI
import kotlin.math.sin

@UnstableApi
class ChorusProcessor : BaseAudioProcessor() {

    @Volatile
    var enabled = false

    @Volatile
    var depth = 0.5f
        set(value) {
            field = value.coerceIn(0f, 1f)
        }

    @Volatile
    var rate = 1.5f
        set(value) {
            field = value.coerceIn(0.1f, 5f)
        }

    @Volatile
    var mix = 0.5f
        set(value) {
            field = value.coerceIn(0f, 1f)
        }

    private var sampleRate = 44100
    private var channelCount = 2
    private var position = 0L
    private val delayBuffer = FloatArray(MAX_DELAY_SAMPLES * 2)
    private var writePos = 0

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        sampleRate = inputAudioFormat.sampleRate
        channelCount = inputAudioFormat.channelCount
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val frameSize = channelCount * 2
        val frameCount = inputBuffer.remaining() / frameSize
        if (frameCount == 0) return

        val output = replaceOutputBuffer(frameCount * frameSize)

        if (!enabled) {
            output.put(inputBuffer)
            output.flip()
            return
        }

        val maxDelay = (sampleRate * 0.025).toInt().coerceAtLeast(2)
        val capacityFrames = delayBuffer.size / channelCount

        for (frame in 0 until frameCount) {
            val time = position.toDouble() / sampleRate
            val modulation = sin(2.0 * PI * rate * time) * depth
            val delayFrames = ((maxDelay / 2.0) * (1.0 + modulation))
                .toInt()
                .coerceIn(1, maxDelay - 1)

            for (ch in 0 until channelCount) {
                val sample = inputBuffer.short.toFloat() / 32768f
                delayBuffer[writePos] = sample

                val readFrame = (writePos / channelCount - delayFrames + capacityFrames) % capacityFrames
                val delayed = delayBuffer[readFrame * channelCount + ch]
                val mixed = sample * (1f - mix) + delayed * mix
                val out = (mixed * 32767f)
                    .toInt()
                    .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                output.putShort(out.toShort())

                writePos = (writePos + 1) % delayBuffer.size
            }
            position++
        }
        output.flip()
    }

    override fun onFlush() {
        position = 0L
        writePos = 0
        delayBuffer.fill(0f)
    }

    override fun onReset() {
        onFlush()
    }

    companion object {
        private const val MAX_DELAY_SAMPLES = 44100
    }
}
