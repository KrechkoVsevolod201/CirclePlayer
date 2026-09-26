package com.example.circleplayer.audio

import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi

@UnstableApi
class EffectsManager {

    val wowFlutter = WowFlutterProcessor()
    val volumeDetonation = VolumeDetonationProcessor()
    val chorus = ChorusProcessor()
    val vintageNoise = VintageNoiseProcessor()

    @Volatile
    var effectsEnabled = false
        set(value) {
            field = value
            wowFlutter.effectsEnabled = value
            volumeDetonation.effectsEnabled = value
            chorus.effectsEnabled = value
            vintageNoise.effectsEnabled = value
        }

    fun getAudioProcessors(): Array<AudioProcessor> {
        return arrayOf(
            wowFlutter,
            volumeDetonation,
            chorus,
            vintageNoise
        )
    }

    companion object {
        val shared: EffectsManager by lazy { EffectsManager() }
    }
}
