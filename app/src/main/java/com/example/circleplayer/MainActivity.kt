package com.example.circleplayer

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.DocumentsContract
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.media3.exoplayer.ExoPlayer
import java.io.File

class MainActivity : ComponentActivity() {

    private lateinit var exoPlayer: ExoPlayer

    // ✅ Регистрируем лаунчеры НА УРОВНЕ КЛАССА (обязательно!)
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            Toast.makeText(this, "Требуется доступ к аудиофайлам", Toast.LENGTH_LONG).show()
        }
        // Если разрешение получено — MusicPlayerApp автоматически загрузит треки
    }

    private val folderPicker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION

            contentResolver.takePersistableUriPermission(uri, flags)

            val path = getFullPathFromTreeUri(uri)
            val sharedPref = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            sharedPref.edit()
                .putString("selected_music_folder_path", path)
                .apply()
        }
    }

    private fun getFullPathFromTreeUri(treeUri: Uri): String? {
        return try {
            val docId = DocumentsContract.getTreeDocumentId(treeUri)
            if (docId.startsWith("primary:")) {
                val relativePath = docId.substring(8)
                File(Environment.getExternalStorageDirectory(), relativePath).absolutePath
            } else if (docId.startsWith("raw:")) {
                docId.substring(4)
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        exoPlayer = ExoPlayer.Builder(this).build()
        requestPermissionIfNeeded()

        // На Android 11+ фильтрация по папке отключена из-за ограничений Scoped Storage
        val folderPath = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            null
        } else {
            getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                .getString("selected_music_folder_path", null)
        }

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MusicPlayerApp(
                        exoPlayer = exoPlayer,
                        initialFolderPath = folderPath,
                        onFolderSelect = { folderPicker.launch(null) }
                    )
                }
            }
        }
    }

    private fun requestPermissionIfNeeded() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(permission)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        exoPlayer.release()
    }
}