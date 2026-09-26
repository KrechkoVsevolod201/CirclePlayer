package com.example.circleplayer

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.graphics.BitmapFactory
import android.util.LruCache
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.media.ToneGenerator
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import androidx.compose.foundation.*
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.Image
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.*
import androidx.media3.common.MediaItem
import androidx.media3.common.Metadata
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.metadata.id3.TextInformationFrame
import com.example.circleplayer.audio.EffectsManager
import com.example.circleplayer.ui.theme.LocalPlayerPalette
import com.example.circleplayer.ui.theme.BuiltInThemePresets
import com.example.circleplayer.ui.theme.PaletteColorField
import com.example.circleplayer.ui.theme.PlayerPalette
import com.example.circleplayer.ui.theme.ThemeColors
import com.example.circleplayer.ui.theme.ThemePreset
import com.example.circleplayer.ui.theme.decodeThemePreset
import com.example.circleplayer.ui.theme.decodeThemePresetList
import com.example.circleplayer.ui.theme.encodeThemePreset
import com.example.circleplayer.ui.theme.encodeThemePresetList
import com.example.circleplayer.ui.theme.paletteColorFields
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.content.Context
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.ArrayDeque
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference

// =============== ВСПОМОГАТЕЛЬНЫЕ ФУНКЦИИ ===============

private fun formatTime(ms: Long): String {
    if (ms < 0) return "00:00"
    val totalSeconds = (ms / 1000).toInt()
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    val minuteText = if (minutes < 10) "0$minutes" else minutes.toString()
    val secondText = if (seconds < 10) "0$seconds" else seconds.toString()
    return "$minuteText:$secondText"
}

private val LocalPlayerLanguage = compositionLocalOf { "ru" }

private enum class PlayerPage { EFFECTS, THEMES, SCALE, SETTINGS, PLAYER }

@Composable
private fun localized(russian: String, english: String): String =
    if (LocalPlayerLanguage.current == "en") english else russian

private fun mixColors(from: Color, to: Color, fraction: Float) = Color(
    red = from.red + (to.red - from.red) * fraction,
    green = from.green + (to.green - from.green) * fraction,
    blue = from.blue + (to.blue - from.blue) * fraction,
    alpha = from.alpha + (to.alpha - from.alpha) * fraction
)

@Composable
private fun PlayerToggleIconButton(
    image: ImageVector,
    description: String,
    selected: Boolean,
    selectedTint: Color,
    inactiveTint: Color,
    iconRotation: Float = 0f,
    onClick: () -> Unit
) {
    val tint by animateColorAsState(
        targetValue = if (selected) selectedTint else inactiveTint,
        animationSpec = tween(180),
        label = "toggle-button-tint"
    )
    val iconScale by animateFloatAsState(
        targetValue = if (selected) 1.08f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 500f),
        label = "toggle-button-scale"
    )
    IconButton(
        onClick = onClick
    ) {
        Icon(
            imageVector = image,
            contentDescription = description,
            tint = tint,
            modifier = Modifier.graphicsLayer {
                scaleX = iconScale
                scaleY = iconScale
                rotationZ = iconRotation
            }
        )
    }
}

private fun AudioTrack.toMediaItem(): MediaItem {
    val metadata = MediaMetadata.Builder()
        .setTitle(title)
        .setArtist(artist)
        .apply {
            albumArtUri?.let { setArtworkUri(Uri.parse(it)) }
        }
        .build()
    return MediaItem.Builder()
        .setUri(Uri.parse(uri))
        .setMediaMetadata(metadata)
        .build()
}

private fun calculateAngle(point: Offset, center: Offset): Float {
    val dx = point.x - center.x
    val dy = point.y - center.y
    return Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
}

enum class PlayerScaleElement(val titleRu: String, val titleEn: String, val key: String) {
    COVER("Обложка / винил", "Cover / vinyl", "cover"),
    TRACK_INFO("Информация о треке", "Track information", "track_info"),
    PROGRESS("Шкала воспроизведения", "Playback progress", "progress"),
    CLICK_WHEEL("Click Wheel", "Click Wheel", "click_wheel"),
    ACTION_BUTTONS("Управляющие кнопки", "Control buttons", "action_buttons"),
    TRACK_LIST("Список треков и папок", "Track and folder list", "track_list")
}

private fun defaultPlayerScales() = PlayerScaleElement.entries.associateWith { 1f }

private fun playerScalePreferenceKey(orientation: String, element: PlayerScaleElement) =
    "player_scale_${orientation}_${element.key}"

private fun extractTrackBpm(metadata: Metadata): Float? {
    for (index in 0 until metadata.length()) {
        val frame = metadata[index] as? TextInformationFrame ?: continue
        val isBpmFrame = frame.id.equals("TBPM", ignoreCase = true) ||
            (frame.id.equals("TXXX", ignoreCase = true) &&
                frame.description.equals("BPM", ignoreCase = true))
        if (!isBpmFrame) continue
        return frame.value.trim().toFloatOrNull()?.takeIf { it in 40f..240f }
    }
    return null
}

private object AlbumArtworkCache {
    private val cache = object : LruCache<String, ImageBitmap>(8 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap): Int =
            (value.width * value.height * 4).coerceAtLeast(1)
    }

    fun get(uri: String): ImageBitmap? = cache.get(uri)

    fun put(uri: String, artwork: ImageBitmap) {
        cache.put(uri, artwork)
    }
}

@Composable
private fun rememberAlbumArtwork(artworkUri: String?): ImageBitmap? {
    val context = LocalContext.current.applicationContext
    val artwork by produceState<ImageBitmap?>(initialValue = null, key1 = artworkUri) {
        value = artworkUri?.let { uri ->
            withContext(Dispatchers.IO) {
                AlbumArtworkCache.get(uri) ?: run {
                    runCatching {
                        val parsedUri = Uri.parse(uri)
                        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        context.contentResolver.openInputStream(parsedUri)?.use {
                            BitmapFactory.decodeStream(it, null, bounds)
                        }
                        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                            return@runCatching null
                        }

                        var sampleSize = 1
                        while (maxOf(bounds.outWidth, bounds.outHeight) / sampleSize > 512) {
                            sampleSize *= 2
                        }
                        val options = BitmapFactory.Options().apply {
                            inSampleSize = sampleSize
                        }
                        context.contentResolver.openInputStream(parsedUri)?.use {
                            BitmapFactory.decodeStream(it, null, options)?.asImageBitmap()
                        }
                    }.getOrNull()?.also { decodedArtwork ->
                        AlbumArtworkCache.put(uri, decodedArtwork)
                    }
                }
            }
        }
    }
    return artwork
}

// =============== ОСНОВНОЙ КОМПОЗЕБЛ ===============

@UnstableApi
@Composable
fun MusicPlayerApp(
    initialPlayer: Player,
    effectsManager: EffectsManager,
    initialFolderPath: String? = null,
    onFolderSelect: () -> Unit,
    darkTheme: Boolean,
    language: String,
    themeColors: ThemeColors,
    onLanguageChange: (String) -> Unit,
    onThemeColorsChange: (ThemeColors) -> Unit,
    onToggleTheme: () -> Unit
) {
    val context = LocalContext.current

    var currentFolderPath by remember { mutableStateOf(initialFolderPath) }
    var lastKnownFolderPath by remember { mutableStateOf<String?>(null) }

    var selectedIndex by rememberSaveable { mutableIntStateOf(0) }
    var playingTrackIndex by remember { mutableIntStateOf(-1) }
    var selectedDirectoryIndex by rememberSaveable { mutableIntStateOf(0) }
    var selectionAnimationDurationMs by remember { mutableIntStateOf(200) }
    var lastWheelScrollAt by remember { mutableLongStateOf(0L) }

    var tracks by remember { mutableStateOf<List<AudioTrack>>(emptyList()) }
    var isPlaying by remember(initialPlayer) { mutableStateOf(initialPlayer.isPlaying) }
    var showEffectsMenu by remember { mutableStateOf(false) }
    var listMode by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showScaleSettings by remember { mutableStateOf(false) }
    var showThemeSettings by remember { mutableStateOf(false) }
    var showDirectoryBrowser by remember { mutableStateOf(false) }
    var showVinyl by remember { mutableStateOf(false) }
    var availableFolders by remember { mutableStateOf<List<AudioFolder>>(emptyList()) }
    var isLoadingFolders by remember { mutableStateOf(false) }
    var directoryRootPath by remember { mutableStateOf<String?>(null) }
    var directoryBrowsePath by remember { mutableStateOf<String?>(null) }

    val prefs = remember { context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE) }
    var savedThemePresets by remember {
        mutableStateOf(decodeThemePresetList(prefs.getString("saved_theme_presets_json", null)))
    }
    var customScaleEnabled by remember {
        mutableStateOf(prefs.getBoolean("player_custom_scale_enabled", false))
    }
    var portraitElementScales by remember {
        mutableStateOf(
            PlayerScaleElement.entries.associateWith { element ->
                prefs.getFloat(playerScalePreferenceKey("portrait", element), 1f)
            }
        )
    }
    var landscapeElementScales by remember {
        mutableStateOf(
            PlayerScaleElement.entries.associateWith { element ->
                prefs.getFloat(playerScalePreferenceKey("landscape", element), 1f)
            }
        )
    }
    var screensaverEnabled by remember {
        mutableStateOf(prefs.getBoolean("screensaver_enabled", false))
    }
    var easterEggsEnabled by remember {
        mutableStateOf(prefs.getBoolean("easter_eggs_enabled", false))
    }
    var wheelVibrationEnabled by remember {
        mutableStateOf(prefs.getBoolean("clickwheel_vibration_enabled", true))
    }
    var wheelClickSoundEnabled by remember {
        mutableStateOf(prefs.getBoolean("clickwheel_sound_enabled", true))
    }
    var wheelClickSoundUri by remember {
        mutableStateOf(prefs.getString("clickwheel_sound_uri", null))
    }
    var vinylRotationSpeed by remember {
        mutableFloatStateOf(prefs.getFloat("vinyl_rotation_speed", 1f).coerceIn(0.25f, 2.5f))
    }
    var vinylBpmSyncEnabled by remember {
        mutableStateOf(prefs.getBoolean("vinyl_bpm_sync_enabled", false))
    }
    var currentTrackBpm by remember(initialPlayer) { mutableFloatStateOf(120f) }
    val wheelClickSoundPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: SecurityException) {
                // Keep the selected sound for this session if the provider doesn't persist grants.
            }
            wheelClickSoundUri = uri.toString()
            prefs.edit().putString("clickwheel_sound_uri", uri.toString()).apply()
        }
    }
    var presetToExport by remember { mutableStateOf<ThemePreset?>(null) }
    val themeExportPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        val preset = presetToExport
        if (uri != null && preset != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { writer ->
                    writer.write(encodeThemePreset(preset))
                }
            }
        }
        presetToExport = null
    }
    val themeImportPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                val json = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                val imported = json?.let(::decodeThemePreset) ?: return@runCatching
                val saved = savedThemePresets + imported.copy(id = UUID.randomUUID().toString())
                savedThemePresets = saved
                prefs.edit().putString("saved_theme_presets_json", encodeThemePresetList(saved)).apply()
            }
        }
    }
    val exportThemePreset: (ThemePreset) -> Unit = { preset ->
        presetToExport = preset
        val safeName = preset.name.replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "theme" }
        themeExportPicker.launch("$safeName.json")
    }
    var lastInteraction by remember { mutableLongStateOf(System.currentTimeMillis()) }

    var shuffleEnabled by remember { mutableStateOf(false) }
    var repeatMode by remember { mutableStateOf(0) } // 0 - off, 1 - all, 2 - one

    var useEffects by remember { mutableStateOf(prefs.getBoolean("use_effects", false)) }
    val currentPlayer = initialPlayer
    val currentVinylRotationSpeed = (
        vinylRotationSpeed * if (vinylBpmSyncEnabled) currentTrackBpm / 120f else 1f
    ).coerceIn(0.1f, 4f)

    val currentDirectoryFolders = remember(availableFolders, directoryBrowsePath) {
        directoryBrowsePath?.let {
            MusicRepository.getImmediateAudioFolders(availableFolders, it)
        }.orEmpty()
    }
    val currentDirectoryTrackCount = remember(availableFolders, directoryBrowsePath) {
        directoryBrowsePath?.let {
            MusicRepository.getAudioTrackCountInFolder(availableFolders, it)
        } ?: availableFolders.sumOf { it.trackCount }
    }

    LaunchedEffect(initialFolderPath) {
        if (initialFolderPath != currentFolderPath) {
            currentFolderPath = initialFolderPath
        }
    }

    LaunchedEffect(currentFolderPath) {
        val newTracks = withContext(Dispatchers.IO) {
            MusicRepository.getAudioTracks(context, currentFolderPath)
        }
        tracks = newTracks

        val currentUri = currentPlayer.currentMediaItem?.localConfiguration?.uri?.toString()
        val currentTrackIndex = newTracks.indexOfFirst { it.uri == currentUri }
        playingTrackIndex = when {
            currentTrackIndex >= 0 -> currentTrackIndex
            isPlaying && newTracks.isNotEmpty() -> 0
            else -> -1
        }

        if (currentFolderPath != lastKnownFolderPath) {
            selectedIndex = currentTrackIndex.takeIf { it >= 0 } ?: 0
            lastKnownFolderPath = currentFolderPath
        } else if (currentTrackIndex >= 0) {
            selectedIndex = currentTrackIndex
        }

        if (tracks.isNotEmpty() && selectedIndex >= tracks.size) {
            selectedIndex = tracks.size - 1
        }
    }

    LaunchedEffect(showDirectoryBrowser) {
        if (showDirectoryBrowser) {
            isLoadingFolders = true
            availableFolders = withContext(Dispatchers.IO) {
                MusicRepository.getAudioFolders(context)
            }
            directoryRootPath = MusicRepository.getAudioFolderRoot(availableFolders)
            directoryBrowsePath = directoryRootPath
            isLoadingFolders = false
        }
    }

    LaunchedEffect(showDirectoryBrowser, availableFolders, directoryRootPath) {
        if (showDirectoryBrowser && !isLoadingFolders) {
            directoryBrowsePath = directoryRootPath
            selectedDirectoryIndex = 0
        }
    }

    LaunchedEffect(useEffects, effectsManager) {
        effectsManager.effectsEnabled = useEffects
    }

    LaunchedEffect(playingTrackIndex, tracks, isPlaying) {
        if (!isPlaying || tracks.isEmpty()) return@LaunchedEffect
        val track = tracks.getOrNull(playingTrackIndex) ?: return@LaunchedEffect
        val player = currentPlayer
        try {
            val currentUri = player.currentMediaItem?.localConfiguration?.uri?.toString()
            if (currentUri != track.uri) {
                player.setMediaItem(track.toMediaItem())
                player.prepare()
            }
            player.playWhenReady = true
            player.play()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Автопереход к следующему треку с учётом shuffle/repeat
    DisposableEffect(
        currentPlayer,
        tracks,
        playingTrackIndex,
        repeatMode,
        shuffleEnabled
    ) {
        val listener = object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                currentTrackBpm = 120f
            }

            override fun onMetadata(metadata: Metadata) {
                extractTrackBpm(metadata)?.let { currentTrackBpm = it }
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                isPlaying = playWhenReady
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState != Player.STATE_ENDED) return
                if (tracks.isEmpty()) return

                if (repeatMode == 2) {
                    currentPlayer.seekTo(0)
                    currentPlayer.playWhenReady = true
                    return
                }

                val next: Int? = if (shuffleEnabled) {
                    if (tracks.size == 1) playingTrackIndex
                    else {
                        var r = kotlin.random.Random.nextInt(tracks.size)
                        while (r == playingTrackIndex) r = kotlin.random.Random.nextInt(tracks.size)
                        r
                    }
                } else when {
                    playingTrackIndex + 1 < tracks.size -> playingTrackIndex + 1
                    repeatMode == 1 -> 0
                    else -> null
                }

                if (next != null) {
                    if (next == playingTrackIndex) {
                        currentPlayer.seekTo(0)
                        currentPlayer.playWhenReady = true
                    } else {
                        playingTrackIndex = next
                        selectedIndex = next
                    }
                }
            }
        }
        currentPlayer.addListener(listener)
        onDispose { currentPlayer.removeListener(listener) }
    }

    fun updateSelectionAnimationSpeed(stepCount: Int) {
        val now = android.os.SystemClock.uptimeMillis()
        if (lastWheelScrollAt > 0L) {
            val elapsedMs = (now - lastWheelScrollAt).coerceAtLeast(1L)
            val steps = kotlin.math.abs(stepCount).coerceAtLeast(1)
            val millisPerStep = elapsedMs.toFloat() / steps
            selectionAnimationDurationMs = if (millisPerStep >= 60_000f / 85f) {
                (millisPerStep * 0.35f).toInt().coerceIn(220, 420)
            } else {
                0
            }
        }
        lastWheelScrollAt = now
    }

    fun handleScroll(stepCount: Int) {
        if (showDirectoryBrowser) {
            if (isLoadingFolders) return
            val newIndex =
                (selectedDirectoryIndex + stepCount).coerceIn(0, currentDirectoryFolders.size + 1)
            val actualStepCount = newIndex - selectedDirectoryIndex
            if (actualStepCount != 0) {
                updateSelectionAnimationSpeed(actualStepCount)
                selectedDirectoryIndex = newIndex
            }
            return
        }

        if (tracks.isEmpty()) return

        val newIndex = (selectedIndex + stepCount).coerceIn(0, tracks.size - 1)
        if (newIndex != selectedIndex) {
            updateSelectionAnimationSpeed(newIndex - selectedIndex)
            selectedIndex = newIndex
        }
    }

    fun handlePlayPause() {
        if (showDirectoryBrowser) {
            if (currentPlayer.currentMediaItem == null) return
            isPlaying = !isPlaying
            currentPlayer.playWhenReady = isPlaying
            if (isPlaying) currentPlayer.play() else currentPlayer.pause()
            return
        }

        val track = tracks.getOrNull(selectedIndex)
        if (track == null) {
            if (isPlaying) {
                isPlaying = false
                currentPlayer.pause()
            }
            return
        }

        val currentUri = currentPlayer.currentMediaItem?.localConfiguration?.uri?.toString()
        playingTrackIndex = selectedIndex
        if (currentUri != track.uri) {
            currentPlayer.setMediaItem(track.toMediaItem())
            currentPlayer.prepare()
            isPlaying = true
            currentPlayer.playWhenReady = true
            currentPlayer.play()
        } else {
            isPlaying = !isPlaying
            currentPlayer.playWhenReady = isPlaying
            if (isPlaying) currentPlayer.play() else currentPlayer.pause()
        }
    }

    fun handleTrackSkip(stepCount: Int) {
        if (showDirectoryBrowser || listMode || tracks.isEmpty()) {
            handleScroll(stepCount)
            return
        }

        val baseIndex = playingTrackIndex.takeIf { it in tracks.indices } ?: selectedIndex
        val newIndex = (baseIndex + stepCount).coerceIn(0, tracks.lastIndex)
        selectedIndex = newIndex
        if (isPlaying) {
            playingTrackIndex = newIndex
        }
    }

    fun handleDirectoryConfirm() {
        if (isLoadingFolders) return
        when (selectedDirectoryIndex) {
            0 -> {
                val selectedFolderPath = directoryBrowsePath
                    ?.takeUnless { it == directoryRootPath }
                currentFolderPath = selectedFolderPath
                if (selectedFolderPath == null) {
                    prefs.edit().remove("selected_music_folder_path").apply()
                } else {
                    prefs.edit().putString("selected_music_folder_path", selectedFolderPath).apply()
                }
                showDirectoryBrowser = false
                listMode = true
            }
            in 1..currentDirectoryFolders.size -> {
                directoryBrowsePath = currentDirectoryFolders[selectedDirectoryIndex - 1].path
                selectedDirectoryIndex = 0
            }
            else -> onFolderSelect()
        }
    }

    fun navigateDirectoryBack() {
        val rootPath = directoryRootPath
        val currentPath = directoryBrowsePath
        if (rootPath == null || currentPath == null || currentPath == rootPath) {
            showDirectoryBrowser = false
            return
        }

        val parentPath = File(currentPath).parent
        directoryBrowsePath = when {
            parentPath == null -> rootPath
            rootPath == File.separator || parentPath == rootPath ||
                parentPath.startsWith("${rootPath.trimEnd('/')}/") -> parentPath
            else -> rootPath
        }
        selectedDirectoryIndex = 0
    }

    fun leaveTrackList() {
        if (playingTrackIndex in tracks.indices) {
            selectedIndex = playingTrackIndex
        }
        listMode = false
    }

    fun handleWheelConfirm() {
        if (showDirectoryBrowser) {
            handleDirectoryConfirm()
        } else if (listMode) {
            leaveTrackList()
        }
    }

    val backPressedDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    var backPressedCallback by remember { mutableStateOf<OnBackPressedCallback?>(null) }

    fun handleBackNavigation() {
        when {
            showVinyl -> showVinyl = false
            showThemeSettings -> showThemeSettings = false
            showScaleSettings -> showScaleSettings = false
            showSettings -> showSettings = false
            showEffectsMenu -> showEffectsMenu = false
            showDirectoryBrowser -> navigateDirectoryBack()
            listMode -> leaveTrackList()
            else -> {
                backPressedCallback?.isEnabled = false
                backPressedDispatcher?.onBackPressed()
            }
        }
    }

    fun handleMenuButton() {
        when {
            showVinyl -> showVinyl = false
            showThemeSettings -> showThemeSettings = false
            showScaleSettings -> showScaleSettings = false
            showSettings -> showSettings = false
            showEffectsMenu -> showEffectsMenu = false
            showDirectoryBrowser -> navigateDirectoryBack()
            listMode -> leaveTrackList()
            else -> listMode = true
        }
    }

    DisposableEffect(backPressedDispatcher) {
        val callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = handleBackNavigation()
        }
        backPressedCallback = callback
        backPressedDispatcher?.addCallback(callback)
        onDispose {
            if (backPressedCallback === callback) backPressedCallback = null
            callback.remove()
        }
    }

    // Скринсейвер при воспроизведении: через 10 секунд бездействия
    LaunchedEffect(
        screensaverEnabled, isPlaying, lastInteraction,
        showVinyl, showSettings, showScaleSettings, showThemeSettings, showEffectsMenu, showDirectoryBrowser
    ) {
        if (!screensaverEnabled || !isPlaying) return@LaunchedEffect
        if (showVinyl || showSettings || showScaleSettings || showThemeSettings || showEffectsMenu || showDirectoryBrowser) {
            return@LaunchedEffect
        }
        delay(10_000)
        if (System.currentTimeMillis() - lastInteraction >= 9_500) {
            showVinyl = true
        }
    }

    fun handleListButton() {
        when {
            showVinyl -> showVinyl = false
            showDirectoryBrowser -> showDirectoryBrowser = false
            listMode -> {
                leaveTrackList()
                if (screensaverEnabled) showVinyl = true
            }
            else -> listMode = true
        }
    }

    // Скринсейвер доступен только при включённой настройке
    LaunchedEffect(screensaverEnabled) {
        if (!screensaverEnabled) showVinyl = false
    }

    val activePage = when {
        showEffectsMenu -> PlayerPage.EFFECTS
        showThemeSettings -> PlayerPage.THEMES
        showScaleSettings -> PlayerPage.SCALE
        showSettings -> PlayerPage.SETTINGS
        else -> PlayerPage.PLAYER
    }

    CompositionLocalProvider(LocalPlayerLanguage provides language) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    lastInteraction = System.currentTimeMillis()
                    do {
                        val event = awaitPointerEvent()
                        if (!event.changes.any { it.pressed }) break
                    } while (true)
                    lastInteraction = System.currentTimeMillis()
                }
            }
    ) {
        AnimatedContent(
            targetState = activePage,
            modifier = Modifier.fillMaxSize(),
            transitionSpec = {
                (fadeIn(tween(280)) + slideInVertically(tween(280)) { it / 20 }) togetherWith
                    (fadeOut(tween(220)) + slideOutVertically(tween(220)) { -it / 28 })
            },
            label = "player-page"
        ) { page ->
        when (page) {
            PlayerPage.EFFECTS -> EffectsMenu(
                effectsManager = effectsManager,
                useEffects = useEffects,
                onUseEffectsChange = {
                    useEffects = it
                    prefs.edit().putBoolean("use_effects", it).apply()
                },
                onBack = { showEffectsMenu = false }
            )
            PlayerPage.THEMES -> ThemeSettingsScreen(
                themeColors = themeColors,
                builtInPresets = BuiltInThemePresets,
                savedPresets = savedThemePresets,
                onApplyPreset = { preset ->
                    onThemeColorsChange(ThemeColors(preset.light, preset.dark))
                },
                onColorChange = { isDark, field, color ->
                    val updated = if (isDark) {
                        themeColors.copy(dark = field.set(themeColors.dark, color))
                    } else {
                        themeColors.copy(light = field.set(themeColors.light, color))
                    }
                    onThemeColorsChange(updated)
                },
                onSavePreset = { name ->
                    val saved = savedThemePresets + ThemePreset(
                        id = UUID.randomUUID().toString(),
                        name = name,
                        light = themeColors.light,
                        dark = themeColors.dark
                    )
                    savedThemePresets = saved
                    prefs.edit().putString("saved_theme_presets_json", encodeThemePresetList(saved)).apply()
                },
                onDeletePreset = { preset ->
                    val saved = savedThemePresets.filterNot { it.id == preset.id }
                    savedThemePresets = saved
                    prefs.edit().putString("saved_theme_presets_json", encodeThemePresetList(saved)).apply()
                },
                onImportPreset = { themeImportPicker.launch(arrayOf("application/json", "text/json", "application/octet-stream")) },
                onExportPreset = exportThemePreset,
                onBack = { showThemeSettings = false }
            )
            PlayerPage.SCALE -> ScaleSettingsScreen(
                customScaleEnabled = customScaleEnabled,
                onCustomScaleEnabledChange = {
                    customScaleEnabled = it
                    prefs.edit().putBoolean("player_custom_scale_enabled", it).apply()
                },
                portraitScales = portraitElementScales,
                landscapeScales = landscapeElementScales,
                onScaleChange = { isLandscape, element, scale ->
                    val orientationKey = if (isLandscape) "landscape" else "portrait"
                    prefs.edit().putFloat(playerScalePreferenceKey(orientationKey, element), scale).apply()
                    if (isLandscape) {
                        landscapeElementScales = landscapeElementScales + (element to scale)
                    } else {
                        portraitElementScales = portraitElementScales + (element to scale)
                    }
                },
                onReset = { isLandscape ->
                    val orientationKey = if (isLandscape) "landscape" else "portrait"
                    val editor = prefs.edit()
                    PlayerScaleElement.entries.forEach { element ->
                        editor.putFloat(playerScalePreferenceKey(orientationKey, element), 1f)
                    }
                    editor.apply()
                    if (isLandscape) {
                        landscapeElementScales = defaultPlayerScales()
                    } else {
                        portraitElementScales = defaultPlayerScales()
                    }
                },
                onBack = { showScaleSettings = false }
            )
            PlayerPage.SETTINGS -> SettingsScreen(
                darkTheme = darkTheme,
                onToggleTheme = onToggleTheme,
                screensaverEnabled = screensaverEnabled,
                onToggleScreensaver = {
                    screensaverEnabled = it
                    prefs.edit().putBoolean("screensaver_enabled", it).apply()
                },
                easterEggsEnabled = easterEggsEnabled,
                onToggleEasterEggs = {
                    easterEggsEnabled = it
                    prefs.edit().putBoolean("easter_eggs_enabled", it).apply()
                },
                wheelVibrationEnabled = wheelVibrationEnabled,
                onToggleWheelVibration = {
                    wheelVibrationEnabled = it
                    prefs.edit().putBoolean("clickwheel_vibration_enabled", it).apply()
                },
                wheelClickSoundEnabled = wheelClickSoundEnabled,
                onToggleWheelClickSound = {
                    wheelClickSoundEnabled = it
                    prefs.edit().putBoolean("clickwheel_sound_enabled", it).apply()
                },
                wheelClickSoundUri = wheelClickSoundUri,
                onSelectWheelSound = { wheelClickSoundPicker.launch(arrayOf("audio/*")) },
                onClearWheelSound = {
                    wheelClickSoundUri?.let { soundUri ->
                        runCatching {
                            context.contentResolver.releasePersistableUriPermission(
                                Uri.parse(soundUri),
                                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                            )
                        }
                    }
                    wheelClickSoundUri = null
                    prefs.edit().remove("clickwheel_sound_uri").apply()
                },
                vinylRotationSpeed = vinylRotationSpeed,
                onVinylRotationSpeedChange = {
                    vinylRotationSpeed = it
                    prefs.edit().putFloat("vinyl_rotation_speed", it).apply()
                },
                vinylBpmSyncEnabled = vinylBpmSyncEnabled,
                onVinylBpmSyncChange = {
                    vinylBpmSyncEnabled = it
                    prefs.edit().putBoolean("vinyl_bpm_sync_enabled", it).apply()
                },
                language = language,
                onLanguageChange = onLanguageChange,
                onOpenThemeSettings = { showThemeSettings = true },
                onOpenScaleSettings = { showScaleSettings = true },
                onBack = { showSettings = false }
            )
            PlayerPage.PLAYER -> NowPlayingScreen(
                tracks = tracks,
                folders = currentDirectoryFolders,
                directoryBrowsePath = directoryBrowsePath,
                directoryRootPath = directoryRootPath,
                currentDirectoryTrackCount = currentDirectoryTrackCount,
                selectedIndex = selectedIndex,
                playingTrackIndex = playingTrackIndex,
                selectedDirectoryIndex = selectedDirectoryIndex,
                directoryBrowserOpen = showDirectoryBrowser,
                isLoadingFolders = isLoadingFolders,
                currentPlayer = currentPlayer,
                isPlaying = isPlaying,
                shuffleEnabled = shuffleEnabled,
                repeatMode = repeatMode,
                listMode = listMode,
                selectionAnimationDurationMs = selectionAnimationDurationMs,
                wheelVibrationEnabled = wheelVibrationEnabled,
                wheelClickSoundEnabled = wheelClickSoundEnabled,
                wheelClickSoundUri = wheelClickSoundUri,
                customScaleEnabled = customScaleEnabled,
                portraitElementScales = portraitElementScales,
                landscapeElementScales = landscapeElementScales,
                vinylRotationSpeed = currentVinylRotationSpeed,
                onCycleRepeat = { repeatMode = (repeatMode + 1) % 3 },
                onPlayPause = { handlePlayPause() },
                onMenuClick = { handleMenuButton() },
                onConfirm = { handleWheelConfirm() },
                onToggleShuffle = { shuffleEnabled = !shuffleEnabled },
                onTrackPlay = { index ->
                    selectionAnimationDurationMs = 280
                    lastWheelScrollAt = 0L
                    selectedIndex = index
                },
                onPreviousTrack = { handleTrackSkip(-1) },
                onNextTrack = { handleTrackSkip(1) },
                onScroll = { stepCount -> handleScroll(stepCount) },
                onListButton = { handleListButton() },
                onOpenEffects = { showEffectsMenu = true },
                onOpenSettings = { showSettings = true },
                onFolderSelect = {
                    showDirectoryBrowser = true
                    listMode = true
                },
                onDirectorySelectionIndexChange = {
                    selectionAnimationDurationMs = 280
                    lastWheelScrollAt = 0L
                    selectedDirectoryIndex = it
                },
                easterEggsEnabled = easterEggsEnabled
            )
        }
        }

        AnimatedVisibility(
            visible = showVinyl,
            enter = fadeIn(tween(320)) + scaleIn(initialScale = 0.96f, animationSpec = tween(320)),
            exit = fadeOut(tween(240)) + scaleOut(targetScale = 0.98f, animationSpec = tween(240)),
            label = "vinyl-screensaver"
        ) {
            VinylScreensaver(
                track = tracks.getOrNull(playingTrackIndex)
                    ?: tracks.getOrNull(selectedIndex),
                isPlaying = isPlaying || currentPlayer.isPlaying,
                onExit = { showVinyl = false },
                scratchEnabled = easterEggsEnabled,
                rotationSpeed = currentVinylRotationSpeed,
                onScratch = { deltaSeconds ->
                    val duration = currentPlayer.duration.takeIf { it > 0 } ?: 0L
                    if (duration > 0) {
                        val newPos = (currentPlayer.currentPosition + (deltaSeconds * 1000).toLong())
                            .coerceIn(0L, duration)
                        currentPlayer.seekTo(newPos)
                    }
                }
            )
        }
    }
    }
}

// =============== Экран "Сейчас играет" ===============

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NowPlayingScreen(
    tracks: List<AudioTrack>,
    folders: List<AudioFolder>,
    directoryBrowsePath: String?,
    directoryRootPath: String?,
    currentDirectoryTrackCount: Int,
    selectedIndex: Int,
    playingTrackIndex: Int,
    selectedDirectoryIndex: Int,
    directoryBrowserOpen: Boolean,
    isLoadingFolders: Boolean,
    currentPlayer: Player,
    isPlaying: Boolean,
    shuffleEnabled: Boolean,
    repeatMode: Int,
    listMode: Boolean,
    selectionAnimationDurationMs: Int,
    wheelVibrationEnabled: Boolean,
    wheelClickSoundEnabled: Boolean,
    wheelClickSoundUri: String?,
    customScaleEnabled: Boolean,
    portraitElementScales: Map<PlayerScaleElement, Float>,
    landscapeElementScales: Map<PlayerScaleElement, Float>,
    vinylRotationSpeed: Float,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onPlayPause: () -> Unit,
    onMenuClick: () -> Unit,
    onConfirm: () -> Unit,
    onTrackPlay: (Int) -> Unit,
    onPreviousTrack: () -> Unit,
    onNextTrack: () -> Unit,
    onScroll: (Int) -> Unit,
    onListButton: () -> Unit,
    onOpenEffects: () -> Unit,
    onOpenSettings: () -> Unit,
    onFolderSelect: () -> Unit,
    onDirectorySelectionIndexChange: (Int) -> Unit,
    easterEggsEnabled: Boolean
) {
    val palette = LocalPlayerPalette.current
    val track = tracks.getOrNull(playingTrackIndex) ?: tracks.getOrNull(selectedIndex)
    val isLandscape =
        LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val iconRotation = if (isLandscape) 90f else 0f
    val orientationScales = if (isLandscape) landscapeElementScales else portraitElementScales
    fun scaleOf(element: PlayerScaleElement) =
        if (customScaleEnabled) orientationScales[element] ?: 1f else 1f

    val onScratch: (Float) -> Unit = remember(currentPlayer) {
        { deltaSeconds ->
            val duration = currentPlayer.duration.takeIf { it > 0 } ?: 0L
            if (duration > 0) {
                val newPosition = (currentPlayer.currentPosition + (deltaSeconds * 1000).toLong())
                    .coerceIn(0L, duration)
                currentPlayer.seekTo(newPosition)
            }
        }
    }

    val body: @Composable () -> Unit = {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(palette.background)
                .padding(horizontal = 20.dp)
        ) {
        if (isLandscape) {
            // Ландшафт: единый блок экрана (как в 1.2)
            UprightContainer(
                upright = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(2f)
                    .background(palette.discPanel, RoundedCornerShape(10.dp))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 10.dp, vertical = 10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        AnimatedContent(
                            targetState = listMode,
                            transitionSpec = {
                                (fadeIn(tween(240)) + scaleIn(initialScale = 0.96f, animationSpec = tween(260))) togetherWith
                                    (fadeOut(tween(190)) + scaleOut(targetScale = 0.98f, animationSpec = tween(190)))
                            },
                            label = "landscape-cover-list"
                        ) { showingList ->
                            if (showingList) {
                                if (directoryBrowserOpen) {
                                    DirectorySelectionPanel(
                                        folders = folders,
                                        isRoot = directoryBrowsePath == directoryRootPath,
                                        currentFolderTrackCount = currentDirectoryTrackCount,
                                        selectedIndex = selectedDirectoryIndex,
                                        isLoading = isLoadingFolders,
                                        landscape = true,
                                        animationDurationMs = selectionAnimationDurationMs,
                                        elementScale = scaleOf(PlayerScaleElement.TRACK_LIST),
                                        onSelectionChange = onDirectorySelectionIndexChange
                                    )
                                } else {
                                    TrackSelectionList(
                                        tracks = tracks,
                                        selectedIndex = selectedIndex,
                                        onTrackPlay = onTrackPlay,
                                        landscape = true,
                                        animationDurationMs = selectionAnimationDurationMs,
                                        elementScale = scaleOf(PlayerScaleElement.TRACK_LIST)
                                    )
                                }
                            } else {
                                DiscArt(
                                    isPlaying = isPlaying,
                                    scratchEnabled = easterEggsEnabled,
                                    artworkUri = track?.albumArtUri,
                                    onScratch = onScratch,
                                    rotationSpeed = vinylRotationSpeed,
                                    elementScale = scaleOf(PlayerScaleElement.COVER)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    LandscapeTrackInfo(
                        track = track,
                        currentPlayer = currentPlayer,
                        isPlaying = isPlaying,
                        shuffleEnabled = shuffleEnabled,
                        repeatMode = repeatMode,
                        trackInfoScale = scaleOf(PlayerScaleElement.TRACK_INFO),
                        progressScale = scaleOf(PlayerScaleElement.PROGRESS),
                        iconRotation = iconRotation,
                        onToggleShuffle = onToggleShuffle,
                        onCycleRepeat = onCycleRepeat
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        } else {
            // Портрет: раздельная структура (как в 1.1)
            // Область диска / выбор трека колесом
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1.4f)
                    .background(
                        if (listMode) palette.surface else palette.discPanel,
                        RoundedCornerShape(10.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                AnimatedContent(
                    targetState = listMode,
                    transitionSpec = {
                        (fadeIn(tween(240)) + scaleIn(initialScale = 0.96f, animationSpec = tween(260))) togetherWith
                            (fadeOut(tween(190)) + scaleOut(targetScale = 0.98f, animationSpec = tween(190)))
                    },
                    label = "portrait-cover-list"
                ) { showingList ->
                    if (showingList) {
                        if (directoryBrowserOpen) {
                            DirectorySelectionPanel(
                                folders = folders,
                                isRoot = directoryBrowsePath == directoryRootPath,
                                currentFolderTrackCount = currentDirectoryTrackCount,
                                selectedIndex = selectedDirectoryIndex,
                                isLoading = isLoadingFolders,
                                landscape = false,
                                animationDurationMs = selectionAnimationDurationMs,
                                elementScale = scaleOf(PlayerScaleElement.TRACK_LIST),
                                onSelectionChange = onDirectorySelectionIndexChange
                            )
                        } else {
                            TrackSelectionList(
                                tracks = tracks,
                                selectedIndex = selectedIndex,
                                onTrackPlay = onTrackPlay,
                                landscape = false,
                                animationDurationMs = selectionAnimationDurationMs,
                                elementScale = scaleOf(PlayerScaleElement.TRACK_LIST)
                            )
                        }
                    } else {
                        DiscArt(
                            isPlaying = isPlaying,
                            scratchEnabled = easterEggsEnabled,
                            artworkUri = track?.albumArtUri,
                            onScratch = onScratch,
                            rotationSpeed = vinylRotationSpeed,
                            elementScale = scaleOf(PlayerScaleElement.COVER)
                        )
                    }
                }
            }

            PortraitTrackInfo(
                track = track,
                currentPlayer = currentPlayer,
                isPlaying = isPlaying,
                shuffleEnabled = shuffleEnabled,
                repeatMode = repeatMode,
                trackInfoScale = scaleOf(PlayerScaleElement.TRACK_INFO),
                progressScale = scaleOf(PlayerScaleElement.PROGRESS),
                onToggleShuffle = onToggleShuffle,
                onCycleRepeat = onCycleRepeat
            )
        }

        PlayerControlArea(
            isPlaying = isPlaying && (directoryBrowserOpen || selectedIndex == playingTrackIndex),
            iconRotation = iconRotation,
            listMode = listMode,
            directoryBrowserOpen = directoryBrowserOpen,
            wheelVibrationEnabled = wheelVibrationEnabled,
            wheelClickSoundEnabled = wheelClickSoundEnabled,
            wheelClickSoundUri = wheelClickSoundUri,
            wheelScale = scaleOf(PlayerScaleElement.CLICK_WHEEL),
            actionButtonsScale = scaleOf(PlayerScaleElement.ACTION_BUTTONS),
            weight = if (isLandscape) 2.5f else 1.3f,
            onScroll = onScroll,
            onPlayPause = onPlayPause,
            onMenuClick = onMenuClick,
            onConfirm = onConfirm,
            onPreviousTrack = onPreviousTrack,
            onNextTrack = onNextTrack,
            onListButton = onListButton,
            onOpenEffects = onOpenEffects,
            onFolderSelect = onFolderSelect,
            onOpenSettings = onOpenSettings
        )

        Spacer(modifier = Modifier.height(16.dp))
    }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(8.dp)
    ) {
        val scale = minOf(
            1f,
            if (isLandscape) maxHeight / 360.dp else maxWidth / 360.dp,
            if (isLandscape) maxWidth / 640.dp else maxHeight / 640.dp
        ).coerceAtLeast(0.1f)
        val contentWidth = if (isLandscape) maxHeight else maxWidth
        val contentHeight = if (isLandscape) maxWidth else maxHeight

        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .requiredSize(
                        width = contentWidth / scale,
                        height = contentHeight / scale
                    )
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        if (isLandscape) rotationZ = -90f
                    }
            ) {
                body()
            }
        }
    }
}

@Composable
private fun LandscapeTrackInfo(
    track: AudioTrack?,
    currentPlayer: Player,
    isPlaying: Boolean,
    shuffleEnabled: Boolean,
    repeatMode: Int,
    trackInfoScale: Float,
    progressScale: Float,
    iconRotation: Float,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit
) {
    val palette = LocalPlayerPalette.current
    val trackLabel = track?.let { "${it.title} - ${it.artist}" } ?: localized("Нет треков", "No tracks")
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(palette.chipBackground, RoundedCornerShape(8.dp))
            .border(1.dp, palette.discRim, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    scaleX = trackInfoScale
                    scaleY = trackInfoScale
                },
            verticalAlignment = Alignment.CenterVertically
        ) {
            PlayerToggleIconButton(
                image = Icons.Default.Shuffle,
                description = localized("Перемешивание", "Shuffle"),
                selected = shuffleEnabled,
                selectedTint = palette.chipText,
                inactiveTint = palette.chipText.copy(alpha = 0.38f),
                iconRotation = iconRotation,
                onClick = onToggleShuffle
            )

            AnimatedContent(
                targetState = trackLabel,
                modifier = Modifier.weight(1f),
                transitionSpec = {
                    (fadeIn(tween(220)) + slideInVertically(tween(220)) { it / 4 }) togetherWith
                        (fadeOut(tween(160)) + slideOutVertically(tween(160)) { -it / 4 })
                },
                label = "landscape-track-label"
            ) { label ->
                Text(
                    text = label,
                    color = palette.chipText,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE)
                )
            }

            PlayerToggleIconButton(
                image = if (repeatMode == 2) Icons.Default.RepeatOne else Icons.Default.Repeat,
                description = localized("Повтор", "Repeat"),
                selected = repeatMode > 0,
                selectedTint = palette.chipText,
                inactiveTint = palette.chipText.copy(alpha = 0.38f),
                iconRotation = iconRotation,
                onClick = onCycleRepeat
            )
        }

        PlaybackProgress(
            currentPlayer = currentPlayer,
            isPlaying = isPlaying,
            fallbackDuration = track?.duration ?: 0L,
            thumbColor = palette.chipText,
            activeTrackColor = palette.chipText,
            inactiveTrackColor = Color.White.copy(alpha = 0.22f),
            timeColor = palette.chipText,
            scale = progressScale
        )
    }
}

@Composable
private fun PortraitTrackInfo(
    track: AudioTrack?,
    currentPlayer: Player,
    isPlaying: Boolean,
    shuffleEnabled: Boolean,
    repeatMode: Int,
    trackInfoScale: Float,
    progressScale: Float,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit
) {
    val palette = LocalPlayerPalette.current
    val trackLabel = track?.let { "${it.title} - ${it.artist}" } ?: localized("Нет треков", "No tracks")
    val brightText = if (palette.text.luminance() >= palette.textSecondary.luminance()) {
        palette.text
    } else {
        palette.textSecondary
    }
    val dimText = if (brightText == palette.text) palette.textSecondary else palette.text

    Spacer(modifier = Modifier.height(12.dp))

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = trackInfoScale
                scaleY = trackInfoScale
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        PlayerToggleIconButton(
            image = Icons.Default.Shuffle,
            description = localized("Перемешивание", "Shuffle"),
            selected = shuffleEnabled,
            selectedTint = brightText,
            inactiveTint = dimText,
            onClick = onToggleShuffle
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .background(palette.chipBackground, RoundedCornerShape(4.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            AnimatedContent(
                targetState = trackLabel,
                transitionSpec = {
                    (fadeIn(tween(220)) + slideInVertically(tween(220)) { it / 4 }) togetherWith
                        (fadeOut(tween(160)) + slideOutVertically(tween(160)) { -it / 4 })
                },
                label = "portrait-track-label"
            ) { label ->
                Text(
                    text = label,
                    color = palette.chipText,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                    modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE)
                )
            }
        }

        PlayerToggleIconButton(
            image = if (repeatMode == 2) Icons.Default.RepeatOne else Icons.Default.Repeat,
            description = localized("Повтор", "Repeat"),
            selected = repeatMode > 0,
            selectedTint = brightText,
            inactiveTint = dimText,
            onClick = onCycleRepeat
        )
    }

    Spacer(modifier = Modifier.height(4.dp))

    PlaybackProgress(
        currentPlayer = currentPlayer,
        isPlaying = isPlaying,
        fallbackDuration = track?.duration ?: 0L,
        thumbColor = palette.progressActive,
        activeTrackColor = palette.progressActive,
        inactiveTrackColor = palette.progressTrack,
        timeColor = palette.text,
        scale = progressScale
    )

    Spacer(modifier = Modifier.height(16.dp))
}

@Composable
private fun PlaybackProgress(
    currentPlayer: Player,
    isPlaying: Boolean,
    fallbackDuration: Long,
    thumbColor: Color,
    activeTrackColor: Color,
    inactiveTrackColor: Color,
    timeColor: Color,
    scale: Float = 1f
) {
    var currentPosition by remember(currentPlayer) {
        mutableLongStateOf(currentPlayer.currentPosition)
    }
    var duration by remember(currentPlayer, fallbackDuration) {
        mutableLongStateOf(currentPlayer.duration.takeIf { it > 0 } ?: fallbackDuration)
    }
    var isDraggingSlider by remember { mutableStateOf(false) }
    var dragProgress by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(currentPlayer, isPlaying, fallbackDuration) {
        while (true) {
            val playerPosition = currentPlayer.currentPosition
            val playerDuration = currentPlayer.duration.takeIf { it > 0 } ?: fallbackDuration
            if (currentPosition != playerPosition) currentPosition = playerPosition
            if (duration != playerDuration) duration = playerDuration
            delay(if (isPlaying) 100 else 1_000)
        }
    }

    val progress = if (duration > 0) {
        (currentPosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    val smoothProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(140, easing = FastOutSlowInEasing),
        label = "playback-progress"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
    ) {
        Slider(
            value = if (isDraggingSlider) dragProgress else smoothProgress,
            onValueChange = {
                isDraggingSlider = true
                dragProgress = it
            },
            onValueChangeFinished = {
                if (duration > 0) {
                    currentPlayer.seekTo((dragProgress * duration).toLong())
                    currentPosition = (dragProgress * duration).toLong()
                }
                isDraggingSlider = false
            },
            enabled = duration > 0,
            colors = SliderDefaults.colors(
                thumbColor = thumbColor,
                activeTrackColor = activeTrackColor,
                inactiveTrackColor = inactiveTrackColor
            )
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                formatTime(currentPosition),
                color = timeColor,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
            Text(
                formatTime(duration),
                color = timeColor,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
        }
    }
}

@Composable
private fun TrackSelectionList(
    tracks: List<AudioTrack>,
    selectedIndex: Int,
    onTrackPlay: (Int) -> Unit,
    landscape: Boolean,
    animationDurationMs: Int,
    elementScale: Float = 1f
) {
    val palette = LocalPlayerPalette.current
    val listState = rememberLazyListState()

    LaunchedEffect(selectedIndex, tracks, animationDurationMs) {
        if (selectedIndex in tracks.indices) {
            val viewportHeight = listState.layoutInfo.viewportEndOffset -
                listState.layoutInfo.viewportStartOffset
            val offset = -(viewportHeight / 2 - 60).coerceAtLeast(0)
            if (animationDurationMs == 0) {
                val isSelectedItemVisible = listState.layoutInfo.visibleItemsInfo
                    .any { it.index == selectedIndex }
                if (!isSelectedItemVisible) {
                    listState.scrollToItem(selectedIndex, offset)
                }
                return@LaunchedEffect
            }
            listState.animateScrollToItem(selectedIndex, offset)
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                scaleX = elementScale
                scaleY = elementScale
            }
            .then(
                if (landscape) {
                    Modifier
                        .background(palette.background, RoundedCornerShape(8.dp))
                        .border(1.dp, palette.divider, RoundedCornerShape(8.dp))
                } else {
                    Modifier
                }
            )
            .padding(vertical = 6.dp),
        contentPadding = PaddingValues(horizontal = 6.dp)
    ) {
        itemsIndexed(tracks, key = { index, track -> "${track.uri}_$index" }) { index, track ->
            TrackRow(
                track = track,
                isSelected = index == selectedIndex,
                animationDurationMs = animationDurationMs,
                onClick = { onTrackPlay(index) }
            )
        }
    }
}

@Composable
private fun ColumnScope.PlayerControlArea(
    isPlaying: Boolean,
    iconRotation: Float,
    listMode: Boolean,
    directoryBrowserOpen: Boolean,
    wheelVibrationEnabled: Boolean,
    wheelClickSoundEnabled: Boolean,
    wheelClickSoundUri: String?,
    wheelScale: Float,
    actionButtonsScale: Float,
    weight: Float,
    onScroll: (Int) -> Unit,
    onPlayPause: () -> Unit,
    onMenuClick: () -> Unit,
    onConfirm: () -> Unit,
    onPreviousTrack: () -> Unit,
    onNextTrack: () -> Unit,
    onListButton: () -> Unit,
    onOpenEffects: () -> Unit,
    onFolderSelect: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val palette = LocalPlayerPalette.current
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .weight(weight)
    ) {
        val menuButtonSize = minOf(56.dp, maxWidth * 0.18f) * actionButtonsScale
        val topControlBand = 40.dp * actionButtonsScale
        val footerHeight = 30.dp * actionButtonsScale
        val wheelSize = minOf(
            300.dp * wheelScale,
            maxWidth,
            (maxHeight - topControlBand - footerHeight).coerceAtLeast(0.dp)
        )

        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = topControlBand, bottom = footerHeight),
                contentAlignment = Alignment.Center
            ) {
                ClickWheel(
                    isPlaying = isPlaying,
                    directoryBrowserOpen = directoryBrowserOpen,
                    wheelSize = wheelSize,
                    wheelVibrationEnabled = wheelVibrationEnabled,
                    wheelClickSoundEnabled = wheelClickSoundEnabled,
                    wheelClickSoundUri = wheelClickSoundUri,
                    iconRotation = iconRotation,
                    onScroll = onScroll,
                    onConfirm = onConfirm,
                    onMenuClick = onMenuClick,
                    onPlayPause = onPlayPause,
                    onPreviousTrack = onPreviousTrack,
                    onNextTrack = onNextTrack
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(topControlBand)
                    .padding(horizontal = 12.dp)
                    .align(Alignment.TopCenter),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SideCircleButton(
                    icon = if (listMode) Icons.Default.Close else Icons.AutoMirrored.Filled.List,
                    description = if (listMode) localized("Выйти из списка", "Exit list") else localized("Список треков", "Track list"),
                    iconRotation = iconRotation,
                    buttonSize = menuButtonSize,
                    modifier = Modifier.requiredSize(menuButtonSize),
                    onClick = onListButton
                )

                SideCircleButton(
                    icon = Icons.Default.Tune,
                    description = localized("Эффекты", "Effects"),
                    iconRotation = iconRotation,
                    buttonSize = menuButtonSize,
                    modifier = Modifier.requiredSize(menuButtonSize),
                    onClick = onOpenEffects
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(footerHeight)
                    .align(Alignment.BottomCenter),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onFolderSelect,
                    modifier = Modifier.requiredSize(48.dp * actionButtonsScale)
                ) {
                    Icon(
                        Icons.Default.Folder,
                        localized("Выбрать папку", "Choose folder"),
                        tint = palette.textSecondary,
                        modifier = Modifier.graphicsLayer {
                            rotationZ = iconRotation
                            scaleX = actionButtonsScale
                            scaleY = actionButtonsScale
                        }
                    )
                }

                IconButton(
                    onClick = onOpenSettings,
                    modifier = Modifier.requiredSize(48.dp * actionButtonsScale)
                ) {
                    Icon(
                        Icons.Default.Settings,
                        localized("Настройки", "Settings"),
                        tint = palette.textSecondary,
                        modifier = Modifier.graphicsLayer {
                            rotationZ = iconRotation
                            scaleX = actionButtonsScale
                            scaleY = actionButtonsScale
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun UprightContainer(
    upright: Boolean,
    modifier: Modifier = Modifier,
    contentAlignment: Alignment = Alignment.TopStart,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier,
        contentAlignment = contentAlignment
    ) {
        if (upright) {
            BoxWithConstraints(modifier = Modifier.matchParentSize()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .requiredSize(width = maxHeight, height = maxWidth)
                        .graphicsLayer { rotationZ = 90f },
                    contentAlignment = contentAlignment
                ) {
                    content()
                }
            }
        } else {
            content()
        }
    }
}

@Composable
private fun SideCircleButton(
    icon: ImageVector,
    description: String,
    iconRotation: Float = 0f,
    buttonSize: Dp = 72.dp,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val palette = LocalPlayerPalette.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 650f),
        label = "side-button-press"
    )
    Box(
        modifier = modifier
            .size(buttonSize)
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .shadow(3.dp, CircleShape)
            .background(palette.sideButton, CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current
            ) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            description,
            tint = palette.sideButtonIcon,
            modifier = Modifier
                .size(buttonSize * 0.42f)
                .graphicsLayer { rotationZ = iconRotation }
        )
    }
}

@Composable
private fun DiscArt(
    isPlaying: Boolean,
    scratchEnabled: Boolean = false,
    artworkUri: String? = null,
    onScratch: ((deltaSeconds: Float) -> Unit)? = null,
    elementScale: Float = 1f,
    rotationSpeed: Float = 1f
) {
    val palette = LocalPlayerPalette.current
    val rotation = remember { Animatable(0f) }
    val artwork = rememberAlbumArtwork(artworkUri)
    val scope = rememberCoroutineScope()

    // Состояние для скретча
    var isScratching by remember { mutableStateOf(false) }
    var isSettling by remember { mutableStateOf(false) }
    var lastAngle by remember { mutableFloatStateOf(0f) }
    var scratchVelocity by remember { mutableFloatStateOf(0f) }
    var scratchRotation by remember { mutableFloatStateOf(0f) }
    val scratchSettleJob = remember { AtomicReference<Job?>(null) }

    // Автоматическое вращение (только если не скретчим)
    LaunchedEffect(isPlaying, isScratching, isSettling, rotationSpeed) {
        if (isPlaying && !isScratching && !isSettling) {
            while (true) {
                rotation.animateTo(
                    rotation.value + 360f,
                    animationSpec = tween(
                        (3000f / rotationSpeed.coerceAtLeast(0.1f)).toInt().coerceAtLeast(100),
                        easing = LinearEasing
                    )
                )
            }
        }
    }

    // Обработка скретча
    val scratchModifier = if (scratchEnabled) {
        Modifier.pointerInput(Unit) {
            detectDragGestures(
                onDragStart = { offset ->
                    scratchSettleJob.getAndSet(null)?.cancel()
                    isSettling = false
                    isScratching = true
                    scratchRotation = rotation.value
                    lastAngle = calculateAngle(offset, Offset(size.width / 2f, size.height / 2f))
                    scratchVelocity = 0f
                },
                onDrag = { change, _ ->
                    val currentAngle = calculateAngle(
                        change.position,
                        Offset(size.width / 2f, size.height / 2f)
                    )
                    var deltaAngle = currentAngle - lastAngle
                    if (deltaAngle > 180f) deltaAngle -= 360f
                    if (deltaAngle < -180f) deltaAngle += 360f

                    lastAngle = currentAngle
                    scratchVelocity = deltaAngle

                    // 360° = 10 секунд
                    val deltaSeconds = deltaAngle / 36f
                    onScratch?.invoke(deltaSeconds)

                    scratchRotation += deltaAngle
                },
                onDragEnd = {
                    val finalRotation = scratchRotation
                    val finalVelocity = scratchVelocity
                    isSettling = true
                    scratchSettleJob.set(scope.launch {
                        rotation.snapTo(finalRotation)
                        isScratching = false
                        if (kotlin.math.abs(finalVelocity) > 5f) {
                            rotation.animateTo(
                                finalRotation + finalVelocity * 3f,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessLow
                                )
                            )
                        }
                        isSettling = false
                        scratchSettleJob.set(null)
                    })
                }
            )
        }
    } else {
        Modifier
    }

    Box(
        modifier = Modifier
            .fillMaxWidth((0.62f * elementScale).coerceIn(0.3f, 0.9f))
            .aspectRatio(1f)
            .graphicsLayer { rotationZ = if (isScratching) scratchRotation else rotation.value }
            .then(scratchModifier),
        contentAlignment = Alignment.Center
    ) {
        artwork?.let {
            Image(
                bitmap = it,
                contentDescription = localized("Обложка трека", "Track album art"),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
            )
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
            val r = min(size.width, size.height) / 2f

            if (artwork == null) {
                drawCircle(
                    color = palette.discBody,
                    radius = r,
                    center = center
                )
            }
            drawCircle(
                color = palette.discRim,
                radius = r - 1.dp.toPx(),
                center = center,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
            )
            drawCircle(
                color = palette.discRim.copy(alpha = 0.5f),
                radius = r * 0.72f,
                center = center,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx())
            )
            drawCircle(
                color = palette.discRim.copy(alpha = 0.35f),
                radius = r * 0.45f,
                center = center,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx())
            )
            rotate(0f, pivot = center) {
                drawLine(
                    color = palette.discRim.copy(alpha = 0.6f),
                    start = Offset(center.x, center.y - r * 0.86f),
                    end = Offset(center.x, center.y - r * 0.52f),
                    strokeWidth = 1.5.dp.toPx()
                )
            }
            drawCircle(color = palette.discHole, radius = r * 0.16f, center = center)
            drawCircle(
                color = palette.discHoleInner,
                radius = r * 0.07f,
                center = center
            )
        }
    }
}

// =============== ClickWheel (Play не вращается) ===============

@SuppressLint("RestrictedApi")
@Composable
fun ClickWheel(
    isPlaying: Boolean = false,
    directoryBrowserOpen: Boolean = false,
    wheelSize: Dp = 300.dp,
    wheelVibrationEnabled: Boolean = true,
    wheelClickSoundEnabled: Boolean = true,
    wheelClickSoundUri: String? = null,
    iconRotation: Float = 0f,
    onScroll: (Int) -> Unit,
    onConfirm: () -> Unit,
    onMenuClick: () -> Unit,
    onPlayPause: () -> Unit,
    onPreviousTrack: () -> Unit,
    onNextTrack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val palette = LocalPlayerPalette.current
    var wheelRotation by remember { mutableFloatStateOf(0f) }
    val coroutineScope = rememberCoroutineScope()
    val wheelScale = (wheelSize / 300.dp).coerceIn(0.45f, 1.5f)
    val clickToneGenerator = remember(context) {
        runCatching { ToneGenerator(AudioManager.STREAM_SYSTEM, 70) }.getOrNull()
    }
    val soundPool = remember(wheelClickSoundUri) {
        if (wheelClickSoundUri == null) null else SoundPool.Builder()
            .setMaxStreams(1)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .build()
    }
    var customSoundId by remember(soundPool) { mutableIntStateOf(0) }
    val currentOnScroll by rememberUpdatedState(onScroll)
    val currentClickSoundEnabled by rememberUpdatedState(wheelClickSoundEnabled)
    val currentSoundPool by rememberUpdatedState(soundPool)
    val currentCustomSoundId by rememberUpdatedState(customSoundId)

    DisposableEffect(clickToneGenerator) {
        onDispose { clickToneGenerator?.release() }
    }

    DisposableEffect(soundPool, wheelClickSoundUri) {
        if (soundPool != null && wheelClickSoundUri != null) {
            soundPool.setOnLoadCompleteListener { _, sampleId, status ->
                customSoundId = if (status == 0) sampleId else 0
            }
            runCatching {
                context.contentResolver.openAssetFileDescriptor(Uri.parse(wheelClickSoundUri), "r")
                    ?.use { descriptor ->
                        customSoundId = soundPool.load(
                            descriptor.fileDescriptor,
                            descriptor.startOffset,
                            descriptor.length,
                            1
                        )
                    }
            }
        }
        onDispose {
            soundPool?.setOnLoadCompleteListener(null)
            soundPool?.release()
        }
    }

    val currentVibrationEnabled by rememberUpdatedState(wheelVibrationEnabled)
    val vibrator = remember(context) {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                manager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
        }.getOrNull()
    }

    val vibrate = remember(vibrator) {
        {
            if (currentVibrationEnabled && vibrator != null) {
                try {
                    if (Build.VERSION_CODES.O <= Build.VERSION.SDK_INT) {
                        vibrator.vibrate(VibrationEffect.createOneShot(10, VibrationEffect.DEFAULT_AMPLITUDE))
                    } else {
                        @Suppress("DEPRECATION")
                        vibrator.vibrate(10)
                    }
                } catch (_: Exception) {
                    // ignore
                }
            }
        }
    }
    val currentVibrate by rememberUpdatedState(vibrate)

    Box(
        modifier = modifier
            .size(wheelSize)
            .shadow(6.dp, CircleShape)
            .background(palette.wheel, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        // Вращающийся слой: только разметка кольца, без кнопок
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(wheelVibrationEnabled, wheelClickSoundEnabled) {
                    val center = Offset((size.width / 2).toFloat(), (size.height / 2).toFloat())
                    var lastAngle = 0f
                    var accumulatedRotation = 0f
                    var lastReportedStep = 0
                    var returnAnimationJob: Job? = null
                    val pendingWheelSteps = ArrayDeque<Int>()
                    var pendingStepJob: Job? = null

                    fun enqueueWheelSteps(stepDelta: Int) {
                        val direction = if (stepDelta > 0) 1 else -1
                        repeat(kotlin.math.abs(stepDelta)) {
                            pendingWheelSteps.addLast(direction)
                        }
                        if (pendingStepJob?.isActive == true) return

                        pendingStepJob = coroutineScope.launch {
                            while (pendingWheelSteps.isNotEmpty()) {
                                currentOnScroll(pendingWheelSteps.removeFirst())
                                if (currentClickSoundEnabled) {
                                    if (currentCustomSoundId != 0) {
                                        currentSoundPool?.play(currentCustomSoundId, 1f, 1f, 1, 0, 1f)
                                    } else {
                                        clickToneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP2, 24)
                                    }
                                }
                                currentVibrate()
                                if (pendingWheelSteps.isNotEmpty()) delay(50)
                            }
                            pendingStepJob = null
                        }
                    }

                    detectDragGestures(
                        onDragStart = { offset ->
                            returnAnimationJob?.cancel()
                            lastAngle = calculateAngle(offset, center)
                            accumulatedRotation = 0f
                            lastReportedStep = 0
                        },
                        onDrag = { change, _ ->
                            val currentAngle = calculateAngle(change.position, center)

                            var deltaAngle = currentAngle - lastAngle
                            if (deltaAngle > 180f) deltaAngle -= 360f
                            if (deltaAngle < -180f) deltaAngle += 360f

                            accumulatedRotation += deltaAngle
                            lastAngle = currentAngle

                            val stepSize = 30f
                            val currentStep = (accumulatedRotation / stepSize).toInt()

                            if (currentStep != lastReportedStep) {
                                val diff = currentStep - lastReportedStep
                                enqueueWheelSteps(diff)
                                lastReportedStep = currentStep
                            }

                            wheelRotation = accumulatedRotation * 1.5f
                        },
                        onDragEnd = {
                            val startRotation = wheelRotation
                            returnAnimationJob = coroutineScope.launch {
                                animate(
                                    initialValue = startRotation,
                                    targetValue = 0f,
                                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
                                ) { value, _ ->
                                    wheelRotation = value
                                }
                            }
                        }
                    )
                }
                .graphicsLayer { rotationZ = wheelRotation }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val radius = size.minDimension / 2
                for (i in 0 until 12) {
                    val angle = Math.toRadians((i * 30).toDouble())
                    val x = center.x + (radius * 0.94f * cos(angle)).toFloat()
                    val y = center.y + (radius * 0.94f * sin(angle)).toFloat()
                    drawCircle(
                        color = palette.wheelEdge,
                        radius = 4f,
                        center = Offset(x, y)
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 22.dp * wheelScale)
                .size(48.dp * wheelScale)
                .clickable {
                    currentVibrate()
                    onMenuClick()
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Menu,
                contentDescription = localized("Меню", "Menu"),
                tint = palette.wheelIcon,
                modifier = Modifier
                    .size(38.dp * wheelScale)
                    .graphicsLayer { rotationZ = iconRotation }
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 22.dp * wheelScale)
                .size(48.dp * wheelScale)
                .clickable {
                    currentVibrate()
                    onPlayPause()
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (isPlaying) localized("Пауза", "Pause") else localized("Воспроизвести", "Play"),
                tint = palette.wheelIcon,
                modifier = Modifier
                    .size(38.dp * wheelScale)
                    .graphicsLayer { rotationZ = iconRotation }
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 22.dp * wheelScale)
                .size(48.dp * wheelScale)
                .clickable {
                    currentVibrate()
                    onPreviousTrack()
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.SkipPrevious,
                contentDescription = localized("Предыдущий трек", "Previous track"),
                tint = palette.wheelIcon,
                modifier = Modifier
                    .size(38.dp * wheelScale)
                    .graphicsLayer { rotationZ = iconRotation }
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 22.dp * wheelScale)
                .size(48.dp * wheelScale)
                .clickable {
                    currentVibrate()
                    onNextTrack()
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.SkipNext,
                contentDescription = localized("Следующий трек", "Next track"),
                tint = palette.wheelIcon,
                modifier = Modifier
                    .size(38.dp * wheelScale)
                    .graphicsLayer { rotationZ = iconRotation }
            )
        }

        // Центральная кнопка подтверждения — статична
        Box(
            modifier = Modifier
                .size(88.dp * wheelScale)
                .background(palette.centerButton, CircleShape)
                .clickable {
                    currentVibrate()
                    onConfirm()
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = if (directoryBrowserOpen) localized("Выбрать папку", "Select folder") else "OK",
                tint = palette.centerIcon,
                modifier = Modifier
                    .size(40.dp * wheelScale)
                    .graphicsLayer { rotationZ = iconRotation }
            )
        }
    }
}

// =============== Строка трека ===============

@Composable
fun TrackRow(
    track: AudioTrack,
    isSelected: Boolean,
    onClick: () -> Unit,
    animationDurationMs: Int = 200
) {
    val palette = LocalPlayerPalette.current
    val transitionDuration = animationDurationMs.coerceIn(0, 420)
    val rowBackground by animateColorAsState(
        targetValue = if (isSelected) palette.listRowSelected else Color.Transparent,
        animationSpec = if (transitionDuration == 0) snap() else tween(transitionDuration),
        label = "track-row-selection"
    )
    val selectionProgress by animateFloatAsState(
        targetValue = if (isSelected) 1f else 0f,
        animationSpec = if (transitionDuration == 0) {
            snap()
        } else {
            tween(transitionDuration, easing = FastOutSlowInEasing)
        },
        label = "track-row-indicator"
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .background(rowBackground)
            .clickable { onClick() }
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.width(20.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Icon(
                Icons.Default.PlayArrow,
                null,
                tint = palette.text,
                modifier = Modifier
                    .size(14.dp)
                    .graphicsLayer {
                        alpha = selectionProgress
                        scaleX = 0.75f + selectionProgress * 0.25f
                        scaleY = 0.75f + selectionProgress * 0.25f
                    }
            )
        }
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = "${track.title} - ${track.artist}",
            fontSize = 13.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = palette.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = formatTime(track.duration),
            fontSize = 11.sp,
            color = palette.textSecondary
        )
    }
}

@Composable
private fun DirectorySelectionPanel(
    folders: List<AudioFolder>,
    isRoot: Boolean,
    currentFolderTrackCount: Int,
    selectedIndex: Int,
    isLoading: Boolean,
    landscape: Boolean,
    animationDurationMs: Int,
    elementScale: Float = 1f,
    onSelectionChange: (Int) -> Unit
) {
    val palette = LocalPlayerPalette.current
    val listState = rememberLazyListState()
    val itemCount = folders.size + 2

    LaunchedEffect(selectedIndex, folders, isLoading, animationDurationMs) {
        if (!isLoading) {
            val viewportHeight = listState.layoutInfo.viewportEndOffset -
                listState.layoutInfo.viewportStartOffset
            val offset = -(viewportHeight / 2 - 20).coerceAtLeast(0)
            val targetIndex = selectedIndex.coerceIn(0, itemCount - 1)
            if (animationDurationMs == 0) {
                val isSelectedItemVisible = listState.layoutInfo.visibleItemsInfo
                    .any { it.index == targetIndex }
                if (!isSelectedItemVisible) {
                    listState.scrollToItem(targetIndex, offset)
                }
                return@LaunchedEffect
            }
            listState.animateScrollToItem(targetIndex, offset)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                scaleX = elementScale
                scaleY = elementScale
            }
            .then(
                if (landscape) {
                    Modifier
                        .background(palette.background, RoundedCornerShape(8.dp))
                        .border(1.dp, palette.divider, RoundedCornerShape(8.dp))
                } else {
                    Modifier
                }
            )
    ) {
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = palette.wheelIcon,
                    strokeWidth = 2.dp
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = 6.dp),
                contentPadding = PaddingValues(horizontal = 6.dp)
            ) {
                item(key = "all-audio-folders") {
                    DirectoryRow(
                        title = if (isRoot) localized("Все песни", "All songs") else localized("Выбрать эту папку", "Choose this folder"),
                        detail = currentFolderTrackCount.toString(),
                        isSelected = selectedIndex == 0,
                        showFolderIcon = !isRoot,
                        animationDurationMs = animationDurationMs,
                        onClick = { onSelectionChange(0) }
                    )
                }
                itemsIndexed(folders, key = { _, folder -> folder.path }) { index, folder ->
                    DirectoryRow(
                        title = folder.name,
                        detail = folder.trackCount.toString(),
                        isSelected = selectedIndex == index + 1,
                        showFolderIcon = true,
                        animationDurationMs = animationDurationMs,
                        onClick = { onSelectionChange(index + 1) }
                    )
                }
                item(key = "browse-device-folder") {
                    DirectoryRow(
                        title = localized("Другие папки…", "More folders…"),
                        detail = "",
                        isSelected = selectedIndex == itemCount - 1,
                        showFolderIcon = true,
                        animationDurationMs = animationDurationMs,
                        onClick = { onSelectionChange(itemCount - 1) }
                    )
                }
            }
        }
    }
}

@Composable
private fun DirectoryRow(
    title: String,
    detail: String,
    isSelected: Boolean,
    showFolderIcon: Boolean,
    animationDurationMs: Int,
    onClick: () -> Unit
) {
    val palette = LocalPlayerPalette.current
    val transitionDuration = animationDurationMs.coerceIn(0, 420)
    val rowBackground by animateColorAsState(
        targetValue = if (isSelected) palette.listRowSelected else Color.Transparent,
        animationSpec = if (transitionDuration == 0) snap() else tween(transitionDuration),
        label = "folder-row-selection"
    )
    val selectionProgress by animateFloatAsState(
        targetValue = if (isSelected) 1f else 0f,
        animationSpec = if (transitionDuration == 0) {
            snap()
        } else {
            tween(transitionDuration, easing = FastOutSlowInEasing)
        },
        label = "folder-row-indicator"
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .background(rowBackground)
            .clickable { onClick() }
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.width(20.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Icon(
                Icons.Default.PlayArrow,
                contentDescription = null,
                tint = palette.text,
                modifier = Modifier
                    .size(14.dp)
                    .graphicsLayer {
                        alpha = selectionProgress
                        scaleX = 0.75f + selectionProgress * 0.25f
                        scaleY = 0.75f + selectionProgress * 0.25f
                    }
            )
        }
        Spacer(modifier = Modifier.width(6.dp))
        if (showFolderIcon) {
            Icon(
                imageVector = Icons.Default.Folder,
                contentDescription = null,
                tint = palette.textSecondary,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
        }
        Text(
            text = title,
            fontSize = 13.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = palette.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (detail.isNotEmpty()) {
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = detail,
                fontSize = 11.sp,
                color = palette.textSecondary
            )
        }
    }
}

// =============== Меню эффектов ===============

@OptIn(UnstableApi::class)
@Composable
fun EffectsMenu(
    effectsManager: EffectsManager,
    useEffects: Boolean,
    onUseEffectsChange: (Boolean) -> Unit,
    onBack: () -> Unit
) {
    val palette = LocalPlayerPalette.current

    var wowEnabled by remember { mutableStateOf(effectsManager.wowFlutter.enabled) }
    var wowDepth by remember { mutableFloatStateOf(effectsManager.wowFlutter.depth) }
    var wowRate by remember { mutableFloatStateOf(effectsManager.wowFlutter.rate) }

    var detonationEnabled by remember { mutableStateOf(effectsManager.volumeDetonation.enabled) }
    var detonationAmount by remember { mutableFloatStateOf(effectsManager.volumeDetonation.amount) }

    var chorusEnabled by remember { mutableStateOf(effectsManager.chorus.enabled) }
    var chorusDepth by remember { mutableFloatStateOf(effectsManager.chorus.depth) }
    var chorusRate by remember { mutableFloatStateOf(effectsManager.chorus.rate) }
    var chorusMix by remember { mutableFloatStateOf(effectsManager.chorus.mix) }

    var noiseEnabled by remember { mutableStateOf(effectsManager.vintageNoise.enabled) }
    var noiseLevel by remember { mutableFloatStateOf(effectsManager.vintageNoise.noiseLevel) }
    var crackleIntensity by remember { mutableFloatStateOf(effectsManager.vintageNoise.crackleIntensity) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
                .padding(bottom = 72.dp)
        ) {
            Text(
                text = localized("Эффекты плёнки", "Tape effects"),
                style = MaterialTheme.typography.headlineMedium,
                color = palette.text,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            EffectCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = localized("Включить эффекты", "Enable effects"),
                            style = MaterialTheme.typography.titleMedium,
                            color = palette.text
                        )
                        Text(
                            text = localized("Переключает режим воспроизведения", "Enables audio processing effects"),
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.textSecondary
                        )
                    }
                    Switch(
                        checked = useEffects,
                        onCheckedChange = onUseEffectsChange,
                        colors = playerSwitchColors(palette)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            EffectCard(enabled = useEffects) {
                EffectHeader(
                    title = "Wow & Flutter",
                    subtitle = localized("Неравномерность скорости воспроизведения", "Playback speed fluctuations"),
                    checked = wowEnabled,
                    onCheckedChange = {
                        wowEnabled = it
                        effectsManager.wowFlutter.enabled = it
                    }
                )
                LabeledSlider(
                    label = localized("Глубина", "Depth"),
                    value = wowDepth,
                    range = 0f..1f,
                    onValueChange = {
                        wowDepth = it
                        effectsManager.wowFlutter.depth = it
                    }
                )
                LabeledSlider(
                    label = localized("Частота, Гц", "Rate, Hz"),
                    value = wowRate,
                    range = 0.1f..5f,
                    onValueChange = {
                        wowRate = it
                        effectsManager.wowFlutter.rate = it
                    }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            EffectCard(enabled = useEffects) {
                EffectHeader(
                    title = "Volume Detonation",
                    subtitle = localized("Перегрузка при высокой амплитуде", "Saturation at high amplitudes"),
                    checked = detonationEnabled,
                    onCheckedChange = {
                        detonationEnabled = it
                        effectsManager.volumeDetonation.enabled = it
                    }
                )
                LabeledSlider(
                    label = localized("Степень", "Amount"),
                    value = detonationAmount,
                    range = 0f..1f,
                    onValueChange = {
                        detonationAmount = it
                        effectsManager.volumeDetonation.amount = it
                    }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            EffectCard(enabled = useEffects) {
                EffectHeader(
                    title = "Chorus",
                    subtitle = localized("Эффект хора", "Chorus effect"),
                    checked = chorusEnabled,
                    onCheckedChange = {
                        chorusEnabled = it
                        effectsManager.chorus.enabled = it
                    }
                )
                LabeledSlider(
                    label = localized("Глубина", "Depth"),
                    value = chorusDepth,
                    range = 0f..1f,
                    onValueChange = {
                        chorusDepth = it
                        effectsManager.chorus.depth = it
                    }
                )
                LabeledSlider(
                    label = localized("Частота, Гц", "Rate, Hz"),
                    value = chorusRate,
                    range = 0.1f..5f,
                    onValueChange = {
                        chorusRate = it
                        effectsManager.chorus.rate = it
                    }
                )
                LabeledSlider(
                    label = localized("Микс", "Mix"),
                    value = chorusMix,
                    range = 0f..1f,
                    onValueChange = {
                        chorusMix = it
                        effectsManager.chorus.mix = it
                    }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            EffectCard(enabled = useEffects) {
                EffectHeader(
                    title = "Vintage Noise",
                    subtitle = localized("Шум и хруст винила", "Vinyl noise and crackle"),
                    checked = noiseEnabled,
                    onCheckedChange = {
                        noiseEnabled = it
                        effectsManager.vintageNoise.enabled = it
                    }
                )
                LabeledSlider(
                    label = localized("Уровень шума", "Noise level"),
                    value = noiseLevel,
                    range = 0f..1f,
                    onValueChange = {
                        noiseLevel = it
                        effectsManager.vintageNoise.noiseLevel = it
                    }
                )
                LabeledSlider(
                    label = localized("Хруст", "Crackle"),
                    value = crackleIntensity,
                    range = 0f..1f,
                    onValueChange = {
                        crackleIntensity = it
                        effectsManager.vintageNoise.crackleIntensity = it
                    }
                )
            }
        }

        IconButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp)
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                localized("Назад", "Back"),
                tint = palette.textSecondary
            )
        }
    }
}

@Composable
private fun EffectCard(
    enabled: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    val palette = LocalPlayerPalette.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = palette.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
                .graphicsLayer { alpha = if (enabled) 1f else 0.4f }
        ) {
            content()
        }
    }
}

@Composable
private fun EffectHeader(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val palette = LocalPlayerPalette.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = palette.text
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = palette.textSecondary
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = playerSwitchColors(palette)
        )
    }
}

@Composable
private fun LabeledSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {
    val palette = LocalPlayerPalette.current
    Text(
        "$label: ${"%.2f".format(value)}",
        style = MaterialTheme.typography.bodySmall,
        color = palette.textSecondary
    )
    Slider(
        value = value,
        onValueChange = onValueChange,
        valueRange = range,
        colors = SliderDefaults.colors(
            thumbColor = palette.progressActive,
            activeTrackColor = palette.progressActive,
            inactiveTrackColor = palette.progressTrack
        )
    )
}


// =============== Настройки ===============

@Composable
private fun PlayerSettingSwitch(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val palette = LocalPlayerPalette.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = palette.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = palette.text
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.textSecondary
                )
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = playerSwitchColors(palette)
            )
        }
    }
}

@Composable
private fun playerSwitchColors(palette: PlayerPalette) = SwitchDefaults.colors(
    checkedTrackColor = mixColors(palette.background, Color.White, 0.22f),
    checkedThumbColor = listOf(palette.background, palette.text, palette.textSecondary)
        .minBy { it.luminance() },
    uncheckedThumbColor = palette.textSecondary,
    uncheckedTrackColor = mixColors(palette.background, Color.Black, 0.12f)
)

@Composable
fun SettingsScreen(
    darkTheme: Boolean,
    onToggleTheme: () -> Unit,
    screensaverEnabled: Boolean,
    onToggleScreensaver: (Boolean) -> Unit,
    easterEggsEnabled: Boolean,
    onToggleEasterEggs: (Boolean) -> Unit,
    wheelVibrationEnabled: Boolean,
    onToggleWheelVibration: (Boolean) -> Unit,
    wheelClickSoundEnabled: Boolean,
    onToggleWheelClickSound: (Boolean) -> Unit,
    wheelClickSoundUri: String?,
    onSelectWheelSound: () -> Unit,
    onClearWheelSound: () -> Unit,
    vinylRotationSpeed: Float,
    onVinylRotationSpeedChange: (Float) -> Unit,
    vinylBpmSyncEnabled: Boolean,
    onVinylBpmSyncChange: (Boolean) -> Unit,
    language: String,
    onLanguageChange: (String) -> Unit,
    onOpenThemeSettings: () -> Unit,
    onOpenScaleSettings: () -> Unit,
    onBack: () -> Unit
) {
    val palette = LocalPlayerPalette.current
    val context = LocalContext.current
    val wheelSoundName = remember(wheelClickSoundUri) {
        wheelClickSoundUri?.let { uri ->
            runCatching {
                context.contentResolver.query(
                    Uri.parse(uri),
                    arrayOf(OpenableColumns.DISPLAY_NAME),
                    null,
                    null,
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
                    } else null
                }
            }.getOrNull()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
                .padding(bottom = 72.dp)
        ) {
            Text(
                text = localized("Настройки", "Settings"),
                style = MaterialTheme.typography.headlineMedium,
                color = palette.text,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = palette.surface)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (darkTheme) localized("Тёмная тема", "Dark theme") else localized("Светлая тема", "Light theme"),
                                style = MaterialTheme.typography.titleMedium,
                                color = palette.text
                            )
                            Text(
                                text = localized("Оформление интерфейса приложения", "Application appearance"),
                                style = MaterialTheme.typography.bodySmall,
                                color = palette.textSecondary
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.LightMode,
                                null,
                                tint = if (!darkTheme) palette.wheelIcon else palette.textSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Switch(
                                checked = darkTheme,
                                onCheckedChange = { onToggleTheme() },
                                colors = playerSwitchColors(palette)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                Icons.Default.DarkMode,
                                null,
                                tint = if (darkTheme) palette.wheelIcon else palette.textSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenThemeSettings),
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = palette.surface)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            localized("Цветовая тема", "Color theme"),
                            style = MaterialTheme.typography.titleMedium,
                            color = palette.text
                        )
                        Text(
                            localized("Цвета элементов, готовые и сохранённые пресеты", "Element colors, built-in and saved presets"),
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.textSecondary
                        )
                    }
                    TextButton(onClick = onOpenThemeSettings) {
                        Text(localized("Настроить", "Customize"))
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = palette.surface)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        localized("Язык интерфейса", "Interface language"),
                        style = MaterialTheme.typography.titleMedium,
                        color = palette.text
                    )
                    Row {
                        Button(
                            onClick = { onLanguageChange("ru") },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (language == "ru") palette.progressActive else palette.surface,
                                contentColor = if (language == "ru") MaterialTheme.colorScheme.onPrimary else palette.text
                            )
                        ) {
                            Text("Русский")
                        }
                        Spacer(Modifier.width(6.dp))
                        Button(
                            onClick = { onLanguageChange("en") },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (language == "en") palette.progressActive else palette.surface,
                                contentColor = if (language == "en") MaterialTheme.colorScheme.onPrimary else palette.text
                            )
                        ) {
                            Text("English")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            PlayerSettingSwitch(
                title = localized("Вибрация Click Wheel", "Click Wheel vibration"),
                description = localized("Отклик при прокрутке и нажатии кнопок колеса", "Haptic feedback while scrolling and pressing wheel buttons"),
                checked = wheelVibrationEnabled,
                onCheckedChange = onToggleWheelVibration
            )

            Spacer(modifier = Modifier.height(12.dp))

            PlayerSettingSwitch(
                title = localized("Звук щелчка", "Click sound"),
                description = localized("Звуковой отклик при прокрутке колеса", "Sound feedback while scrolling the wheel"),
                checked = wheelClickSoundEnabled,
                onCheckedChange = onToggleWheelClickSound
            )

            Spacer(modifier = Modifier.height(12.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = palette.surface)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp)
                ) {
                    Text(
                        text = localized("Пользовательский звук щелчка", "Custom click sound"),
                        style = MaterialTheme.typography.titleMedium,
                        color = palette.text
                    )
                    Text(
                        text = wheelSoundName ?: localized("Выберите короткий аудиофайл для прокрутки", "Choose a short audio clip for scrolling"),
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.textSecondary,
                        modifier = Modifier.padding(top = 4.dp, bottom = 10.dp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(onClick = onSelectWheelSound) {
                            Text(if (wheelClickSoundUri == null) localized("Выбрать звук", "Choose sound") else localized("Заменить", "Replace"))
                        }
                        if (wheelClickSoundUri != null) {
                            TextButton(onClick = onClearWheelSound) {
                                Text(localized("Сбросить", "Clear"), color = palette.textSecondary)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = palette.surface)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = localized("Скорость вращения винила: ${"%.2f".format(vinylRotationSpeed)}×", "Vinyl rotation speed: ${"%.2f".format(vinylRotationSpeed)}×"),
                        style = MaterialTheme.typography.titleMedium,
                        color = palette.text
                    )
                    Text(
                        text = localized("Ручная базовая скорость вращения обложки", "Manual base speed for the record artwork"),
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.textSecondary
                    )
                    Slider(
                        value = vinylRotationSpeed,
                        onValueChange = onVinylRotationSpeedChange,
                        valueRange = 0.25f..2.5f,
                        steps = 44,
                        colors = SliderDefaults.colors(
                            thumbColor = palette.progressActive,
                            activeTrackColor = palette.progressActive,
                            inactiveTrackColor = palette.progressTrack
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            PlayerSettingSwitch(
                title = localized("Скорость по BPM трека", "Sync speed to track BPM"),
                description = localized("Скорость меняется относительно 120 BPM; без BPM в тегах используется 120", "Speed follows track BPM relative to 120; tracks without a BPM tag use 120"),
                checked = vinylBpmSyncEnabled,
                onCheckedChange = onVinylBpmSyncChange
            )

            Spacer(modifier = Modifier.height(12.dp))

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenScaleSettings),
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = palette.surface)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            localized("Масштаб элементов плеера", "Player element scale"),
                            style = MaterialTheme.typography.titleMedium,
                            color = palette.text
                        )
                        Text(
                            localized("Отдельные настройки для портретного и альбомного режима", "Separate portrait and landscape settings"),
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.textSecondary
                        )
                    }
                    TextButton(onClick = onOpenScaleSettings) {
                        Text(localized("Настроить", "Customize"))
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = palette.surface)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = localized("Скринсейвер", "Screensaver"),
                                style = MaterialTheme.typography.titleMedium,
                                color = palette.text
                            )
                            Text(
                                text = localized("Винил появляется при воспроизведении музыки после 10 секунд бездействия", "Shows a spinning record after 10 seconds of playback inactivity"),
                                style = MaterialTheme.typography.bodySmall,
                                color = palette.textSecondary
                            )
                        }
                        Switch(
                            checked = screensaverEnabled,
                            onCheckedChange = onToggleScreensaver,
                            colors = playerSwitchColors(palette)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = palette.surface)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = localized("Режим пасхалок", "Easter egg mode"),
                                style = MaterialTheme.typography.titleMedium,
                                color = palette.text
                            )
                            Text(
                                text = localized("Виниловые пластинки становятся интерактивными — можно скретчить", "Records become interactive and can be scratched"),
                                style = MaterialTheme.typography.bodySmall,
                                color = palette.textSecondary
                            )
                        }
                        Switch(
                            checked = easterEggsEnabled,
                            onCheckedChange = onToggleEasterEggs,
                            colors = playerSwitchColors(palette)
                        )
                    }
                }
            }
        }

        IconButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp)
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                localized("Назад", "Back"),
                tint = palette.textSecondary
            )
        }
    }
}

@Composable
private fun ThemeSettingsScreen(
    themeColors: ThemeColors,
    builtInPresets: List<ThemePreset>,
    savedPresets: List<ThemePreset>,
    onApplyPreset: (ThemePreset) -> Unit,
    onColorChange: (isDark: Boolean, field: PaletteColorField, color: Color) -> Unit,
    onSavePreset: (String) -> Unit,
    onDeletePreset: (ThemePreset) -> Unit,
    onImportPreset: () -> Unit,
    onExportPreset: (ThemePreset) -> Unit,
    onBack: () -> Unit
) {
    val palette = LocalPlayerPalette.current
    val language = LocalPlayerLanguage.current
    var editingDarkTheme by rememberSaveable { mutableStateOf(true) }
    var showSaveDialog by remember { mutableStateOf(false) }
    var presetName by remember { mutableStateOf("") }
    var expandedColorField by remember { mutableStateOf<String?>(null) }
    val editingPalette = if (editingDarkTheme) themeColors.dark else themeColors.light

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
                .padding(bottom = 80.dp)
        ) {
            Text(
                localized("Цветовая тема", "Color theme"),
                style = MaterialTheme.typography.headlineMedium,
                color = palette.text,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onImportPreset) {
                    Text(localized("Импорт JSON", "Import JSON"))
                }
                Button(onClick = { showSaveDialog = true }) {
                    Text(localized("Сохранить пресет", "Save preset"))
                }
            }

            Spacer(Modifier.height(16.dp))
            Text(
                localized("Готовые пресеты", "Built-in presets"),
                style = MaterialTheme.typography.titleLarge,
                color = palette.text,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            builtInPresets.forEach { preset ->
                ThemePresetCard(
                    preset = preset,
                    language = language,
                    onApply = { onApplyPreset(preset) },
                    onExport = { onExportPreset(preset) },
                    onDelete = null
                )
            }

            Text(
                localized("Мои пресеты", "My presets"),
                style = MaterialTheme.typography.titleLarge,
                color = palette.text,
                modifier = Modifier.padding(top = 12.dp, bottom = 8.dp)
            )
            if (savedPresets.isEmpty()) {
                Text(
                    localized("Сохранённых тем пока нет", "No saved themes yet"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.textSecondary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            savedPresets.forEach { preset ->
                ThemePresetCard(
                    preset = preset,
                    language = language,
                    onApply = { onApplyPreset(preset) },
                    onExport = { onExportPreset(preset) },
                    onDelete = { onDeletePreset(preset) }
                )
            }

            Text(
                localized("Настройка цветов", "Customize colors"),
                style = MaterialTheme.typography.titleLarge,
                color = palette.text,
                modifier = Modifier.padding(top = 12.dp, bottom = 8.dp)
            )
            TabRow(selectedTabIndex = if (editingDarkTheme) 1 else 0) {
                Tab(
                    selected = !editingDarkTheme,
                    onClick = { editingDarkTheme = false },
                    text = { Text(localized("Светлая", "Light")) }
                )
                Tab(
                    selected = editingDarkTheme,
                    onClick = { editingDarkTheme = true },
                    text = { Text(localized("Тёмная", "Dark")) }
                )
            }
            paletteColorFields.forEach { field ->
                ThemeColorField(
                    color = field.get(editingPalette),
                    title = if (language == "en") field.titleEn else field.titleRu,
                    expanded = expandedColorField == field.key,
                    onExpandedChange = { expanded ->
                        expandedColorField = if (expanded) field.key else null
                    },
                    onColorChange = { onColorChange(editingDarkTheme, field, it) }
                )
            }
        }

        IconButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp)
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, localized("Назад", "Back"), tint = palette.textSecondary)
        }
    }

    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text(localized("Сохранить пресет", "Save preset")) },
            text = {
                OutlinedTextField(
                    value = presetName,
                    onValueChange = { presetName = it },
                    label = { Text(localized("Название темы", "Theme name")) },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    enabled = presetName.isNotBlank(),
                    onClick = {
                        onSavePreset(presetName.trim())
                        presetName = ""
                        showSaveDialog = false
                    }
                ) { Text(localized("Сохранить", "Save")) }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) {
                    Text(localized("Отмена", "Cancel"))
                }
            }
        )
    }
}

@Composable
private fun ThemePresetCard(
    preset: ThemePreset,
    language: String,
    onApply: () -> Unit,
    onExport: () -> Unit,
    onDelete: (() -> Unit)?
) {
    val palette = LocalPlayerPalette.current
    val name = when (preset.id) {
        "classic" -> if (language == "en") "Classic" else "Классический"
        "hacker_green" -> if (language == "en") "Hacker Green" else "Зелёный хакерский"
        "cyberpunk_violet" -> if (language == "en") "Neon Violet Cyberpunk" else "Неоновый фиолетовый киберпанк"
        "yellow_black" -> if (language == "en") "High-contrast Yellow & Black" else "Контрастный жёлто-чёрный"
        else -> preset.name
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .animateContentSize(tween(220)),
        colors = CardDefaults.cardColors(containerColor = palette.surface),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(26.dp).background(preset.light.background, CircleShape))
                Spacer(Modifier.width(6.dp))
                Box(Modifier.size(26.dp).background(preset.dark.background, CircleShape))
                Spacer(Modifier.width(10.dp))
                Text(
                    name,
                    color = palette.text,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = onApply,
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                ) {
                    Text(if (language == "en") "Choose" else "Выбрать")
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onExport) { Text(if (language == "en") "Export" else "Выгрузить") }
                if (onDelete != null) {
                    TextButton(onClick = onDelete) { Text(if (language == "en") "Delete" else "Удалить") }
                }
            }
        }
    }
}

@Composable
private fun ThemeColorField(
    color: Color,
    title: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onColorChange: (Color) -> Unit
) {
    val palette = LocalPlayerPalette.current
    val initialHsv = remember(color) {
        FloatArray(3).also { android.graphics.Color.colorToHSV(color.toArgb(), it) }
    }
    var hue by remember(color) { mutableFloatStateOf(initialHsv[0]) }
    var saturation by remember(color) { mutableFloatStateOf(initialHsv[1]) }
    var brightness by remember(color) { mutableFloatStateOf(initialHsv[2]) }
    var pickerColor by remember(color) { mutableStateOf(color) }
    val hueColor = remember(hue) {
        Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, 1f, 1f)))
    }
    val saturationHueBrush = remember(hueColor) {
        Brush.horizontalGradient(listOf(Color.White, hueColor))
    }
    val brightnessBrush = remember {
        Brush.verticalGradient(listOf(Color.Transparent, Color.Black))
    }
    val hueBrush = remember {
        Brush.horizontalGradient(
            listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red)
        )
    }
    fun colorAtCurrentSelection(): Color = Color(
        android.graphics.Color.HSVToColor(
            (color.alpha * 255).toInt(),
            floatArrayOf(hue, saturation, brightness)
        )
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .animateContentSize(tween(180)),
        colors = CardDefaults.cardColors(containerColor = palette.surface),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(pickerColor, RoundedCornerShape(6.dp))
                        .border(1.dp, palette.divider, RoundedCornerShape(6.dp))
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, color = palette.text, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = "#%08X".format(pickerColor.toArgb()),
                        color = palette.textSecondary,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                TextButton(onClick = { onExpandedChange(!expanded) }) {
                    Text(
                        if (expanded) localized("Готово", "Done")
                        else localized("Выбрать цвет", "Choose color")
                    )
                }
            }

            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn(tween(160)),
                exit = fadeOut(tween(120)),
                label = "color-picker"
            ) {
                Column(modifier = Modifier.padding(top = 10.dp)) {
                    Text(
                        localized("Насыщенность и яркость", "Saturation and brightness"),
                        color = palette.textSecondary,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .border(1.dp, palette.divider, RoundedCornerShape(10.dp))
                            .pointerInput(Unit) {
                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false)
                                    down.consume()

                                    fun updateSelection(position: Offset) {
                                        saturation = (position.x / size.width).coerceIn(0f, 1f)
                                        brightness = (1f - position.y / size.height).coerceIn(0f, 1f)
                                        pickerColor = colorAtCurrentSelection()
                                    }

                                    updateSelection(down.position)
                                    var pointerPressed = true
                                    while (pointerPressed) {
                                        val event = awaitPointerEvent()
                                        val change = event.changes.firstOrNull { it.id == down.id }
                                            ?: break
                                        updateSelection(change.position)
                                        change.consume()
                                        pointerPressed = change.pressed
                                    }
                                    onColorChange(pickerColor)
                                }
                            }
                    ) {
                        drawRect(
                            brush = saturationHueBrush,
                            size = size
                        )
                        drawRect(
                            brush = brightnessBrush,
                            size = size
                        )
                        val cursor = Offset(
                            saturation * size.width,
                            (1f - brightness) * size.height
                        )
                        drawCircle(
                            color = Color.Black,
                            radius = 10.dp.toPx(),
                            center = cursor,
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4.dp.toPx())
                        )
                        drawCircle(
                            color = Color.White,
                            radius = 10.dp.toPx(),
                            center = cursor,
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
                        )
                    }

                    Text(
                        localized("Оттенок", "Hue"),
                        color = palette.textSecondary,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(top = 10.dp, bottom = 6.dp)
                    )
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(28.dp)
                            .clip(CircleShape)
                            .pointerInput(Unit) {
                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false)
                                    down.consume()

                                    fun updateHue(position: Offset) {
                                        hue = (position.x / size.width * 360f).coerceIn(0f, 360f)
                                        pickerColor = colorAtCurrentSelection()
                                    }

                                    updateHue(down.position)
                                    var pointerPressed = true
                                    while (pointerPressed) {
                                        val event = awaitPointerEvent()
                                        val change = event.changes.firstOrNull { it.id == down.id }
                                            ?: break
                                        updateHue(change.position)
                                        change.consume()
                                        pointerPressed = change.pressed
                                    }
                                    onColorChange(pickerColor)
                                }
                            }
                    ) {
                        drawRoundRect(
                            brush = hueBrush,
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2f),
                            size = size
                        )
                        val cursorX = hue / 360f * size.width
                        drawCircle(
                            color = Color.Black,
                            radius = size.height * 0.42f,
                            center = Offset(cursorX, center.y),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx())
                        )
                        drawCircle(
                            color = Color.White,
                            radius = size.height * 0.42f,
                            center = Offset(cursorX, center.y),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5.dp.toPx())
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ScaleSettingsScreen(
    customScaleEnabled: Boolean,
    onCustomScaleEnabledChange: (Boolean) -> Unit,
    portraitScales: Map<PlayerScaleElement, Float>,
    landscapeScales: Map<PlayerScaleElement, Float>,
    onScaleChange: (isLandscape: Boolean, element: PlayerScaleElement, scale: Float) -> Unit,
    onReset: (isLandscape: Boolean) -> Unit,
    onBack: () -> Unit
) {
    val palette = LocalPlayerPalette.current
    val language = LocalPlayerLanguage.current
    var selectedOrientationIndex by rememberSaveable { mutableIntStateOf(0) }
    val isLandscape = selectedOrientationIndex == 1
    val scales = if (isLandscape) landscapeScales else portraitScales

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
                .padding(bottom = 80.dp)
        ) {
            Text(
                text = localized("Масштаб элементов", "Element scale"),
                style = MaterialTheme.typography.headlineMedium,
                color = palette.text,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            PlayerSettingSwitch(
                title = localized("Пользовательский масштаб", "Custom scale"),
                description = localized("Если выключить, интерфейс будет автоматически подстраиваться под экран", "When disabled, the interface automatically fits the screen"),
                checked = customScaleEnabled,
                onCheckedChange = onCustomScaleEnabledChange
            )

            Spacer(modifier = Modifier.height(12.dp))

            TabRow(selectedTabIndex = selectedOrientationIndex) {
                Tab(
                    selected = selectedOrientationIndex == 0,
                    onClick = { selectedOrientationIndex = 0 },
                    text = { Text(localized("Портрет", "Portrait")) }
                )
                Tab(
                    selected = selectedOrientationIndex == 1,
                    onClick = { selectedOrientationIndex = 1 },
                    text = { Text(localized("Альбом", "Landscape")) }
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = { onReset(isLandscape) }) {
                    Text(localized("Сбросить режим", "Reset orientation"))
                }
            }

            PlayerScaleElement.entries.forEach { element ->
                val scale = scales[element] ?: 1f
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = palette.surface)
                ) {
                    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                        Text(
                            text = "${if (language == "en") element.titleEn else element.titleRu} — ${(scale * 100).toInt()}%",
                            style = MaterialTheme.typography.titleSmall,
                            color = palette.text
                        )
                        Slider(
                            value = scale,
                            onValueChange = { onScaleChange(isLandscape, element, it) },
                            valueRange = 0.6f..1.4f,
                            steps = 15,
                            colors = SliderDefaults.colors(
                                thumbColor = palette.progressActive,
                                activeTrackColor = palette.progressActive,
                                inactiveTrackColor = palette.progressTrack
                            )
                        )
                    }
                }
            }
        }

        IconButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp)
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                localized("Назад", "Back"),
                tint = palette.textSecondary
            )
        }
    }
}

// =============== Скринсейвер с винилом ===============

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VinylScreensaver(
    track: AudioTrack?,
    isPlaying: Boolean,
    onExit: () -> Unit,
    scratchEnabled: Boolean = false,
    rotationSpeed: Float = 1f,
    onScratch: ((deltaSeconds: Float) -> Unit)? = null
) {
    val palette = LocalPlayerPalette.current
    val rotation = remember { Animatable(0f) }
    val artwork = rememberAlbumArtwork(track?.albumArtUri)

    // Состояние для скретча
    var isScratching by remember { mutableStateOf(false) }
    var isSettling by remember { mutableStateOf(false) }
    var lastAngle by remember { mutableFloatStateOf(0f) }
    var scratchVelocity by remember { mutableFloatStateOf(0f) }
    var scratchRotation by remember { mutableFloatStateOf(0f) }
    val scope = rememberCoroutineScope()
    val scratchSettleJob = remember { AtomicReference<Job?>(null) }

    // Автоматическое вращение (только если не скретчим)
    LaunchedEffect(isPlaying, isScratching, isSettling, rotationSpeed) {
        while (true) {
            if (isPlaying && !isScratching && !isSettling) {
                rotation.animateTo(
                    rotation.value + 360f,
                    animationSpec = tween(
                        (3000f / rotationSpeed.coerceAtLeast(0.1f)).toInt().coerceAtLeast(100),
                        easing = LinearEasing
                    )
                )
            } else {
                delay(200)
            }
        }
    }

    // Обработка скретча
    val scratchModifier = if (scratchEnabled) {
        Modifier.pointerInput(Unit) {
            detectDragGestures(
                onDragStart = { offset ->
                    scratchSettleJob.getAndSet(null)?.cancel()
                    isSettling = false
                    isScratching = true
                    scratchRotation = rotation.value
                    lastAngle = calculateAngle(offset, Offset(size.width / 2f, size.height / 2f))
                    scratchVelocity = 0f
                },
                onDrag = { change, _ ->
                    val currentAngle = calculateAngle(
                        change.position,
                        Offset(size.width / 2f, size.height / 2f)
                    )
                    var deltaAngle = currentAngle - lastAngle
                    if (deltaAngle > 180f) deltaAngle -= 360f
                    if (deltaAngle < -180f) deltaAngle += 360f

                    lastAngle = currentAngle
                    scratchVelocity = deltaAngle

                    // 360° = 10 секунд
                    val deltaSeconds = deltaAngle / 36f
                    onScratch?.invoke(deltaSeconds)

                    scratchRotation += deltaAngle
                },
                onDragEnd = {
                    val finalRotation = scratchRotation
                    val finalVelocity = scratchVelocity
                    isSettling = true
                    scratchSettleJob.set(scope.launch {
                        rotation.snapTo(finalRotation)
                        isScratching = false
                        if (kotlin.math.abs(finalVelocity) > 5f) {
                            rotation.animateTo(
                                finalRotation + finalVelocity * 3f,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessLow
                                )
                            )
                        }
                        isSettling = false
                        scratchSettleJob.set(null)
                    })
                }
            )
        }
    } else {
        Modifier
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF161616))
            .clickable { onExit() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(320.dp)
                    .graphicsLayer { rotationZ = if (isScratching) scratchRotation else rotation.value }
                    .then(scratchModifier),
                contentAlignment = Alignment.Center
            ) {
                artwork?.let {
                    Image(
                        bitmap = it,
                        contentDescription = localized("Обложка трека", "Track album art"),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                    )
                }
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val r = min(size.width, size.height) / 2f

                    if (artwork == null) {
                        drawCircle(color = Color(0xFF0D0D0D), radius = r, center = center)
                    }
                    drawCircle(
                        color = Color(0xFF2A2A2A),
                        radius = r - 1.dp.toPx(),
                        center = center,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
                    )

                    var groove = r * 0.92f
                    while (groove > r * 0.36f) {
                        drawCircle(
                            color = Color(0xFF1F1F1F).copy(alpha = if (artwork == null) 1f else 0.65f),
                            radius = groove,
                            center = center,
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 0.6.dp.toPx())
                        )
                        groove -= r * 0.035f
                    }

                    drawArc(
                        color = Color.White.copy(alpha = 0.07f),
                        startAngle = 200f,
                        sweepAngle = 60f,
                        useCenter = false,
                        topLeft = Offset(center.x - r, center.y - r),
                        size = androidx.compose.ui.geometry.Size(r * 2, r * 2),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = r * 0.5f)
                    )

                    if (artwork == null) {
                        drawCircle(color = Color(0xFFB0A08A), radius = r * 0.3f, center = center)
                        drawCircle(
                            color = Color(0xFF8A7A64),
                            radius = r * 0.3f,
                            center = center,
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx())
                        )
                    } else {
                        drawCircle(
                            color = Color(0xFF161616).copy(alpha = 0.22f),
                            radius = r * 0.3f,
                            center = center
                        )
                        drawCircle(
                            color = Color.White.copy(alpha = 0.4f),
                            radius = r * 0.3f,
                            center = center,
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx())
                        )
                    }
                    drawCircle(color = Color(0xFF161616), radius = r * 0.03f, center = center)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Box(
                modifier = Modifier
                    .background(Color(0xFF2A2A2A), RoundedCornerShape(4.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = if (track != null) "${track.title} - ${track.artist}" else "CirclePlayer",
                    color = Color(0xFFCFCFCF),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                    modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = localized("Тапните, чтобы вернуться", "Tap to return"),
                color = Color(0xFF6E6E6E),
                fontSize = 12.sp
            )
        }
    }
}
