package com.example.circleplayer

import android.content.ContentUris
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import java.io.File

object MusicRepository {
    fun getAudioTracks(context: Context, folderPath: String? = null): List<AudioTrack> {
        val tracks = mutableListOf<AudioTrack>()
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.ALBUM_ID
        )

        val selectionParts = mutableListOf<String>()
        val selectionArgs = mutableListOf<String>()

        selectionParts += "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        selectionParts += "${MediaStore.Audio.Media.DURATION} > 0"

        if (!folderPath.isNullOrBlank()) {
            selectionParts += "${MediaStore.Audio.Media.DATA} LIKE ?"
            selectionArgs += if (folderPath.endsWith("/")) "$folderPath%" else "$folderPath/%"
        }

        val selection = selectionParts.joinToString(" AND ")
        val sortOrder = "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"

        try {
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs.toTypedArray(),
                sortOrder
            )?.use { cursor ->
                if (cursor.columnCount == 0) return@use

                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val uri = ContentUris.withAppendedId(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        id
                    )
                    val albumId = cursor.getLong(albumIdCol)
                    val albumArtUri = if (albumId > 0L) {
                        "content://media/external/audio/albumart/$albumId"
                    } else {
                        null
                    }
                    tracks.add(
                        AudioTrack(
                            id = id,
                            title = cursor.getString(titleCol) ?: "Unknown",
                            artist = cursor.getString(artistCol) ?: "Unknown",
                            uri = uri.toString(),
                            duration = cursor.getLong(durationCol),
                            albumArtUri = albumArtUri
                        )
                    )
                }
            }
        } catch (_: Exception) {
            // ignore query errors
        }

        return tracks
    }

    fun getAudioFolders(context: Context): List<AudioFolder> {
        val folderCounts = linkedMapOf<String, Int>()
        val projection = arrayOf(MediaStore.Audio.Media.DATA)
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND " +
            "${MediaStore.Audio.Media.DURATION} > 0"

        try {
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                null,
                "${MediaStore.Audio.Media.DATA} COLLATE NOCASE ASC"
            )?.use { cursor ->
                val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                while (cursor.moveToNext()) {
                    val filePath = cursor.getString(dataColumn) ?: continue
                    val folderPath = File(filePath).parentFile?.absolutePath ?: continue
                    folderCounts[folderPath] = (folderCounts[folderPath] ?: 0) + 1
                }
            }
        } catch (_: Exception) {
            // An empty directory list is shown when the media query is unavailable.
        }

        return folderCounts.map { (path, count) ->
            AudioFolder(
                path = path,
                name = File(path).name.ifBlank { path },
                trackCount = count
            )
        }.sortedBy { it.name.lowercase() }
    }

    fun getAudioFolderRoot(folders: List<AudioFolder>): String {
        val primaryStorage = Environment.getExternalStorageDirectory().absolutePath.trimEnd('/')
        val allInPrimaryStorage = folders.all { folder ->
            folder.path == primaryStorage || folder.path.startsWith("$primaryStorage/")
        }
        return if (allInPrimaryStorage) primaryStorage else File.separator
    }

    fun getImmediateAudioFolders(
        folders: List<AudioFolder>,
        parentPath: String
    ): List<AudioFolder> {
        val normalizedParent = parentPath.trimEnd('/').ifEmpty { File.separator }
        val childCounts = linkedMapOf<String, Int>()

        folders.forEach { folder ->
            val relativePath = when {
                normalizedParent == File.separator -> folder.path.removePrefix(File.separator)
                folder.path == normalizedParent -> ""
                folder.path.startsWith("$normalizedParent/") ->
                    folder.path.removePrefix("$normalizedParent/")
                else -> return@forEach
            }
            if (relativePath.isEmpty()) return@forEach

            val childName = relativePath.substringBefore('/')
            val childPath = if (normalizedParent == File.separator) {
                "$normalizedParent$childName"
            } else {
                "$normalizedParent/$childName"
            }
            childCounts[childPath] = (childCounts[childPath] ?: 0) + folder.trackCount
        }

        return childCounts.map { (path, count) ->
            AudioFolder(
                path = path,
                name = File(path).name.ifBlank { path },
                trackCount = count
            )
        }.sortedBy { it.name.lowercase() }
    }

    fun getAudioTrackCountInFolder(folders: List<AudioFolder>, folderPath: String): Int {
        val normalizedPath = folderPath.trimEnd('/').ifEmpty { File.separator }
        return folders.sumOf { folder ->
            if (normalizedPath == File.separator || folder.path == normalizedPath ||
                folder.path.startsWith("$normalizedPath/")) {
                folder.trackCount
            } else {
                0
            }
        }
    }
}
