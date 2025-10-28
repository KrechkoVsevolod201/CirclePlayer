package com.example.circleplayer

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore

object MusicRepository {
    fun getAudioTracks(context: Context, folderPath: String? = null): List<AudioTrack> {
        val tracks = mutableListOf<AudioTrack>()
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA
        )

        val (selection, selectionArgs) = if (!folderPath.isNullOrBlank()) {
            // Только если путь не null и не пустой
            "${MediaStore.Audio.Media.DATA} LIKE ?" to arrayOf("$folderPath%")
        } else {
            null to null
        }

        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            null
        )?.use { cursor ->
            if (cursor.columnCount == 0) return@use

            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
                tracks.add(
                    AudioTrack(
                        id = id,
                        title = cursor.getString(titleCol),
                        artist = cursor.getString(artistCol),
                        uri = uri.toString(),
                        duration = cursor.getLong(durationCol)
                    )
                )
            }
        }

        return tracks
    }
}