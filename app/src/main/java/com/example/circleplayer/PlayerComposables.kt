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
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.DisposableEffect
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
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
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.example.circleplayer.audio.EffectsManager
import com.example.circleplayer.audio.EffectsRenderersFactory
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.content.Context
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlin.math.atan2
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

@UnstableApi
@Composable
fun MusicPlayerApp(
    initialExoPlayer: ExoPlayer,
    effectsManager: EffectsManager,
    initialFolderPath: String? = null,
    onFolderSelect: () -> Unit
) {
    val context = LocalContext.current
    val appContext = context.applicationContext

    var currentFolderPath by remember { mutableStateOf(initialFolderPath) }
    var lastKnownFolderPath by remember { mutableStateOf<String?>(null) }

    var selectedIndex by rememberSaveable { mutableIntStateOf(0) }

    var tracks by remember { mutableStateOf<List<AudioTrack>>(emptyList()) }
    var isPlaying by remember { mutableStateOf(false) }
    var isFullScreen by remember { mutableStateOf(false) }
    var showEffectsMenu by remember { mutableStateOf(false) }

    var useEffects by remember { mutableStateOf(false) }
    var previousUseEffects by remember { mutableStateOf(false) }
    var currentPlayer by remember { mutableStateOf(initialExoPlayer) }
    var playerGeneration by remember { mutableIntStateOf(0) }

    var lastScrollTime by remember { mutableLongStateOf(0L) }

    // Sync folder path from Activity when picker updates prefs-backed state
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
                    showEffectsMenu -> showEffectsMenu = false
                    isFullScreen -> isFullScreen = false
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

    if (showEffectsMenu) {
        EffectsMenu(
            effectsManager = effectsManager,
            useEffects = useEffects,
            onUseEffectsChange = { useEffects = it },
            onBack = { showEffectsMenu = false }
        )
    } else if (isFullScreen) {
        FullScreenPlayer(
            track = tracks.getOrNull(selectedIndex),
            exoPlayer = currentPlayer,
            isPlaying = isPlaying,
            onPlayPause = {
                isPlaying = !isPlaying
                currentPlayer.playWhenReady = isPlaying
                if (isPlaying) currentPlayer.play() else currentPlayer.pause()
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
                currentPlayer.playWhenReady = isPlaying
                if (isPlaying && tracks.isNotEmpty()) {
                    val track = tracks.getOrNull(selectedIndex)
                        ?: tracks.first().also { selectedIndex = 0 }
                    val mediaItem = MediaItem.fromUri(Uri.parse(track.uri))
                    val currentUri = currentPlayer.currentMediaItem?.localConfiguration?.uri?.toString()
                    if (currentUri != track.uri) {
                        currentPlayer.setMediaItem(mediaItem)
                        currentPlayer.prepare()
                    }
                    currentPlayer.play()
                } else {
                    currentPlayer.pause()
                }
            },
            onMore = { isFullScreen = true },
            onEffectsClick = { showEffectsMenu = true },
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
    onEffectsClick: () -> Unit,
    onFolderSelect: () -> Unit,
    onScroll: (Int) -> Unit
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
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                IconButton(onClick = { /* Back */ }) {
                    Icon(Icons.Default.ArrowBack, "Back", tint = Color.Gray)
                }
                IconButton(onClick = onEffectsClick) {
                    Icon(Icons.Default.MusicNote, "Effects", tint = Color(0xFF556B55))
                }
                IconButton(onClick = onFolderSelect) {
                    Icon(Icons.Default.Folder, "Select Folder", tint = Color(0xFF556B55))
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

    // Защита от слишком быстрого скролла
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

    // Отслеживаем угловое положение
    var startAngle by remember { mutableStateOf(0f) }
    var lastAngle by remember { mutableStateOf(0f) }
    var accumulatedRotation by remember { mutableStateOf(0f) }
    var lastReportedStep by remember { mutableStateOf(0) }

    Box(
        modifier = modifier
            .size(240.dp)
            .background(Color(0xFFE0E0E0), shape = CircleShape)
            .pointerInput(Unit) {
                val center = Offset((size.width / 2).toFloat(), (size.height / 2).toFloat())

                detectDragGestures(
                    onDragStart = { offset ->
                        // Вычисляем начальный угол относительно центра
                        startAngle = calculateAngle(offset, center)
                        lastAngle = startAngle
                        accumulatedRotation = 0f
                        lastReportedStep = 0
                        lastScrollTime = System.currentTimeMillis()
                    },
                    onDrag = { change, _ ->
                        val now = System.currentTimeMillis()
                        if (now - lastScrollTime < 50) return@detectDragGestures

                        // Вычисляем текущий угол
                        val currentAngle = calculateAngle(change.position, center)

                        // Вычисляем изменение угла
                        var deltaAngle = currentAngle - lastAngle

                        // Обрабатываем переход через 0/360 градусов
                        if (deltaAngle > 180f) deltaAngle -= 360f
                        if (deltaAngle < -180f) deltaAngle += 360f

                        accumulatedRotation += deltaAngle
                        lastAngle = currentAngle

                        // Определяем шаг (каждые 30 градусов = 1 шаг)
                        val stepSize = 30f
                        val currentStep = (accumulatedRotation / stepSize).toInt()

                        if (currentStep != lastReportedStep) {
                            val diff = currentStep - lastReportedStep
                            // Положительное значение = по часовой стрелке (вперед)
                            // Отрицательное значение = против часовой стрелки (назад)
                            onScroll(diff)
                            vibrate()
                            lastReportedStep = currentStep
                            lastScrollTime = now
                        }

                        // Визуальное вращение колеса
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
            .graphicsLayer { rotationZ = rotation.value },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Рисуем метки на колесе для визуализации вращения
            val radius = size.minDimension / 2
            for (i in 0 until 12) {
                val angle = Math.toRadians((i * 30).toDouble())
                val x = center.x + (radius * 0.85f * cos(angle)).toFloat()
                val y = center.y + (radius * 0.85f * sin(angle)).toFloat()
                drawCircle(
                    color = Color(0xFF888888),
                    radius = 8f,
                    center = Offset(x, y)
                )
            }
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

// Вспомогательная функция для вычисления угла относительно центра
private fun calculateAngle(point: Offset, center: Offset): Float {
    val dx = point.x - center.x
    val dy = point.y - center.y
    return Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
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

        IconButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp)
        ) {
            Icon(Icons.Default.ArrowBack, "Back", tint = Color(0xFF556B55))
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
fun EffectsMenu(
    effectsManager: EffectsManager,
    useEffects: Boolean,
    onUseEffectsChange: (Boolean) -> Unit,
    onBack: () -> Unit
) {
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
    
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Text(
                text = "Эффекты плёнки",
                style = MaterialTheme.typography.headlineMedium,
                color = Color(0xFF333333),
                modifier = Modifier.padding(bottom = 16.dp)
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFD0E8D0))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Включить эффекты",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color(0xFF333333)
                        )
                        Text(
                            text = "Переключает режим воспроизведения",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF556B55)
                        )
                    }
                    Switch(
                        checked = useEffects,
                        onCheckedChange = onUseEffectsChange
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F0E8))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Wow & Flutter",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color(0xFF333333)
                    )
                    Switch(
                        checked = wowEnabled,
                        onCheckedChange = { 
                            wowEnabled = it
                            effectsManager.wowFlutter.enabled = it
                        }
                    )
                }
                Text(
                    text = "Неравномерность скорости воспроизведения",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF556B55)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text("Глубина: ${"%.2f".format(wowDepth)}", style = MaterialTheme.typography.bodySmall)
                Slider(
                    value = wowDepth,
                    onValueChange = { 
                        wowDepth = it
                        effectsManager.wowFlutter.depth = it
                    },
                    valueRange = 0f..1f
                )
                Text("Частота: ${"%.2f".format(wowRate)} Гц", style = MaterialTheme.typography.bodySmall)
                Slider(
                    value = wowRate,
                    onValueChange = { 
                        wowRate = it
                        effectsManager.wowFlutter.rate = it
                    },
                    valueRange = 0.1f..5f
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F0E8))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Volume Detonation",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color(0xFF333333)
                    )
                    Switch(
                        checked = detonationEnabled,
                        onCheckedChange = { 
                            detonationEnabled = it
                            effectsManager.volumeDetonation.enabled = it
                        }
                    )
                }
                Text(
                    text = "Перегрузка при высокой амплитуде",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF556B55)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text("Степень: ${"%.2f".format(detonationAmount)}", style = MaterialTheme.typography.bodySmall)
                Slider(
                    value = detonationAmount,
                    onValueChange = { 
                        detonationAmount = it
                        effectsManager.volumeDetonation.amount = it
                    },
                    valueRange = 0f..1f
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F0E8))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Chorus",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color(0xFF333333)
                    )
                    Switch(
                        checked = chorusEnabled,
                        onCheckedChange = { 
                            chorusEnabled = it
                            effectsManager.chorus.enabled = it
                        }
                    )
                }
                Text(
                    text = "Эффект хора",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF556B55)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text("Глубина: ${"%.2f".format(chorusDepth)}", style = MaterialTheme.typography.bodySmall)
                Slider(
                    value = chorusDepth,
                    onValueChange = { 
                        chorusDepth = it
                        effectsManager.chorus.depth = it
                    },
                    valueRange = 0f..1f
                )
                Text("Частота: ${"%.2f".format(chorusRate)} Гц", style = MaterialTheme.typography.bodySmall)
                Slider(
                    value = chorusRate,
                    onValueChange = { 
                        chorusRate = it
                        effectsManager.chorus.rate = it
                    },
                    valueRange = 0.1f..5f
                )
                Text("Микс: ${"%.2f".format(chorusMix)}", style = MaterialTheme.typography.bodySmall)
                Slider(
                    value = chorusMix,
                    onValueChange = { 
                        chorusMix = it
                        effectsManager.chorus.mix = it
                    },
                    valueRange = 0f..1f
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F0E8))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Vintage Noise",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color(0xFF333333)
                    )
                    Switch(
                        checked = noiseEnabled,
                        onCheckedChange = { 
                            noiseEnabled = it
                            effectsManager.vintageNoise.enabled = it
                        }
                    )
                }
                Text(
                    text = "Шум и хруст винила",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF556B55)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text("Уровень шума: ${"%.2f".format(noiseLevel)}", style = MaterialTheme.typography.bodySmall)
                Slider(
                    value = noiseLevel,
                    onValueChange = { 
                        noiseLevel = it
                        effectsManager.vintageNoise.noiseLevel = it
                    },
                    valueRange = 0f..1f
                )
                Text("Хруст: ${"%.2f".format(crackleIntensity)}", style = MaterialTheme.typography.bodySmall)
                Slider(
                    value = crackleIntensity,
                    onValueChange = { 
                        crackleIntensity = it
                        effectsManager.vintageNoise.crackleIntensity = it
                    },
                    valueRange = 0f..1f
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        }

        IconButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp)
        ) {
            Icon(Icons.Default.ArrowBack, "Back", tint = Color(0xFF556B55))
        }
    }
}