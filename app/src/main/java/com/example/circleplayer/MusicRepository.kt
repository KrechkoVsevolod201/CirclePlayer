// файл: app/src/main/java/com/example/circleplayer/MusicRepository.kt
package com.example.circleplayer

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore

object MusicRepository {
    fun getAudioTracks(context: Context): List<AudioTrack> {
        val tracks = mutableListOf<AudioTrack>()
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DURATION
        )

        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            null
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)

                tracks.add(
                    AudioTrack(
                        id = id,
                        title = cursor.getString(titleColumn),
                        artist = cursor.getString(artistColumn),
                        uri = uri.toString(),
                        duration = cursor.getLong(durationColumn)
                    )
                )
            }
        }

        return tracks
    }
}