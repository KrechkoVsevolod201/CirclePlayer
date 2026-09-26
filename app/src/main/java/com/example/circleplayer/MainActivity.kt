package com.example.circleplayer

import android.Manifest
import android.content.ComponentName
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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.example.circleplayer.audio.EffectsManager
import com.example.circleplayer.ui.theme.CirclePlayerTheme
import com.example.circleplayer.ui.theme.DarkPalette
import com.example.circleplayer.ui.theme.LightPalette
import com.example.circleplayer.ui.theme.ThemeColors
import com.example.circleplayer.ui.theme.ThemePreset
import com.example.circleplayer.ui.theme.decodeThemePreset
import com.example.circleplayer.ui.theme.encodeThemePreset
import java.io.File

@UnstableApi
class MainActivity : ComponentActivity() {

    private lateinit var effectsManager: EffectsManager
    private var mediaControllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController by mutableStateOf<MediaController?>(null)

    private var selectedFolderPath by mutableStateOf<String?>(null)
    private var darkTheme by mutableStateOf(true)
    private var appLanguage by mutableStateOf("ru")
    private var activeThemeColors by mutableStateOf(ThemeColors(LightPalette, DarkPalette))

    private fun localized(russian: String, english: String) =
        if (appLanguage == "en") english else russian

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            Toast.makeText(
                this,
                localized("Требуется доступ к аудиофайлам", "Audio file access is required"),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private val requestNotificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            Toast.makeText(
                this,
                localized(
                    "Требуется разрешение на уведомления для фонового воспроизведения",
                    "Notification permission is required for background playback"
                ),
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
                Toast.makeText(this, localized("Папка: $path", "Folder: $path"), Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(
                    this,
                    localized("Не удалось определить путь к папке", "Could not determine the folder path"),
                    Toast.LENGTH_SHORT
                ).show()
            }
        } catch (e: Exception) {
            Toast.makeText(
                this,
                localized("Ошибка выбора папки: ${e.message}", "Folder selection error: ${e.message}"),
                Toast.LENGTH_LONG
            ).show()
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
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        darkTheme = prefs.getBoolean("dark_theme", true)
        appLanguage = prefs.getString("app_language", "ru")?.takeIf { it == "en" } ?: "ru"
        activeThemeColors = prefs.getString("active_theme_colors_json", null)
            ?.let(::decodeThemePreset)
            ?.let { ThemeColors(it.light, it.dark) }
            ?: ThemeColors(LightPalette, DarkPalette)
        selectedFolderPath = prefs.getString("selected_music_folder_path", null)

        effectsManager = EffectsManager.shared

        requestPermissionIfNeeded()
        requestNotificationPermissionIfNeeded()

        setContent {
            CirclePlayerTheme(
                darkTheme = darkTheme,
                lightPalette = activeThemeColors.light,
                darkPalette = activeThemeColors.dark
            ) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val player = mediaController
                    if (player == null) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    } else {
                        MusicPlayerApp(
                            initialPlayer = player,
                            effectsManager = effectsManager,
                            initialFolderPath = selectedFolderPath,
                            onFolderSelect = { folderPicker.launch(null) },
                            darkTheme = darkTheme,
                            language = appLanguage,
                            themeColors = activeThemeColors,
                            onLanguageChange = { language ->
                                appLanguage = language
                                prefs.edit().putString("app_language", language).apply()
                            },
                            onThemeColorsChange = { colors ->
                                activeThemeColors = colors
                                prefs.edit().putString(
                                    "active_theme_colors_json",
                                    encodeThemePreset(
                                        ThemePreset("active", "Current", colors.light, colors.dark)
                                    )
                                ).apply()
                            },
                            onToggleTheme = {
                                darkTheme = !darkTheme
                                prefs.edit().putBoolean("dark_theme", darkTheme).apply()
                            }
                        )
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val sessionToken = SessionToken(
            this,
            ComponentName(this, com.example.circleplayer.service.PlaybackService::class.java)
        )
        val controllerFuture = MediaController.Builder(this, sessionToken).buildAsync()
        mediaControllerFuture = controllerFuture
        controllerFuture.addListener(
            {
                if (mediaControllerFuture === controllerFuture) {
                    try {
                        mediaController = controllerFuture.get()
                    } catch (e: Exception) {
                        if (!isFinishing && !isChangingConfigurations) {
                            Toast.makeText(
                                this,
                                localized(
                                    "Не удалось подключиться к медиаплееру: ${e.message}",
                                    "Could not connect to the media player: ${e.message}"
                                ),
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }
            },
            ContextCompat.getMainExecutor(this)
        )
    }

    override fun onStop() {
        mediaController = null
        mediaControllerFuture?.let(MediaController::releaseFuture)
        mediaControllerFuture = null
        super.onStop()
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
    }
}
