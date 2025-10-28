// файл: app/src/main/java/com/example/circleplayer/PlayerComposables.kt
package com.example.circleplayer

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
    Column(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(8.dp)
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
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(16.dp),
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
    var accumulatedDelta by remember { mutableStateOf(0f) }
    val rotation = remember { Animatable(0f) }
    val coroutineScope = rememberCoroutineScope() // 👈 ключевая строка

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
                            accumulatedDelta -= direction * threshold
                        }

                        val targetRotation = -accumulatedDelta * 2f
                        if (rotation.targetValue != targetRotation) {
                            // Запускаем анимацию асинхронно
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            track?.let {
                Text(it.title, style = MaterialTheme.typography.headlineMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Text(it.artist, style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(48.dp))
                Button(onClick = onPlayPause) {
                    Text(if (isPlaying) "Pause" else "Play")
                }
            }
        }

        IconButton(
            onClick = onBack,
            modifier = Modifier.align(Alignment.TopStart)
        ) {
            Icon(Icons.Default.ArrowBack, "Back")
        }

        ClickWheel(
            isSeekMode = true,
            onScroll = { direction ->
                if (exoPlayer.duration <= 0) return@ClickWheel
                val currentPosition = exoPlayer.currentPosition
                val stepMs = 5000L
                val newPosition = (currentPosition + direction * stepMs).toLong().coerceIn(0L, exoPlayer.duration)
                exoPlayer.seekTo(newPosition)
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp)
        )
    }
}