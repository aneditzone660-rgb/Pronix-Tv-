package com.example.player

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.os.Build
import android.util.Log
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.*
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.data.model.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(UnstableApi::class)
@Composable
fun IptvPlayerComposable(
    channel: Channel,
    onBack: (Long) -> Unit,
    modifier: Modifier = Modifier,
    initialPosition: Long = 0L
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Find host activity for orientation and PiP
    val activity = remember(context) { context.findActivity() }

    // ExoPlayer State
    var playerState by remember { mutableStateOf(Player.STATE_IDLE) }
    var isPlayingState by remember { mutableStateOf(false) }
    var currentPlaybackPosition by remember { mutableStateOf(0L) }
    var duration by remember { mutableStateOf(0L) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var retryCount by remember { mutableIntStateOf(0) }
    var isBuffering by remember { mutableStateOf(false) }

    // Controller overlay state
    var showControls by remember { mutableStateOf(true) }
    var playbackSpeed by remember { mutableFloatStateOf(1.0f) }
    var resizeMode by remember { mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) } // FIT, FILL, ZOOM
    var isLocked by remember { mutableStateOf(false) }

    // Tracks & Options Dialog
    var showTrackSelectionDialog by remember { mutableStateOf(false) }
    var showSpeedDialog by remember { mutableStateOf(false) }
    var availableVideoTracks by remember { mutableStateOf<List<TrackInfo>>(emptyList()) }
    var availableAudioTracks by remember { mutableStateOf<List<TrackInfo>>(emptyList()) }
    var selectedVideoTrackIndex by remember { mutableIntStateOf(-1) }
    var selectedAudioTrackIndex by remember { mutableIntStateOf(-1) }

    // Initialize ExoPlayer
    val player = remember(channel.id) {
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                15_000, // Min buffer before starting playback (15s)
                50_000, // Max buffer size (50s)
                1_500,  // Buffer required to resume after user pause/seek (1.5s)
                3_000   // Buffer required after rebuffering occurs (3s)
            )
            .build()

        ExoPlayer.Builder(context)
            .setLoadControl(loadControl)
            .setSeekParameters(SeekParameters.CLOSEST_SYNC)
            .build().apply {
                val mediaItem = MediaItem.Builder()
                    .setUri(channel.streamUrl)
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(channel.name)
                            .setArtist(channel.category)
                            .build()
                    )
                    .build()
                setMediaItem(mediaItem)
                playWhenReady = true
                if (initialPosition > 0) {
                    seekTo(initialPosition)
                }
                prepare()
            }
    }

    // Handle Player Callbacks
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                playerState = state
                isBuffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_READY) {
                    duration = player.duration.coerceAtLeast(0L)
                    errorMessage = null
                    retryCount = 0 // Reset retry count upon successful load
                    // Read tracks
                    readTracks(player) { videoTracks, audioTracks ->
                        availableVideoTracks = videoTracks
                        availableAudioTracks = audioTracks
                    }
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                isPlayingState = isPlaying
            }

            override fun onPlayerError(error: PlaybackException) {
                Log.e("IptvPlayer", "Player error: ${error.message}", error)
                isBuffering = false
                errorMessage = "Playback Error: ${error.localizedMessage ?: "Source connection failed"}"

                // Auto-retry / Auto-reconnect logic
                if (retryCount < 3) {
                    coroutineScope.launch {
                        errorMessage = "Reconnecting stream... (Attempt ${retryCount + 1}/3)"
                        delay(3000)
                        retryCount++
                        player.prepare()
                        player.play()
                    }
                } else {
                    errorMessage = "Unable to connect. Please check your internet connection or stream source."
                }
            }

            override fun onTracksChanged(tracks: Tracks) {
                readTracks(player) { videoTracks, audioTracks ->
                    availableVideoTracks = videoTracks
                    availableAudioTracks = audioTracks
                }
            }
        }

        player.addListener(listener)

        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    // Periodic time updates
    LaunchedEffect(player, isPlayingState) {
        while (isPlayingState) {
            currentPlaybackPosition = player.currentPosition
            delay(1000)
        }
    }

    // Auto-hide controller overlay
    LaunchedEffect(showControls, isPlayingState) {
        if (showControls && isPlayingState && !isLocked) {
            delay(5000)
            showControls = false
        }
    }

    // Fullscreen behavior on entry/exit
    DisposableEffect(Unit) {
        // Force Landscape for full screen experience
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        // Hide System UI bars
        activity?.setSystemBarsVisible(false)

        onDispose {
            // Restore Portrait/Unspecified on exit
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            // Restore System UI bars
            activity?.setSystemBarsVisible(true)
        }
    }

    // Back button handling
    BackHandler {
        onBack(player.currentPosition)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag("full_screen_player_container")
    ) {
        // Player Surface View
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    this.player = player
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    this.resizeMode = resizeMode
                }
            },
            update = { view ->
                view.resizeMode = resizeMode
            },
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    if (!isLocked) {
                        showControls = !showControls
                    } else {
                        // If locked, clicking shows lock icon momentarily to unlock
                        showControls = true
                    }
                }
        )

        // Buffering Indicator
        if (isBuffering) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 4.dp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Buffering Live Stream...",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // Error & Retry Overlay
        errorMessage?.let { error ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.8f))
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.widthIn(max = 450.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Filled.Warning,
                            contentDescription = "Error",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Playback Connection Issue",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = error,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f),
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedButton(
                                onClick = { onBack(player.currentPosition) },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onErrorContainer)
                            ) {
                                Text("Go Back")
                            }
                            Button(
                                onClick = {
                                    errorMessage = null
                                    retryCount = 0
                                    player.prepare()
                                    player.play()
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error,
                                    contentColor = MaterialTheme.colorScheme.onError
                                )
                            ) {
                                Text("Retry Now")
                            }
                        }
                    }
                }
            }
        }

        // Custom Overlaid Player Controllers
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
            ) {
                // LOCK BUTTON (left edge center)
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .align(Alignment.CenterStart)
                        .padding(start = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    IconButton(
                        onClick = { isLocked = !isLocked },
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                            .size(50.dp)
                    ) {
                        Icon(
                            imageVector = if (isLocked) Icons.Filled.Lock else Icons.Filled.LockOpen,
                            contentDescription = "Lock Controls",
                            tint = if (isLocked) MaterialTheme.colorScheme.primary else Color.White
                        )
                    }
                }

                if (!isLocked) {
                    // TOP BAR CONTROLS
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopCenter)
                            .background(Color.Transparent)
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { onBack(player.currentPosition) },
                            modifier = Modifier.background(Color.Black.copy(alpha = 0.4f), CircleShape)
                        ) {
                            Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Column {
                            Text(
                                text = channel.name,
                                color = Color.White,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Live IPTV Stream • ${channel.category.replace("cat_", "").capitalize()}",
                                color = Color.White.copy(alpha = 0.7f),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }

                        Spacer(modifier = Modifier.weight(1.0f))

                        // Aspect Ratio Control
                        IconButton(
                            onClick = {
                                resizeMode = when (resizeMode) {
                                    AspectRatioFrameLayout.RESIZE_MODE_FIT -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                                    AspectRatioFrameLayout.RESIZE_MODE_FILL -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                    else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                                }
                            },
                            modifier = Modifier.background(Color.Black.copy(alpha = 0.4f), CircleShape)
                        ) {
                            val aspectIcon = when (resizeMode) {
                                AspectRatioFrameLayout.RESIZE_MODE_FIT -> Icons.Outlined.AspectRatio
                                AspectRatioFrameLayout.RESIZE_MODE_FILL -> Icons.Filled.Fullscreen
                                else -> Icons.Filled.CropFree
                            }
                            Icon(aspectIcon, contentDescription = "Aspect Ratio", tint = Color.White)
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        // Audio and Video Track Quality Selector
                        IconButton(
                            onClick = { showTrackSelectionDialog = true },
                            modifier = Modifier.background(Color.Black.copy(alpha = 0.4f), CircleShape)
                        ) {
                            Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = Color.White)
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        // Picture in Picture
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            IconButton(
                                onClick = {
                                    activity?.enterPictureInPictureMode()
                                },
                                modifier = Modifier.background(Color.Black.copy(alpha = 0.4f), CircleShape)
                            ) {
                                Icon(Icons.Filled.PictureInPicture, contentDescription = "PiP Mode", tint = Color.White)
                            }
                        }
                    }

                    // CENTER PLAY/PAUSE CONTROLS
                    Row(
                        modifier = Modifier.align(Alignment.Center),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(32.dp)
                    ) {
                        // Rewind 10s (if seekable)
                        IconButton(
                            onClick = { player.seekTo((player.currentPosition - 10000).coerceAtLeast(0)) },
                            modifier = Modifier
                                .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                                .size(48.dp)
                        ) {
                            Icon(Icons.Filled.Replay10, contentDescription = "Rewind 10s", tint = Color.White, modifier = Modifier.size(28.dp))
                        }

                        // Play/Pause Toggle
                        IconButton(
                            onClick = {
                                if (isPlayingState) player.pause() else player.play()
                            },
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.primary, CircleShape)
                                .size(64.dp)
                        ) {
                            Icon(
                                imageVector = if (isPlayingState) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = "Play/Pause",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(36.dp)
                            )
                        }

                        // Fast Forward 10s
                        IconButton(
                            onClick = { player.seekTo((player.currentPosition + 10000).coerceAtMost(duration)) },
                            modifier = Modifier
                                .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                                .size(48.dp)
                        ) {
                            Icon(Icons.Filled.Forward10, contentDescription = "Forward 10s", tint = Color.White, modifier = Modifier.size(28.dp))
                        }
                    }

                    // BOTTOM CONTROL BAR
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .background(Color.Transparent)
                            .padding(bottom = 8.dp)
                    ) {
                        // Progress Slider Row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = formatTime(currentPlaybackPosition),
                                color = Color.White,
                                style = MaterialTheme.typography.bodySmall
                            )

                            Slider(
                                value = if (duration > 0) currentPlaybackPosition.toFloat() else 0f,
                                onValueChange = { pos ->
                                    player.seekTo(pos.toLong())
                                },
                                valueRange = 0f..(duration.toFloat().coerceAtLeast(1f)),
                                colors = SliderDefaults.colors(
                                    thumbColor = MaterialTheme.colorScheme.primary,
                                    activeTrackColor = MaterialTheme.colorScheme.primary,
                                    inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                                ),
                                modifier = Modifier.weight(1.0f)
                            )

                            Text(
                                text = if (duration > 0) formatTime(duration) else "Live",
                                color = if (duration > 0) Color.White else MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = if (duration > 0) FontWeight.Normal else FontWeight.Bold
                            )
                        }

                        // Playback Settings (Speed, resizing, lock status overlay)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(
                                onClick = { showSpeedDialog = true },
                                colors = ButtonDefaults.textButtonColors(contentColor = Color.White)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.Speed, contentDescription = null, size = 18.dp)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Speed: ${playbackSpeed}x")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // TRACK & QUALITY SELECTION DIALOG
    if (showTrackSelectionDialog) {
        AlertDialog(
            onDismissRequest = { showTrackSelectionDialog = false },
            title = { Text("Quality & Audio Setup") },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Video Resolution Quality",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )

                    if (availableVideoTracks.isEmpty()) {
                        Text("Auto Resolution Only", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                    } else {
                        // Auto Option
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    player.trackSelectionParameters = player.trackSelectionParameters
                                        .buildUpon()
                                        .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
                                        .build()
                                    selectedVideoTrackIndex = -1
                                    showTrackSelectionDialog = false
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = selectedVideoTrackIndex == -1, onClick = null)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Automatic (Best Adaptive)")
                        }

                        availableVideoTracks.forEachIndexed { idx, track ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        setTrackOverride(player, C.TRACK_TYPE_VIDEO, track.groupIndex, track.trackIndex)
                                        selectedVideoTrackIndex = idx
                                        showTrackSelectionDialog = false
                                    }
                                    .padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(selected = selectedVideoTrackIndex == idx, onClick = null)
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(track.name)
                            }
                        }
                    }

                    Divider(modifier = Modifier.padding(vertical = 16.dp))

                    Text(
                        text = "Audio Track Language",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )

                    if (availableAudioTracks.isEmpty()) {
                        Text("Default Audio Track", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                    } else {
                        availableAudioTracks.forEachIndexed { idx, track ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        setTrackOverride(player, C.TRACK_TYPE_AUDIO, track.groupIndex, track.trackIndex)
                                        selectedAudioTrackIndex = idx
                                        showTrackSelectionDialog = false
                                    }
                                    .padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(selected = selectedAudioTrackIndex == idx, onClick = null)
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(track.name)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showTrackSelectionDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    // PLAYBACK SPEED DIALOG
    if (showSpeedDialog) {
        val speeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)
        AlertDialog(
            onDismissRequest = { showSpeedDialog = false },
            title = { Text("Select Playback Speed") },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    speeds.forEach { speed ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    playbackSpeed = speed
                                    player.setPlaybackSpeed(speed)
                                    showSpeedDialog = false
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = playbackSpeed == speed, onClick = null)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("${speed}x" + (if (speed == 1.0f) " (Normal)" else ""))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSpeedDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

// Format media miliseconds into clean string MM:SS or HH:MM:SS
private fun formatTime(ms: Long): String {
    val totalSecs = ms / 1000
    val hours = totalSecs / 3600
    val mins = (totalSecs % 3600) / 60
    val secs = totalSecs % 60
    return if (hours > 0) {
        String.format("%02d:%02d:%02d", hours, mins, secs)
    } else {
        String.format("%02d:%02d", mins, secs)
    }
}

// Helper to find parent Activity from context
private fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

// Set System Bars visibility on host activity
private fun Activity.setSystemBarsVisible(visible: Boolean) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val controller = window.insetsController
        if (controller != null) {
            if (visible) {
                controller.show(android.view.WindowInsets.Type.systemBars())
            } else {
                controller.hide(android.view.WindowInsets.Type.systemBars())
                controller.systemBarsBehavior = android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }
    } else {
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = if (visible) {
            android.view.View.SYSTEM_UI_FLAG_VISIBLE
        } else {
            android.view.View.SYSTEM_UI_FLAG_FULLSCREEN or
                    android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        }
    }
}

// Simple Helper Data class for Track Info
data class TrackInfo(
    val groupIndex: Int,
    val trackIndex: Int,
    val name: String
)

@OptIn(UnstableApi::class)
private fun readTracks(player: Player, onTracksRead: (List<TrackInfo>, List<TrackInfo>) -> Unit) {
    val videoTracks = mutableListOf<TrackInfo>()
    val audioTracks = mutableListOf<TrackInfo>()

    val currentTracks = player.currentTracks
    for (groupIndex in 0 until currentTracks.groups.size) {
        val group = currentTracks.groups[groupIndex]
        if (group.type == C.TRACK_TYPE_VIDEO) {
            for (trackIndex in 0 until group.length) {
                val format = group.getTrackFormat(trackIndex)
                val width = format.width
                val height = format.height
                val label = if (width > 0 && height > 0) "${height}p" else "Video Track ${trackIndex + 1}"
                videoTracks.add(TrackInfo(groupIndex, trackIndex, label))
            }
        } else if (group.type == C.TRACK_TYPE_AUDIO) {
            for (trackIndex in 0 until group.length) {
                val format = group.getTrackFormat(trackIndex)
                val lang = format.language ?: "Default"
                val audioLabel = "${lang.uppercase()} (${format.sampleRate} Hz)"
                audioTracks.add(TrackInfo(groupIndex, trackIndex, audioLabel))
            }
        }
    }
    onTracksRead(videoTracks, audioTracks)
}

@OptIn(UnstableApi::class)
private fun setTrackOverride(player: Player, trackType: @C.TrackType Int, groupIndex: Int, trackIndex: Int) {
    val currentTracks = player.currentTracks
    if (groupIndex >= currentTracks.groups.size) return
    val group = currentTracks.groups[groupIndex]
    val trackGroup = group.mediaTrackGroup

    player.trackSelectionParameters = player.trackSelectionParameters
        .buildUpon()
        .setOverrideForType(TrackSelectionOverride(trackGroup, trackIndex))
        .build()
}

// Utility extension for helper icon sizing
@Composable
private fun Icon(imageVector: androidx.compose.ui.graphics.vector.ImageVector, contentDescription: String?, size: androidx.compose.ui.unit.Dp) {
    Icon(
        imageVector = imageVector,
        contentDescription = contentDescription,
        modifier = Modifier.size(size),
        tint = Color.White
    )
}
