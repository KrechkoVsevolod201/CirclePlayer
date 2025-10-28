// файл: app/src/main/java/com/example/circleplayer/PlayerComposables.kt
package com.example.circleplayer

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
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
import kotlinx.coroutines.flow.collect
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Stop
import androidx.compose.ui.platform.LocalContext

@Composable
fun MusicPlayerApp(exoPlayer: ExoPlayer) {
    val context = LocalContext.current

    var tracks by remember { mutableStateOf<List<AudioTrack>>(emptyList()) }
    var selectedIndex by remember { mutableIntStateOf(0) }
    var isPlaying by remember { mutableStateOf(false) }
    var isFullScreen by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        tracks = MusicRepository.getAudioTracks(context)
    }

    // Следим за состоянием плеера


    if (isFullScreen) {
        FullScreenPlayer(
            track = tracks.getOrNull(selectedIndex),
            exoPlayer = exoPlayer,
            isPlaying = isPlaying,
            onPlayPause = {
                exoPlayer.playWhenReady = !exoPlayer.playWhenReady
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
                val mediaItem = MediaItem.fromUri(Uri.parse(track.uri))
                exoPlayer.setMediaItem(mediaItem)
                exoPlayer.prepare()
                exoPlayer.play()
            },
            onMore = { isFullScreen = true }
        )
    }
}

@Composable
fun iPodView(
    tracks: List<AudioTrack>,
    selectedIndex: Int,
    onTrackSelected: (Int) -> Unit,
    onPlay: () -> Unit,
    onMore: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .border(2.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp)) // 👈 граница
            .padding(8.dp)
    ) {
        // Верх: список треков + счётчик
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
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

            // Счётчик треков в правом верхнем углу
            Text(
                text = "${selectedIndex + 1} / ${tracks.size}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .background(Color.White.copy(alpha = 0.7f), CircleShape)
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            )
        }

        // Низ: колесо и кнопки
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
                IconButton(onClick = { /* Назад не нужен */ }) {
                    Icon(Icons.Default.ArrowBack, "Back", tint = Color.Gray)
                }
                IconButton(onClick = onMore) {
                    Icon(Icons.Default.MoreVert, "More", tint = Color.Gray)
                }
            }
        }
    }
}

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

    // Функция вибрации
    val vibrate = remember {
        @androidx.annotation.RequiresPermission(android.Manifest.permission.VIBRATE) {
            try {
                val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                    vibratorManager.defaultVibrator
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
                // Игнорируем, если нет вибрации
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
                        val deltaDp = dragAmount.y
                        accumulatedDelta += deltaDp

                        val threshold = 30f
                        while (accumulatedDelta.absoluteValue >= threshold) {
                            val direction = if (accumulatedDelta > 0) 1f else -1f
                            onScroll(direction)
                            vibrate() // 👈 виброотклик при каждом шаге
                            accumulatedDelta -= direction * threshold
                        }

                        val targetRotation = -accumulatedDelta * 2f
                        if (rotation.targetValue != targetRotation) {
                            coroutineScope.launch {
                                rotation.animateTo(
                                    targetRotation,
                                    animationSpec = tween(durationMillis = 100, easing = LinearEasing)
                                )
                            }
                        }
                    },
                    onDragEnd = {
                        coroutineScope.launch {
                            rotation.animateTo(
                                0f,
                                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
                            )
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

@Composable
fun FullScreenPlayer(
    track: AudioTrack?,
    exoPlayer: ExoPlayer,
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
    onBack: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        // Кнопка назад
        IconButton(
            onClick = onBack,
            modifier = Modifier.align(Alignment.TopStart).padding(16.dp)
        ) {
            Icon(Icons.Default.ArrowBack, "Back")
        }

        // Таймлайн вверху
        track?.let {
            val currentPosition by remember { derivedStateOf { exoPlayer.currentPosition } }
            val duration by remember { derivedStateOf { exoPlayer.duration } }

            val currentTime = if (duration > 0) formatTime(currentPosition) else "--:--"
            val totalTime = if (duration > 0) formatTime(duration) else "--:--"

            Text(
                text = "$currentTime / $totalTime",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 16.dp)
            )
        }

        // Основной контент по центру
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp)
        ) {
            track?.let {
                Text(it.title, style = MaterialTheme.typography.headlineMedium)
                Spacer(modifier = Modifier.height(4.dp))
                Text(it.artist, style = MaterialTheme.typography.titleMedium)
            }
        }

        // Колесо внизу
        ClickWheel(
            isSeekMode = true,
            onScroll = { direction ->
                if (exoPlayer.duration <= 0) return@ClickWheel
                val stepMs = 5000L
                val newPosition = (exoPlayer.currentPosition + direction * stepMs).toLong()
                    .coerceIn(0L, exoPlayer.duration)
                exoPlayer.seekTo(newPosition)
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp)
        )

        // Кнопка Play в центре колеса (поверх всего)
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
                    .background(Color.Gray, CircleShape)
                    .clickable { onPlayPause() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    }
}

// Вспомогательная функция форматирования времени
private fun formatTime(ms: Long): String {
    val totalSeconds = (ms / 1000).toInt()
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}