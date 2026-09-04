package com.example.circleplayer

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.net.Uri
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.LightMode
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
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
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.*
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.example.circleplayer.audio.EffectsManager
import com.example.circleplayer.audio.EffectsRenderersFactory
import com.example.circleplayer.ui.theme.LocalPlayerPalette
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.content.Context
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

// =============== ВСПОМОГАТЕЛЬНЫЕ ФУНКЦИИ ===============

private fun formatTime(ms: Long): String {
    if (ms < 0) return "00:00"
    val totalSeconds = (ms / 1000).toInt()
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}

private fun calculateAngle(point: Offset, center: Offset): Float {
    val dx = point.x - center.x
    val dy = point.y - center.y
    return Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
}

// =============== ОСНОВНОЙ КОМПОЗЕБЛ ===============

@UnstableApi
@Composable
fun MusicPlayerApp(
    initialExoPlayer: ExoPlayer,
    effectsManager: EffectsManager,
    initialFolderPath: String? = null,
    onFolderSelect: () -> Unit,
    darkTheme: Boolean,
    onToggleTheme: () -> Unit
) {
    val context = LocalContext.current
    val appContext = context.applicationContext

    var currentFolderPath by remember { mutableStateOf(initialFolderPath) }
    var lastKnownFolderPath by remember { mutableStateOf<String?>(null) }

    var selectedIndex by rememberSaveable { mutableIntStateOf(0) }

    var tracks by remember { mutableStateOf<List<AudioTrack>>(emptyList()) }
    var isPlaying by remember { mutableStateOf(false) }
    var showEffectsMenu by remember { mutableStateOf(false) }
    var listMode by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showVinyl by remember { mutableStateOf(false) }

    val prefs = remember { context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE) }
    var screensaverEnabled by remember {
        mutableStateOf(prefs.getBoolean("screensaver_enabled", false))
    }
    var lastInteraction by remember { mutableLongStateOf(System.currentTimeMillis()) }

    var shuffleEnabled by remember { mutableStateOf(false) }
    var repeatMode by remember { mutableStateOf(0) } // 0 - off, 1 - all, 2 - one

    var useEffects by remember { mutableStateOf(false) }
    var previousUseEffects by remember { mutableStateOf(false) }
    var currentPlayer by remember { mutableStateOf(initialExoPlayer) }
    var playerGeneration by remember { mutableIntStateOf(0) }

    var lastScrollTime by remember { mutableLongStateOf(0L) }

    LaunchedEffect(initialFolderPath) {
        if (initialFolderPath != currentFolderPath) {
            currentFolderPath = initialFolderPath
        }
    }

    LaunchedEffect(currentFolderPath) {
        val newTracks = MusicRepository.getAudioTracks(context, currentFolderPath)
        tracks = newTracks

        if (currentFolderPath != lastKnownFolderPath) {
            selectedIndex = 0
            lastKnownFolderPath = currentFolderPath
        }

        if (tracks.isNotEmpty() && selectedIndex >= tracks.size) {
            selectedIndex = tracks.size - 1
        }
    }

    LaunchedEffect(useEffects) {
        if (useEffects == previousUseEffects) return@LaunchedEffect
        previousUseEffects = useEffects

        val oldPlayer = currentPlayer
        val wasPlaying = isPlaying || oldPlayer.isPlaying
        val position = oldPlayer.currentPosition.coerceAtLeast(0L)
        val trackUri = oldPlayer.currentMediaItem?.localConfiguration?.uri?.toString()
            ?: tracks.getOrNull(selectedIndex)?.uri

        try {
            oldPlayer.pause()
        } catch (_: Exception) {
        }

        val newPlayer = try {
            if (useEffects) {
                val renderersFactory = EffectsRenderersFactory(
                    appContext,
                    effectsManager.getAudioProcessors()
                )
                ExoPlayer.Builder(appContext, renderersFactory).build()
            } else {
                ExoPlayer.Builder(appContext).build()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            ExoPlayer.Builder(appContext).build()
        }

        currentPlayer = newPlayer
        playerGeneration++

        if (trackUri != null) {
            try {
                val mediaItem = MediaItem.fromUri(Uri.parse(trackUri))
                newPlayer.setMediaItem(mediaItem)
                newPlayer.prepare()
                if (position > 0) {
                    newPlayer.seekTo(position)
                }
                if (wasPlaying) {
                    isPlaying = true
                    newPlayer.playWhenReady = true
                    newPlayer.play()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        if (oldPlayer !== initialExoPlayer) {
            try {
                oldPlayer.release()
            } catch (_: Exception) {
            }
        }
    }

    LaunchedEffect(selectedIndex, tracks, isPlaying, playerGeneration) {
        if (!isPlaying || tracks.isEmpty()) return@LaunchedEffect
        val track = tracks.getOrNull(selectedIndex) ?: return@LaunchedEffect
        val player = currentPlayer
        try {
            val currentUri = player.currentMediaItem?.localConfiguration?.uri?.toString()
            if (currentUri != track.uri) {
                val mediaItem = MediaItem.fromUri(Uri.parse(track.uri))
                player.setMediaItem(mediaItem)
                player.prepare()
            }
            player.playWhenReady = true
            player.play()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Автопереход к следующему треку с учётом shuffle/repeat
    DisposableEffect(currentPlayer, playerGeneration) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState != Player.STATE_ENDED) return
                if (tracks.isEmpty()) return

                if (repeatMode == 2) {
                    currentPlayer.seekTo(0)
                    currentPlayer.playWhenReady = true
                    return
                }

                val next: Int? = if (shuffleEnabled) {
                    if (tracks.size == 1) selectedIndex
                    else {
                        var r = kotlin.random.Random.nextInt(tracks.size)
                        while (r == selectedIndex) r = kotlin.random.Random.nextInt(tracks.size)
                        r
                    }
                } else when {
                    selectedIndex + 1 < tracks.size -> selectedIndex + 1
                    repeatMode == 1 -> 0
                    else -> null
                }

                if (next != null) {
                    if (next == selectedIndex) {
                        currentPlayer.seekTo(0)
                        currentPlayer.playWhenReady = true
                    } else {
                        selectedIndex = next
                    }
                }
            }
        }
        currentPlayer.addListener(listener)
        onDispose { currentPlayer.removeListener(listener) }
    }

    fun handleTrackSelection(newIndex: Int) {
        if (newIndex in tracks.indices && newIndex != selectedIndex) {
            selectedIndex = newIndex
        }
    }

    fun handleScroll(stepCount: Int) {
        val now = System.currentTimeMillis()
        if (now - lastScrollTime < 100) return

        lastScrollTime = now

        if (tracks.isEmpty()) return

        val newIndex = (selectedIndex + stepCount).coerceIn(0, tracks.size - 1)
        if (newIndex != selectedIndex) {
            selectedIndex = newIndex
        }
    }

    fun handlePlayPause() {
        isPlaying = !isPlaying
        currentPlayer.playWhenReady = isPlaying
        if (isPlaying && tracks.isNotEmpty()) {
            val track = tracks.getOrNull(selectedIndex)
                ?: tracks.first().also { selectedIndex = 0 }
            val currentUri = currentPlayer.currentMediaItem?.localConfiguration?.uri?.toString()
            if (currentUri != track.uri) {
                currentPlayer.setMediaItem(MediaItem.fromUri(Uri.parse(track.uri)))
                currentPlayer.prepare()
            }
            currentPlayer.play()
        } else {
            currentPlayer.pause()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            if (currentPlayer !== initialExoPlayer) {
                try {
                    currentPlayer.release()
                } catch (_: Exception) {
                }
            }
        }
    }

    val backPressedDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher

    DisposableEffect(backPressedDispatcher) {
        val callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    showVinyl -> showVinyl = false
                    showSettings -> showSettings = false
                    showEffectsMenu -> showEffectsMenu = false
                    listMode -> listMode = false
                    else -> {
                        isEnabled = false
                        backPressedDispatcher?.onBackPressed()
                    }
                }
            }
        }
        backPressedDispatcher?.addCallback(callback)
        onDispose { callback.remove() }
    }

    // Скринсейвер при воспроизведении: через 10 секунд бездействия
    LaunchedEffect(
        screensaverEnabled, isPlaying, lastInteraction,
        showVinyl, showSettings, showEffectsMenu
    ) {
        if (!screensaverEnabled || !isPlaying) return@LaunchedEffect
        if (showVinyl || showSettings || showEffectsMenu) return@LaunchedEffect
        delay(10_000)
        if (System.currentTimeMillis() - lastInteraction >= 9_500) {
            showVinyl = true
        }
    }

    fun handleListButton() {
        when {
            showVinyl -> showVinyl = false
            listMode -> {
                listMode = false
                if (screensaverEnabled) showVinyl = true
            }
            else -> listMode = true
        }
    }

    // Скринсейвер доступен только при включённой настройке
    LaunchedEffect(screensaverEnabled) {
        if (!screensaverEnabled) showVinyl = false
    }

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
        when {
            showEffectsMenu -> EffectsMenu(
                effectsManager = effectsManager,
                useEffects = useEffects,
                onUseEffectsChange = { useEffects = it },
                onBack = { showEffectsMenu = false }
            )
            showSettings -> SettingsScreen(
                darkTheme = darkTheme,
                onToggleTheme = onToggleTheme,
                screensaverEnabled = screensaverEnabled,
                onToggleScreensaver = {
                    screensaverEnabled = it
                    prefs.edit().putBoolean("screensaver_enabled", it).apply()
                },
                onBack = { showSettings = false }
            )
            else -> NowPlayingScreen(
                tracks = tracks,
                selectedIndex = selectedIndex,
                currentPlayer = currentPlayer,
                isPlaying = isPlaying,
                shuffleEnabled = shuffleEnabled,
                repeatMode = repeatMode,
                darkTheme = darkTheme,
                listMode = listMode,
                onToggleShuffle = { shuffleEnabled = !shuffleEnabled },
                onCycleRepeat = { repeatMode = (repeatMode + 1) % 3 },
                onPlayPause = {
                    if (listMode) {
                        if (tracks.isNotEmpty()) {
                            listMode = false
                            isPlaying = true
                            currentPlayer.playWhenReady = true
                            currentPlayer.play()
                        }
                    } else {
                        handlePlayPause()
                    }
                },
                onTrackPlay = { index ->
                    if (index != selectedIndex) {
                        selectedIndex = index
                    } else if (tracks.isNotEmpty()) {
                        isPlaying = true
                        currentPlayer.playWhenReady = true
                        currentPlayer.play()
                    }
                    listMode = false
                },
                onPreviousTrack = { handleScroll(-1) },
                onNextTrack = { handleScroll(1) },
                onSkipForward = {
                    if (currentPlayer.duration > 0) {
                        currentPlayer.seekTo(
                            (currentPlayer.currentPosition + 10000)
                                .coerceAtMost(currentPlayer.duration)
                        )
                    }
                },
                onSkipBackward = {
                    currentPlayer.seekTo((currentPlayer.currentPosition - 10000).coerceAtLeast(0L))
                },
                onScroll = { stepCount -> handleScroll(stepCount) },
                onListButton = { handleListButton() },
                onOpenEffects = { showEffectsMenu = true },
                onOpenSettings = { showSettings = true },
                onFolderSelect = onFolderSelect,
                onToggleTheme = onToggleTheme
            )
        }

        if (showVinyl) {
            VinylScreensaver(
                track = tracks.getOrNull(selectedIndex),
                isPlaying = isPlaying || currentPlayer.isPlaying,
                onExit = { showVinyl = false }
            )
        }
    }
}

// =============== Экран "Сейчас играет" ===============

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NowPlayingScreen(
    tracks: List<AudioTrack>,
    selectedIndex: Int,
    currentPlayer: ExoPlayer,
    isPlaying: Boolean,
    shuffleEnabled: Boolean,
    repeatMode: Int,
    darkTheme: Boolean,
    listMode: Boolean,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onPlayPause: () -> Unit,
    onTrackPlay: (Int) -> Unit,
    onPreviousTrack: () -> Unit,
    onNextTrack: () -> Unit,
    onSkipForward: () -> Unit,
    onSkipBackward: () -> Unit,
    onScroll: (Int) -> Unit,
    onListButton: () -> Unit,
    onOpenEffects: () -> Unit,
    onOpenSettings: () -> Unit,
    onFolderSelect: () -> Unit,
    onToggleTheme: () -> Unit
) {
    val palette = LocalPlayerPalette.current
    val track = tracks.getOrNull(selectedIndex)
    val isLandscape =
        LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val iconRotation = if (isLandscape) 90f else 0f

    var currentPosition by remember(currentPlayer) { mutableLongStateOf(currentPlayer.currentPosition) }

    LaunchedEffect(currentPlayer, isPlaying) {
        while (true) {
            currentPosition = currentPlayer.currentPosition
            delay(250)
        }
    }

    val duration = currentPlayer.duration.takeIf { it > 0 } ?: (track?.duration ?: 0L)
    val progress = if (duration > 0) {
        (currentPosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
    } else 0f

    var isDraggingSlider by remember { mutableStateOf(false) }
    var dragProgress by remember { mutableFloatStateOf(0f) }

    val body: @Composable () -> Unit = {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(palette.background)
                .padding(horizontal = 20.dp)
        ) {
        // Верхняя панель: тема
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onToggleTheme) {
                Icon(
                    if (darkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                    "Тема",
                    tint = palette.textSecondary,
                    modifier = Modifier.graphicsLayer { rotationZ = iconRotation }
                )
            }
        }

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
                        if (listMode) {
                            val panelListState = rememberLazyListState()

                            LaunchedEffect(selectedIndex, tracks) {
                                if (listMode && selectedIndex in tracks.indices) {
                                    val viewportHeight = panelListState.layoutInfo.viewportEndOffset -
                                        panelListState.layoutInfo.viewportStartOffset
                                    val offset = -(viewportHeight / 2 - 60).coerceAtLeast(0)
                                    panelListState.animateScrollToItem(selectedIndex, offset)
                                }
                            }

                            LazyColumn(
                                state = panelListState,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(palette.background, RoundedCornerShape(8.dp))
                                    .border(1.dp, palette.divider, RoundedCornerShape(8.dp))
                                    .padding(vertical = 6.dp),
                                contentPadding = PaddingValues(horizontal = 6.dp)
                            ) {
                                itemsIndexed(tracks, key = { index, t -> "${t.uri}_$index" }) { index, track ->
                                    TrackRow(
                                        track = track,
                                        isSelected = index == selectedIndex,
                                        onClick = { onTrackPlay(index) }
                                    )
                                }
                            }
                        } else {
                            DiscArt(isPlaying = isPlaying)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Блок активного трека
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(palette.chipBackground, RoundedCornerShape(8.dp))
                            .border(1.dp, palette.discRim, RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = onToggleShuffle) {
                                Icon(
                                    Icons.Default.Shuffle,
                                    "Перемешивание",
                                    tint = if (shuffleEnabled) palette.chipText
                                    else palette.chipText.copy(alpha = 0.5f),
                                    modifier = Modifier
                                        .size(22.dp)
                                        .graphicsLayer { rotationZ = iconRotation }
                                )
                            }

                            val label = if (track != null) {
                                "${track.title} - ${track.artist}"
                            } else {
                                "Нет треков"
                            }
                            Text(
                                text = label,
                                color = palette.chipText,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Clip,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier = Modifier
                                    .weight(1f)
                                    .basicMarquee(iterations = Int.MAX_VALUE)
                            )

                            IconButton(onClick = onCycleRepeat) {
                                Icon(
                                    if (repeatMode == 2) Icons.Default.RepeatOne else Icons.Default.Repeat,
                                    "Повтор",
                                    tint = if (repeatMode > 0) palette.chipText
                                    else palette.chipText.copy(alpha = 0.5f),
                                    modifier = Modifier
                                        .size(22.dp)
                                        .graphicsLayer { rotationZ = iconRotation }
                                )
                            }
                        }

                        Slider(
                            value = if (isDraggingSlider) dragProgress else progress,
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
                                thumbColor = palette.chipText,
                                activeTrackColor = palette.chipText,
                                inactiveTrackColor = Color.White.copy(alpha = 0.22f)
                            )
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                formatTime(currentPosition),
                                color = palette.chipText,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            Text(
                                formatTime(duration),
                                color = palette.chipText,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
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
                if (listMode) {
                    val panelListState = rememberLazyListState()

                    LaunchedEffect(selectedIndex, tracks) {
                        if (listMode && selectedIndex in tracks.indices) {
                            val viewportHeight = panelListState.layoutInfo.viewportEndOffset -
                                panelListState.layoutInfo.viewportStartOffset
                            val offset = -(viewportHeight / 2 - 60).coerceAtLeast(0)
                            panelListState.animateScrollToItem(selectedIndex, offset)
                        }
                    }

                    LazyColumn(
                        state = panelListState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(vertical = 6.dp),
                        contentPadding = PaddingValues(horizontal = 6.dp)
                    ) {
                        itemsIndexed(tracks, key = { index, t -> "${t.uri}_$index" }) { index, track ->
                            TrackRow(
                                track = track,
                                isSelected = index == selectedIndex,
                                onClick = { onTrackPlay(index) }
                            )
                        }
                    }
                } else {
                    DiscArt(isPlaying = isPlaying)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Marquee-строка с названием
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onToggleShuffle) {
                    Icon(
                        Icons.Default.Shuffle,
                        "Перемешивание",
                        tint = if (shuffleEnabled) palette.wheelIcon else palette.textSecondary,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(palette.chipBackground, RoundedCornerShape(4.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val label = if (track != null) {
                        "${track.title} - ${track.artist}"
                    } else {
                        "Нет треков"
                    }
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

                IconButton(onClick = onCycleRepeat) {
                    Icon(
                        if (repeatMode == 2) Icons.Default.RepeatOne else Icons.Default.Repeat,
                        "Повтор",
                        tint = if (repeatMode > 0) palette.wheelIcon else palette.textSecondary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Прогресс
            Column(modifier = Modifier.fillMaxWidth()) {
                Slider(
                    value = if (isDraggingSlider) dragProgress else progress,
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
                        thumbColor = palette.wheelIcon,
                        activeTrackColor = palette.progressActive,
                        inactiveTrackColor = palette.progressTrack
                    )
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        formatTime(currentPosition),
                        color = palette.text,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    Text(
                        formatTime(duration),
                        color = palette.text,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        // Колесо управления
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1.3f),
            contentAlignment = Alignment.Center
        ) {
            ClickWheel(
                isPlaying = isPlaying,
                iconRotation = iconRotation,
                onScroll = onScroll,
                onCenterClick = onPlayPause,
                onSkipForward = onSkipForward,
                onSkipBackward = onSkipBackward,
                onPreviousTrack = onPreviousTrack,
                onNextTrack = onNextTrack
            )

            SideCircleButton(
                icon = if (listMode) Icons.Default.Close else Icons.AutoMirrored.Filled.List,
                description = if (listMode) "Выйти из списка" else "Список треков",
                iconRotation = iconRotation,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = 12.dp, y = (-64).dp)
            ) { onListButton() }

            SideCircleButton(
                icon = Icons.Default.Tune,
                description = "Эффекты",
                iconRotation = iconRotation,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .offset(x = (-12).dp, y = (-64).dp)
            ) { onOpenEffects() }

            // Кнопка выбора папки — в нижнем левом углу
            IconButton(
                onClick = onFolderSelect,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(bottom = 4.dp)
            ) {
                Icon(
                    Icons.Default.Folder,
                    "Выбрать папку",
                    tint = palette.textSecondary,
                    modifier = Modifier.graphicsLayer { rotationZ = iconRotation }
                )
            }

            // Кнопка настроек — в нижнем правом углу
            IconButton(
                onClick = onOpenSettings,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 4.dp)
            ) {
                Icon(
                    Icons.Default.Settings,
                    "Настройки",
                    tint = palette.textSecondary,
                    modifier = Modifier.graphicsLayer { rotationZ = iconRotation }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
    }

    if (isLandscape) {
        // Корпус iPod зафиксирован относительно устройства: поворачиваем всё колесо
        // и кнопки вместе с устройством, а дисплеи разворачиваем к читателю
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(palette.background)
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .requiredSize(width = maxHeight, height = maxWidth)
                        .graphicsLayer { rotationZ = -90f }
                ) {
                    body()
                }
            }
        }
    } else {
        body()
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
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val palette = LocalPlayerPalette.current
    Box(
        modifier = modifier
            .size(72.dp)
            .shadow(3.dp, CircleShape)
            .background(palette.sideButton, CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            description,
            tint = palette.sideButtonIcon,
            modifier = Modifier
                .size(30.dp)
                .graphicsLayer { rotationZ = iconRotation }
        )
    }
}

@Composable
private fun DiscArt(isPlaying: Boolean) {
    val palette = LocalPlayerPalette.current
    val rotation = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            while (true) {
                rotation.animateTo(
                    rotation.value + 360f,
                    animationSpec = tween(3000, easing = LinearEasing)
                )
            }
        }
    }

    Canvas(
        modifier = Modifier
            .fillMaxWidth(0.62f)
            .aspectRatio(1f)
            .graphicsLayer { rotationZ = rotation.value }
    ) {
        val r = min(size.width, size.height) / 2f

        // Тело диска
        drawCircle(
            color = palette.discBody,
            radius = r,
            center = center
        )
        // Внешний ободок
        drawCircle(
            color = palette.discRim,
            radius = r - 1.dp.toPx(),
            center = center,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
        )
        // Декоративные кольца
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
        // Метка, показывающая вращение
        rotate(0f, pivot = center) {
            drawLine(
                color = palette.discRim.copy(alpha = 0.6f),
                start = Offset(center.x, center.y - r * 0.86f),
                end = Offset(center.x, center.y - r * 0.52f),
                strokeWidth = 1.5.dp.toPx()
            )
        }
        // Центральное отверстие
        drawCircle(color = palette.discHole, radius = r * 0.16f, center = center)
        drawCircle(
            color = palette.discHoleInner,
            radius = r * 0.07f,
            center = center
        )
    }
}

// =============== ClickWheel (Play не вращается) ===============

@SuppressLint("RestrictedApi")
@Composable
fun ClickWheel(
    isPlaying: Boolean = false,
    iconRotation: Float = 0f,
    onScroll: (Int) -> Unit,
    onCenterClick: () -> Unit,
    onSkipForward: () -> Unit,
    onSkipBackward: () -> Unit,
    onPreviousTrack: () -> Unit,
    onNextTrack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val palette = LocalPlayerPalette.current
    val rotation = remember { Animatable(0f) }
    val coroutineScope = rememberCoroutineScope()

    var lastScrollTime by remember { mutableLongStateOf(0L) }

    val vibrate = remember {
        {
            try {
                val now = System.currentTimeMillis()
                if (now - lastScrollTime < 50) return@remember

                val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                    vm.defaultVibrator
                } else {
                    @Suppress("DEPRECATION")
                    context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                }
                if (Build.VERSION_CODES.O <= Build.VERSION.SDK_INT) {
                    vibrator.vibrate(VibrationEffect.createOneShot(10, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(10)
                }
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    var lastAngle by remember { mutableStateOf(0f) }
    var accumulatedRotation by remember { mutableStateOf(0f) }
    var lastReportedStep by remember { mutableStateOf(0) }

    Box(
        modifier = modifier
            .size(280.dp)
            .shadow(6.dp, CircleShape)
            .background(palette.wheel, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        // Вращающийся слой: только разметка кольца, без кнопок
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    val center = Offset((size.width / 2).toFloat(), (size.height / 2).toFloat())

                    detectDragGestures(
                        onDragStart = { offset ->
                            lastAngle = calculateAngle(offset, center)
                            accumulatedRotation = 0f
                            lastReportedStep = 0
                        },
                        onDrag = { change, _ ->
                            val now = System.currentTimeMillis()
                            if (now - lastScrollTime < 50) return@detectDragGestures

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
                                onScroll(diff)
                                vibrate()
                                lastReportedStep = currentStep
                                lastScrollTime = now
                            }

                            val targetRotation = accumulatedRotation * 1.5f
                            if (rotation.targetValue != targetRotation) {
                                coroutineScope.launch {
                                    rotation.animateTo(
                                        targetRotation,
                                        animationSpec = tween(100, easing = LinearEasing)
                                    )
                                }
                            }
                        },
                        onDragEnd = {
                            coroutineScope.launch {
                                rotation.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                            }
                        }
                    )
                }
                .graphicsLayer { rotationZ = rotation.value }
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

        // Стрелки — статичны, не вращаются
        Icon(
            imageVector = Icons.Default.FastForward,
            contentDescription = "Вперёд на 10 секунд",
            tint = palette.wheelIcon,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 22.dp)
                .size(34.dp)
                    .graphicsLayer { rotationZ = iconRotation }
                .clickable { onSkipForward() }
        )

        Icon(
            imageVector = Icons.Default.FastRewind,
            contentDescription = "Назад на 10 секунд",
            tint = palette.wheelIcon,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 22.dp)
                .size(34.dp)
                    .graphicsLayer { rotationZ = iconRotation }
                .clickable { onSkipBackward() }
        )

        Icon(
            imageVector = Icons.Default.SkipPrevious,
            contentDescription = "Предыдущий трек",
            tint = palette.wheelIcon,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 22.dp)
                .size(34.dp)
                    .graphicsLayer { rotationZ = iconRotation }
                .clickable { onPreviousTrack() }
        )

        Icon(
            imageVector = Icons.Default.SkipNext,
            contentDescription = "Следующий трек",
            tint = palette.wheelIcon,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 22.dp)
                .size(34.dp)
                    .graphicsLayer { rotationZ = iconRotation }
                .clickable { onNextTrack() }
        )

        // Центральная кнопка Play/Pause — статична
        Box(
            modifier = Modifier
                .size(88.dp)
                .background(palette.centerButton, CircleShape)
                .clickable { onCenterClick() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (isPlaying) "Пауза" else "Воспроизвести",
                tint = palette.centerIcon,
                modifier = Modifier
                    .size(40.dp)
                    .graphicsLayer { rotationZ = iconRotation }
            )
        }
    }
}

// =============== Строка трека ===============

@Composable
fun TrackRow(track: AudioTrack, isSelected: Boolean, onClick: () -> Unit) {
    val palette = LocalPlayerPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .background(if (isSelected) palette.listRowSelected else Color.Transparent)
            .clickable { onClick() }
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isSelected) {
            Icon(
                Icons.Default.PlayArrow,
                null,
                tint = palette.text,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
        }
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
                text = "Эффекты плёнки",
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
                            text = "Включить эффекты",
                            style = MaterialTheme.typography.titleMedium,
                            color = palette.text
                        )
                        Text(
                            text = "Переключает режим воспроизведения",
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.textSecondary
                        )
                    }
                    Switch(
                        checked = useEffects,
                        onCheckedChange = onUseEffectsChange,
                        colors = SwitchDefaults.colors(
                            checkedTrackColor = palette.wheelIcon,
                            checkedThumbColor = palette.centerIcon,
                            uncheckedThumbColor = palette.textSecondary,
                            uncheckedTrackColor = palette.progressTrack
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            EffectCard(enabled = useEffects) {
                EffectHeader(
                    title = "Wow & Flutter",
                    subtitle = "Неравномерность скорости воспроизведения",
                    checked = wowEnabled,
                    onCheckedChange = {
                        wowEnabled = it
                        effectsManager.wowFlutter.enabled = it
                    }
                )
                LabeledSlider(
                    label = "Глубина",
                    value = wowDepth,
                    range = 0f..1f,
                    onValueChange = {
                        wowDepth = it
                        effectsManager.wowFlutter.depth = it
                    }
                )
                LabeledSlider(
                    label = "Частота, Гц",
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
                    subtitle = "Перегрузка при высокой амплитуде",
                    checked = detonationEnabled,
                    onCheckedChange = {
                        detonationEnabled = it
                        effectsManager.volumeDetonation.enabled = it
                    }
                )
                LabeledSlider(
                    label = "Степень",
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
                    subtitle = "Эффект хора",
                    checked = chorusEnabled,
                    onCheckedChange = {
                        chorusEnabled = it
                        effectsManager.chorus.enabled = it
                    }
                )
                LabeledSlider(
                    label = "Глубина",
                    value = chorusDepth,
                    range = 0f..1f,
                    onValueChange = {
                        chorusDepth = it
                        effectsManager.chorus.depth = it
                    }
                )
                LabeledSlider(
                    label = "Частота, Гц",
                    value = chorusRate,
                    range = 0.1f..5f,
                    onValueChange = {
                        chorusRate = it
                        effectsManager.chorus.rate = it
                    }
                )
                LabeledSlider(
                    label = "Микс",
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
                    subtitle = "Шум и хруст винила",
                    checked = noiseEnabled,
                    onCheckedChange = {
                        noiseEnabled = it
                        effectsManager.vintageNoise.enabled = it
                    }
                )
                LabeledSlider(
                    label = "Уровень шума",
                    value = noiseLevel,
                    range = 0f..1f,
                    onValueChange = {
                        noiseLevel = it
                        effectsManager.vintageNoise.noiseLevel = it
                    }
                )
                LabeledSlider(
                    label = "Хруст",
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
                "Назад",
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
            colors = SwitchDefaults.colors(
                            checkedTrackColor = palette.wheelIcon,
                            checkedThumbColor = palette.centerIcon,
                            uncheckedThumbColor = palette.textSecondary,
                            uncheckedTrackColor = palette.progressTrack
            )
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
            thumbColor = palette.wheelIcon,
            activeTrackColor = palette.progressActive,
            inactiveTrackColor = palette.progressTrack
        )
    )
}


// =============== Настройки ===============

@Composable
fun SettingsScreen(
    darkTheme: Boolean,
    onToggleTheme: () -> Unit,
    screensaverEnabled: Boolean,
    onToggleScreensaver: (Boolean) -> Unit,
    onBack: () -> Unit
) {
    val palette = LocalPlayerPalette.current

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
                text = "Настройки",
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
                                text = if (darkTheme) "Тёмная тема" else "Светлая тема",
                                style = MaterialTheme.typography.titleMedium,
                                color = palette.text
                            )
                            Text(
                                text = "Оформление интерфейса приложения",
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
                                colors = SwitchDefaults.colors(
                            checkedTrackColor = palette.wheelIcon,
                            checkedThumbColor = palette.centerIcon,
                            uncheckedThumbColor = palette.textSecondary,
                            uncheckedTrackColor = palette.progressTrack
                                )
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
                                text = "Скринсейвер",
                                style = MaterialTheme.typography.titleMedium,
                                color = palette.text
                            )
                            Text(
                                text = "Винил появляется при воспроизведении музыки после 10 секунд бездействия",
                                style = MaterialTheme.typography.bodySmall,
                                color = palette.textSecondary
                            )
                        }
                        Switch(
                            checked = screensaverEnabled,
                            onCheckedChange = onToggleScreensaver,
                            colors = SwitchDefaults.colors(
                            checkedTrackColor = palette.wheelIcon,
                            checkedThumbColor = palette.centerIcon,
                            uncheckedThumbColor = palette.textSecondary,
                            uncheckedTrackColor = palette.progressTrack
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
                "Назад",
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
    onExit: () -> Unit
) {
    val palette = LocalPlayerPalette.current
    val rotation = remember { Animatable(0f) }

    LaunchedEffect(isPlaying) {
        while (true) {
            if (isPlaying) {
                rotation.animateTo(
                    rotation.value + 360f,
                    animationSpec = tween(2700, easing = LinearEasing)
                )
            } else {
                delay(200)
            }
        }
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
            Canvas(
                modifier = Modifier
                    .size(320.dp)
                    .graphicsLayer { rotationZ = rotation.value }
            ) {
                val r = min(size.width, size.height) / 2f

                // Тело виниловой пластинки
                drawCircle(color = Color(0xFF0D0D0D), radius = r, center = center)
                drawCircle(
                    color = Color(0xFF2A2A2A),
                    radius = r - 1.dp.toPx(),
                    center = center,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
                )

                // Дорожки
                var groove = r * 0.92f
                while (groove > r * 0.36f) {
                    drawCircle(
                        color = Color(0xFF1F1F1F),
                        radius = groove,
                        center = center,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 0.6.dp.toPx())
                    )
                    groove -= r * 0.035f
                }

                // Блик
                drawArc(
                    color = Color.White.copy(alpha = 0.07f),
                    startAngle = 200f,
                    sweepAngle = 60f,
                    useCenter = false,
                    topLeft = Offset(center.x - r, center.y - r),
                    size = androidx.compose.ui.geometry.Size(r * 2, r * 2),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = r * 0.5f)
                )

                // этикетка
                drawCircle(color = Color(0xFFB0A08A), radius = r * 0.3f, center = center)
                drawCircle(color = Color(0xFF8A7A64), radius = r * 0.3f, center = center,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx()))
                drawCircle(color = Color(0xFF161616), radius = r * 0.03f, center = center)
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
                text = "Тапните, чтобы вернуться",
                color = Color(0xFF6E6E6E),
                fontSize = 12.sp
            )
        }
    }
}
