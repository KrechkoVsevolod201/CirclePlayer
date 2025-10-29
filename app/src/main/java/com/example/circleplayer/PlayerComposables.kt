package com.example.circleplayer

import android.annotation.SuppressLint
import android.net.Uri
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.*
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.content.Context
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

// =============== ВСПОМОГАТЕЛЬНЫЕ ФУНКЦИИ ===============

private fun formatTime(ms: Long): String {
    if (ms < 0) return "--:--"
    val totalSeconds = (ms / 1000).toInt()
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}

private fun Float.toRadians() = this * (kotlin.math.PI.toFloat() / 180f)

// =============== ОСНОВНОЙ КОМПОЗЕБЛ ===============

@Composable
fun MusicPlayerApp(
    exoPlayer: ExoPlayer,
    initialFolderPath: String? = null,
    onFolderSelect: () -> Unit
) {
    val context = LocalContext.current

    var currentFolderPath by remember { mutableStateOf(initialFolderPath) }
    var lastKnownFolderPath by remember { mutableStateOf<String?>(null) }

    // 🔑 Используем rememberSaveable с ключом
    var selectedIndex by rememberSaveable(currentFolderPath) { mutableIntStateOf(0) }

    var tracks by remember { mutableStateOf<List<AudioTrack>>(emptyList()) }
    var isPlaying by remember { mutableStateOf(false) }
    var isFullScreen by remember { mutableStateOf(false) }

    // 🔑 Запоминаем время последнего скролла для предотвращения множественных вызовов
    var lastScrollTime by remember { mutableLongStateOf(0L) }

    // Загружаем треки при изменении папки
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

    // Обработка воспроизведения
    LaunchedEffect(selectedIndex, tracks, isPlaying) {
        if (isPlaying && tracks.isNotEmpty()) {
            val track = tracks.getOrNull(selectedIndex) ?: return@LaunchedEffect
            val mediaItem = MediaItem.fromUri(Uri.parse(track.uri))
            if (exoPlayer.currentMediaItem?.mediaId != track.uri) {
                exoPlayer.setMediaItem(mediaItem)
                exoPlayer.prepare()
                exoPlayer.play()
            }
        }
    }

    // 🔑 Функция для безопасного изменения индекса
    fun handleTrackSelection(newIndex: Int) {
        if (newIndex in tracks.indices && newIndex != selectedIndex) {
            selectedIndex = newIndex
        }
    }

    // 🔑 Функция для обработки скролла с защитой от спама
    fun handleScroll(stepCount: Int) {
        val now = System.currentTimeMillis()
        if (now - lastScrollTime < 100) return // Защита от слишком частых вызовов

        lastScrollTime = now

        if (tracks.isEmpty()) return

        val newIndex = (selectedIndex + stepCount).coerceIn(0, tracks.size - 1)
        if (newIndex != selectedIndex) {
            selectedIndex = newIndex
        }
    }

    if (isFullScreen) {
        FullScreenPlayer(
            track = tracks.getOrNull(selectedIndex),
            exoPlayer = exoPlayer,
            isPlaying = isPlaying,
            onPlayPause = {
                isPlaying = !isPlaying
                exoPlayer.playWhenReady = isPlaying
            },
            onBack = { isFullScreen = false }
        )
    } else {
        iPodView(
            tracks = tracks,
            selectedIndex = selectedIndex,
            isPlaying = isPlaying,
            onTrackSelected = { index -> handleTrackSelection(index) },
            onPlayPause = {
                isPlaying = !isPlaying
                exoPlayer.playWhenReady = isPlaying
                if (isPlaying && tracks.isNotEmpty()) {
                    val track = tracks.getOrNull(selectedIndex) ?: tracks.first().also { selectedIndex = 0 }
                    val mediaItem = MediaItem.fromUri(Uri.parse(track.uri))
                    if (exoPlayer.currentMediaItem?.mediaId != track.uri) {
                        exoPlayer.setMediaItem(mediaItem)
                        exoPlayer.prepare()
                    }
                    exoPlayer.play()
                } else {
                    exoPlayer.pause()
                }
            },
            onMore = { isFullScreen = true },
            onFolderSelect = onFolderSelect,
            onScroll = { stepCount -> handleScroll(stepCount) }
        )
    }
}

// =============== iPodView ===============

@Composable
fun iPodView(
    tracks: List<AudioTrack>,
    selectedIndex: Int,
    isPlaying: Boolean,
    onTrackSelected: (Int) -> Unit,
    onPlayPause: () -> Unit,
    onMore: () -> Unit,
    onFolderSelect: () -> Unit,
    onScroll: (Int) -> Unit // 🔑 Добавляем отдельный callback для скролла
) {
    val lazyListState = rememberLazyListState()
    var isUserScrolling by remember { mutableStateOf(false) }

    // 🔑 Улучшенная логика авто-прокрутки
    LaunchedEffect(selectedIndex) {
        if (tracks.isNotEmpty() && selectedIndex in tracks.indices && !isUserScrolling) {
            // Небольшая задержка для предотвращения конфликтов
            delay(50)
            if (!isUserScrolling) {
                lazyListState.animateScrollToItem(selectedIndex)
            }
        }
    }

    // 🔑 Отслеживаем пользовательский скролл
    LaunchedEffect(lazyListState.isScrollInProgress) {
        isUserScrolling = lazyListState.isScrollInProgress
        if (!lazyListState.isScrollInProgress) {
            // После завершения скролла можно снова включить авто-прокрутку
            delay(300)
            isUserScrolling = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .border(2.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
            .padding(8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color(0xFFE8F0E8), RoundedCornerShape(8.dp))
                .padding(8.dp)
                .border(1.dp, Color(0xFFA8B5A0), RoundedCornerShape(8.dp))
        ) {
            LazyColumn(
                state = lazyListState,
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // 🔑 Улучшенный ключ - комбинация URI и индекса
                items(tracks.size, key = { index -> "${tracks[index].uri}_$index" }) { index ->
                    TrackRow(
                        track = tracks[index],
                        isSelected = index == selectedIndex,
                        onClick = { onTrackSelected(index) }
                    )
                }
            }

            Text(
                text = "${selectedIndex + 1} / ${tracks.size}",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF556B55),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .background(Color.White.copy(alpha = 0.8f), CircleShape)
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            )

            IconButton(
                onClick = onFolderSelect,
                modifier = Modifier.align(Alignment.BottomEnd)
            ) {
                Icon(Icons.Default.Folder, "Select Folder", tint = Color(0xFF556B55))
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(top = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            ClickWheel(
                isPlaying = isPlaying,
                onScroll = { stepCount ->
                    onScroll(stepCount) // 🔑 Используем новый callback
                },
                onCenterClick = onPlayPause
            )

            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = { /* Back */ }) {
                    Icon(Icons.Default.ArrowBack, "Back", tint = Color.Gray)
                }
                IconButton(onClick = onMore) {
                    Icon(Icons.Default.MoreVert, "More", tint = Color.Gray)
                }
            }
        }
    }
}

// =============== ClickWheel ===============

@SuppressLint("RestrictedApi")
@Composable
fun ClickWheel(
    isPlaying: Boolean = false,
    onScroll: (Int) -> Unit,
    onCenterClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val rotation = remember { Animatable(0f) }
    val coroutineScope = rememberCoroutineScope()

    // 🔑 Добавляем защиту от слишком быстрого скролла
    var lastScrollTime by remember { mutableLongStateOf(0L) }

    val vibrate = remember {
        {
            try {
                val now = System.currentTimeMillis()
                if (now - lastScrollTime < 50) return@remember // Не вибрируем слишком часто

                val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                    vm.defaultVibrator
                } else {
                    @Suppress("DEPRECATION")
                    context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
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

    var startY by remember { mutableStateOf(0f) }
    var currentOffset by remember { mutableStateOf(0f) }
    var lastReportedStep by remember { mutableStateOf(0) }

    Box(
        modifier = modifier
            .size(240.dp)
            .background(Color(0xFFE0E0E0), shape = CircleShape)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        startY = offset.y
                        currentOffset = 0f
                        lastReportedStep = 0
                        lastScrollTime = System.currentTimeMillis()
                    },
                    onDrag = { change, _ ->
                        val now = System.currentTimeMillis()
                        if (now - lastScrollTime < 50) return@detectDragGestures // 🔑 Защита от частых обновлений

                        currentOffset = -(change.position.y - startY)
                        val stepSize = 40f // 🔑 Увеличиваем шаг для меньшей чувствительности
                        val currentStep = (currentOffset / stepSize).toInt()
                        if (currentStep != lastReportedStep) {
                            val diff = currentStep - lastReportedStep
                            onScroll(diff)
                            vibrate()
                            lastReportedStep = currentStep
                            lastScrollTime = now
                        }
                        val targetRotation = currentOffset * 1.5f // 🔑 Уменьшаем вращение
                        if (rotation.targetValue != targetRotation) {
                            coroutineScope.launch {
                                rotation.animateTo(
                                    targetRotation,
                                    animationSpec = tween(100, easing = LinearEasing) // 🔑 Увеличиваем время анимации
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
            .graphicsLayer { rotationZ = rotation.value },
        contentAlignment = Alignment.Center
    ) {
        // ... остальной код без изменений
        Canvas(modifier = Modifier.fillMaxSize()) {
            // ... существующий код
        }

        Box(
            modifier = Modifier
                .size(64.dp)
                .background(Color(0xFF556B55), CircleShape)
                .clickable { onCenterClick() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (isPlaying) "Pause" else "Play",
                tint = Color.White,
                modifier = Modifier.size(32.dp)
            )
        }
    }
}

// =============== TrackRow ===============

@Composable
fun TrackRow(track: AudioTrack, isSelected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isSelected) Color.LightGray else Color.Transparent)
            .clickable { onClick() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = track.title,
                fontWeight = if (isSelected) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Normal
            )
            Text(track.artist, style = MaterialTheme.typography.bodySmall)
        }
    }
}

// =============== FullScreenPlayer ===============

@Composable
fun FullScreenPlayer(
    track: AudioTrack?,
    exoPlayer: ExoPlayer,
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
    onBack: () -> Unit
) {
    var currentPosition by remember { mutableStateOf(exoPlayer.currentPosition) }

    LaunchedEffect(isPlaying) {
        if (!isPlaying) return@LaunchedEffect
        while (true) {
            currentPosition = exoPlayer.currentPosition
            delay(100)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        IconButton(
            onClick = onBack,
            modifier = Modifier.align(Alignment.TopStart).padding(16.dp)
        ) {
            Icon(Icons.Default.ArrowBack, "Back", tint = Color(0xFF556B55))
        }

        track?.let {
            val totalTime = if (exoPlayer.duration > 0) formatTime(exoPlayer.duration) else "--:--"
            val currentTime = formatTime(currentPosition)

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 64.dp)
            ) {
                Text(it.title, style = MaterialTheme.typography.headlineMedium, color = Color(0xFF333333))
                Spacer(modifier = Modifier.height(4.dp))
                Text(it.artist, style = MaterialTheme.typography.titleMedium, color = Color(0xFF555555))
                Spacer(modifier = Modifier.height(12.dp))
                Text("$currentTime / $totalTime", style = MaterialTheme.typography.bodyMedium)

                if (exoPlayer.duration > 0) {
                    LinearProgressIndicator(
                        progress = (currentPosition.toFloat() / exoPlayer.duration.toFloat()).coerceIn(0f, 1f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 48.dp)
                            .padding(top = 8.dp),
                        color = Color(0xFF556B55)
                    )
                }
            }
        }

        ClickWheel(
            isPlaying = isPlaying,
            onScroll = { stepCount ->
                if (exoPlayer.duration <= 0) return@ClickWheel
                val stepMs = 5000L
                val newPosition = (exoPlayer.currentPosition + stepCount * stepMs).toLong()
                    .coerceIn(0L, exoPlayer.duration)
                exoPlayer.seekTo(newPosition)
                currentPosition = newPosition
            },
            onCenterClick = onPlayPause,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp)
        )
    }
}