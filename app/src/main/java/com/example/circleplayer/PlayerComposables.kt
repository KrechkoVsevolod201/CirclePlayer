package com.example.circleplayer

import android.net.Uri
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.*
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.compose.ui.platform.LocalContext
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.absoluteValue
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.content.Context
import androidx.compose.foundation.shape.RoundedCornerShape
import kotlinx.coroutines.delay


// =============== ВСПОМОГАТЕЛЬНЫЕ ФУНКЦИИ ===============

private fun formatTime(ms: Long): String {
    if (ms < 0) return "--:--"
    val totalSeconds = (ms / 1000).toInt()
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}

// =============== ОСНОВНОЙ КОМПОЗЕБЛ ===============

@Composable
fun MusicPlayerApp(
    exoPlayer: ExoPlayer,
    initialFolderPath: String? = null,
    onFolderSelect: () -> Unit
) {
    val context = LocalContext.current

    var tracks by remember { mutableStateOf<List<AudioTrack>>(emptyList()) }
    var selectedIndex by remember { mutableIntStateOf(0) }
    var isPlaying by remember { mutableStateOf(false) }
    var isFullScreen by remember { mutableStateOf(false) }
    var folderPath by remember { mutableStateOf(initialFolderPath) }

    LaunchedEffect(folderPath) {
        tracks = MusicRepository.getAudioTracks(context, folderPath)
        if (tracks.isNotEmpty() && selectedIndex >= tracks.size) {
            selectedIndex = 0
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
            onTrackSelected = { selectedIndex = it },
            onPlay = {
                val track = tracks.getOrNull(selectedIndex) ?: return@iPodView
                try {
                    val mediaItem = MediaItem.fromUri(Uri.parse(track.uri))
                    exoPlayer.setMediaItem(mediaItem)
                    exoPlayer.prepare()
                    exoPlayer.play()
                    isPlaying = true
                } catch (e: Exception) {
                    e.printStackTrace() // смотри в Logcat!
                }
            },
            onMore = { isFullScreen = true },
            onFolderSelect = onFolderSelect
        )
    }
}

// =============== iPodView ===============

@Composable
fun iPodView(
    tracks: List<AudioTrack>,
    selectedIndex: Int,
    onTrackSelected: (Int) -> Unit,
    onPlay: () -> Unit,
    onMore: () -> Unit,
    onFolderSelect: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .border(2.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
            .padding(8.dp)
    ) {
        // Верх: список треков + счётчик
// Внутри iPodView, в Box с weight(1f):

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color(0xFFE8F0E8), RoundedCornerShape(8.dp)) // фон "экрана"
                .padding(8.dp)
                .border(1.dp, Color(0xFFA8B5A0), RoundedCornerShape(8.dp)) // внутренняя рамка
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(tracks.size) { index ->
                    TrackRow(
                        track = tracks[index],
                        isSelected = index == selectedIndex,
                        onClick = { onTrackSelected(index) }
                    )
                }
            }

            // Счётчик треков
            Text(
                text = "${selectedIndex + 1} / ${tracks.size}",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF556B55),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .background(Color.White.copy(alpha = 0.8f), CircleShape)
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            )

            // Кнопка папки
            IconButton(
                onClick = onFolderSelect,
                modifier = Modifier.align(Alignment.TopStart)
            ) {
                Icon(Icons.Default.Folder, "Select Folder", tint = Color(0xFF556B55))
            }
        }

        // Низ: колесо + кнопки (включая Folder)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(top = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            ClickWheel(
                isSeekMode = false,
                onScroll = { direction ->
                    val newIndex = (selectedIndex + direction.toInt()).coerceIn(0, tracks.size - 1)
                    onTrackSelected(newIndex)
                }
            )

            Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(Color.Gray, CircleShape)
                    .clickable { onPlay() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }

            Row(
                modifier = Modifier
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

@Composable
fun ClickWheel(
    isSeekMode: Boolean = false,
    onScroll: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var accumulatedDelta by remember { mutableStateOf(0f) }
    val rotation = remember { Animatable(0f) }
    val coroutineScope = rememberCoroutineScope()

    val vibrate = remember {
        {
            try {
                val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                    vm.defaultVibrator
                } else {
                    @Suppress("DEPRECATION")
                    context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(20, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(20)
                }
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    LaunchedEffect(isSeekMode) {
        rotation.snapTo(0f)
    }

    Box(
        modifier = modifier
            .size(240.dp)
            .background(Color(0xFFE0E0E0), shape = CircleShape)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDrag = { change, dragAmount ->
                        // Инвертируем: вверх = +1
                        val deltaDp = -dragAmount.y
                        accumulatedDelta += deltaDp

                        val threshold = 30f
                        while (accumulatedDelta >= threshold) {
                            onScroll(1f)
                            vibrate()
                            accumulatedDelta -= threshold
                        }
                        while (accumulatedDelta <= -threshold) {
                            onScroll(-1f)
                            vibrate()
                            accumulatedDelta += threshold
                        }

                        val targetRotation = accumulatedDelta * 2f
                        if (rotation.targetValue != targetRotation) {
                            coroutineScope.launch {
                                rotation.animateTo(
                                    targetRotation,
                                    animationSpec = tween(80, easing = LinearEasing)
                                )
                            }
                        }
                    },
                    onDragEnd = {
                        coroutineScope.launch {
                            rotation.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                        }
                        accumulatedDelta = 0f
                    }
                )
            }
            .graphicsLayer { rotationZ = rotation.value },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val radius = size.minDimension / 2f - 20f
            repeat(12) { i ->
                val angle = (i * 30f - 90f).toRadians()
                drawLine(
                    color = Color.Gray,
                    start = Offset(
                        x = size.width / 2f + cos(angle) * radius,
                        y = size.height / 2f + sin(angle) * radius
                    ),
                    end = Offset(
                        x = size.width / 2f + cos(angle) * (radius + 15f),
                        y = size.height / 2f + sin(angle) * (radius + 15f)
                    ),
                    strokeWidth = 4f
                )
            }
        }
    }
}

private fun Float.toRadians() = this * (kotlin.math.PI.toFloat() / 180f)

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
    // Обновляем позицию каждые 100 мс для плавного прогресса
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
            isSeekMode = true,
            onScroll = { direction ->
                if (exoPlayer.duration <= 0) return@ClickWheel
                val stepMs = 5000L
                val newPosition = (exoPlayer.currentPosition + direction * stepMs).toLong()
                    .coerceIn(0L, exoPlayer.duration)
                exoPlayer.seekTo(newPosition)
                currentPosition = newPosition // синхронизируем
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp)
        )

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp)
                .size(240.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(Color(0xFF556B55), CircleShape)
                    .clickable { onPlayPause() },
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
}