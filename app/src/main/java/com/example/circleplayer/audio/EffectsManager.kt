package com.example.circleplayer.audio

import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi

@UnstableApi
class EffectsManager {

    val wowFlutter = WowFlutterProcessor()
    val volumeDetonation = VolumeDetonationProcessor()
    val chorus = ChorusProcessor()
    val vintageNoise = VintageNoiseProcessor()

    fun getAudioProcessors(): Array<AudioProcessor> {
        return arrayOf(
            wowFlutter,
            volumeDetonation,
            chorus,
            vintageNoise
        )
    }
}
