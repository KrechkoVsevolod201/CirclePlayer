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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.example.circleplayer.audio.EffectsManager
import java.io.File

@UnstableApi
class MainActivity : ComponentActivity() {

    private lateinit var exoPlayer: ExoPlayer
    private lateinit var effectsManager: EffectsManager

    private var selectedFolderPath by mutableStateOf<String?>(null)

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            Toast.makeText(this, "Требуется доступ к аудиофайлам", Toast.LENGTH_LONG).show()
        }
    }

    private val requestNotificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            Toast.makeText(
                this,
                "Требуется разрешение на уведомления для фонового воспроизведения",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private val folderPicker = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri == null) return@registerForActivityResult

        try {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            try {
                contentResolver.takePersistableUriPermission(uri, flags)
            } catch (_: SecurityException) {
                // Some providers don't support persistable permissions
            }

            val path = getFullPathFromTreeUri(uri)
            if (path != null) {
                getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                    .edit()
                    .putString("selected_music_folder_path", path)
                    .apply()
                selectedFolderPath = path
                Toast.makeText(this, "Папка: $path", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Не удалось определить путь к папке", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Ошибка выбора папки: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun getFullPathFromTreeUri(treeUri: Uri): String? {
        return try {
            val docId = DocumentsContract.getTreeDocumentId(treeUri)
            when {
                docId.startsWith("primary:") -> {
                    val relativePath = docId.substringAfter("primary:")
                    File(Environment.getExternalStorageDirectory(), relativePath).absolutePath
                }
                docId.startsWith("raw:") -> docId.substringAfter("raw:")
                else -> {
                    // Fallback: try DATA column via DocumentsContract
                    null
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        effectsManager = EffectsManager()
        exoPlayer = ExoPlayer.Builder(this).build()

        requestPermissionIfNeeded()
        requestNotificationPermissionIfNeeded()

        selectedFolderPath = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            .getString("selected_music_folder_path", null)

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MusicPlayerApp(
                        initialExoPlayer = exoPlayer,
                        effectsManager = effectsManager,
                        initialFolderPath = selectedFolderPath,
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

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        exoPlayer.release()
    }
}
