package com.example.circleplayer.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import kotlin.math.PI
import kotlin.math.sin

@UnstableApi
class WowFlutterProcessor : BaseAudioProcessor() {

    @Volatile
    var enabled = false

    @Volatile
    var depth = 0.5f
        set(value) {
            field = value.coerceIn(0f, 1f)
        }

    @Volatile
    var rate = 0.5f
        set(value) {
            field = value.coerceIn(0.1f, 5f)
        }

    private var sampleRate = 44100
    private var channelCount = 2
    private var position = 0L
    private val delayLine = ShortArray(MAX_DELAY_SAMPLES * 2)
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
            for (i in 0 until frameCount * channelCount) {
                val sample = inputBuffer.short
                delayLine[writePos] = sample
                writePos = (writePos + 1) % delayLine.size
                output.putShort(sample)
                position++
            }
            output.flip()
            return
        }

        val maxDelayFrames = (sampleRate * 0.015).toInt().coerceAtLeast(1)
        val delayCapacityFrames = delayLine.size / channelCount

        for (frame in 0 until frameCount) {
            val time = position.toDouble() / sampleRate
            val modulation = sin(2.0 * PI * rate * time) * depth
            val delayFrames = ((maxDelayFrames / 2.0) * (1.0 + modulation))
                .toInt()
                .coerceIn(1, maxDelayFrames)

            for (ch in 0 until channelCount) {
                val sample = inputBuffer.short
                delayLine[writePos] = sample

                val readFrame = (writePos / channelCount - delayFrames + delayCapacityFrames) % delayCapacityFrames
                val readIndex = readFrame * channelCount + ch
                output.putShort(delayLine[readIndex])

                writePos = (writePos + 1) % delayLine.size
            }
            position++
        }
        output.flip()
    }

    override fun onFlush() {
        position = 0L
        writePos = 0
        delayLine.fill(0)
    }

    override fun onReset() {
        onFlush()
    }

    companion object {
        private const val MAX_DELAY_SAMPLES = 44100
    }
}
