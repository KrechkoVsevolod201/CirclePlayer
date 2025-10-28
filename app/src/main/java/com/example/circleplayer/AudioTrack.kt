// файл: app/src/main/java/com/example/circleplayer/AudioTrack.kt
package com.example.circleplayer

data class AudioTrack(
    val id: Long,
    val title: String,
    val artist: String,
    val uri: String,
    val duration: Long
)