package com.kodastream.app.ui.player.compose

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import android.view.View
import android.view.WindowManager
import android.view.SurfaceView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.kodastream.app.ui.theme.KodaStreamTheme
import com.lagradost.cloudstream3.ui.player.CSPlayerLoading

private enum class PlayerSheet { None, Speed, Tracks, Audio, Subtitles, Settings }

/**
 * Fully Compose player UI. The video output is the only platform view (Compose has
 * no native surface); every control, sheet and animation is Compose + M3 Expressive.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ComposePlayerScreen(
    vm: ComposePlayerViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val compact = configuration.screenWidthDp < 600

    val status by vm.status.collectAsState()
    val position by vm.position.collectAsState()
    val duration by vm.duration.collectAsState()
    val speed by vm.speed.collectAsState()
    val title = vm.mediaTitle
    val controls by vm.controlsVisible.collectAsState()
    val error by vm.error.collectAsState()
    val exo by vm.exoPlayer.collectAsState()

    var sheet by remember { mutableStateOf(PlayerSheet.None) }
    val sheetState = rememberModalBottomSheetState()
    var surfaceView by remember { mutableStateOf<SurfaceView?>(null) }
    var seekFraction by remember { mutableFloatStateOf(0f) }
    var seeking by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { vm.load(context) }
    LaunchedEffect(Unit) {
        vm.effects.collect { effect ->
            if (effect is ComposePlayerViewModel.UiEffect.Close) onBack()
        }
    }
    DisposableEffect(Unit) { onDispose { surfaceView = null } }

    LaunchedEffect(exo, surfaceView) {
        exo?.setVideoSurfaceView(surfaceView)
    }

    val fraction = if (duration > 0 && !seeking) {
        (position.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
    } else seekFraction

    KodaStreamTheme(darkTheme = true) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            ) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx: Context ->
                        SurfaceView(ctx).also { sv ->
                            sv.setZOrderMediaOverlay(false)
                            surfaceView = sv
                        }
                    },
                    update = { sv -> exo?.setVideoSurfaceView(sv) },
                )

                // Gesture surface: single tap toggles controls, double tap seeks,
                // vertical drag changes brightness/volume.
                Box(
                    Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onTap = { vm.setControlsVisible(!vm.controlsVisible.value) },
                                onDoubleTap = { offset ->
                                    val forward = offset.x > size.width / 2
                                    vm.seekBy(if (forward) 10_000L else -10_000L)
                                },
                            )
                        }
                        .pointerInput(Unit) {
                            detectHorizontalDragGestures(
                                onHorizontalDrag = { _, delta -> vm.seekBy(delta.toLong() * 50L) },
                            )
                        }
                        .pointerInput(Unit) {
                            detectVerticalDragGestures(
                                onVerticalDrag = { _, delta -> adjustVolume(context, delta) },
                            )
                        }
                )

                val springFloat = spring<Float>(dampingRatio = Spring.DampingRatioMediumBouncy)
    val springOffset = spring<IntOffset>(dampingRatio = Spring.DampingRatioMediumBouncy)

                AnimatedVisibility(
                    visible = controls,
                    enter = fadeIn(animationSpec = springFloat) + slideInVertically(animationSpec = springOffset) { -it },
                    exit = fadeOut(animationSpec = springFloat) + slideOutVertically(animationSpec = springOffset) { -it },
                    modifier = Modifier.align(Alignment.TopCenter),
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.scrim.copy(0.45f))
                            .padding(if (compact) 12.dp else 24.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            title,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                // Center play / pause with spring scale.
                AnimatedVisibility(
                    visible = controls && status != CSPlayerLoading.IsBuffering,
                    enter = fadeIn(animationSpec = springFloat),
                    exit = fadeOut(animationSpec = springFloat),
                    modifier = Modifier.align(Alignment.Center),
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(72.dp),
                        onClick = { vm.playPause() },
                    ) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Icon(
                                when (status) {
                                    CSPlayerLoading.IsPlaying -> Icons.Filled.Pause
                                    CSPlayerLoading.IsEnded -> Icons.Filled.Replay
                                    else -> Icons.Filled.PlayArrow
                                },
                                contentDescription = "Play or pause",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(36.dp),
                            )
                        }
                    }
                }

                if (status == CSPlayerLoading.IsBuffering) {
                    androidx.compose.material3.CircularProgressIndicator(
                        Modifier.align(Alignment.Center),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                AnimatedVisibility(
                    visible = controls,
                    enter = fadeIn(animationSpec = springFloat) + slideInVertically(animationSpec = springOffset) { it },
                    exit = fadeOut(animationSpec = springFloat) + slideOutVertically(animationSpec = springOffset) { it },
                    modifier = Modifier.align(Alignment.BottomCenter),
                ) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.scrim.copy(0.55f))
                            .padding(horizontal = if (compact) 12.dp else 24.dp, vertical = 12.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TimeLabel(position)
                            Box(Modifier.weight(1f).height(32.dp)) {
                                LinearWavyProgressIndicator(
                                    progress = { fraction },
                                    modifier = Modifier.fillMaxWidth().align(Alignment.Center),
                                )
                                Slider(
                                    value = fraction,
                                    onValueChange = {
                                        seeking = true
                                        seekFraction = it
                                    },
                                    onValueChangeFinished = {
                                        vm.seekTo((seekFraction * duration).toLong())
                                        seeking = false
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                            TimeLabel(duration)
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            SmallControl(Icons.Filled.Subtitles, "Subtitles") { sheet = PlayerSheet.Subtitles }
                            SmallControl(Icons.Filled.Audiotrack, "Audio") { sheet = PlayerSheet.Audio }
                            Text(
                                "${speed}x",
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier
                                    .padding(horizontal = 8.dp)
                                    .background(
                                        MaterialTheme.colorScheme.secondaryContainer,
                                        CircleShape,
                                    )
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                            )
                            SmallControl(Icons.Filled.Settings, "Settings") { sheet = PlayerSheet.Settings }
                        }
                    }
                }

                error?.let { message ->
                    androidx.compose.material3.Snackbar(
                        Modifier.align(Alignment.BottomCenter).padding(16.dp),
                        dismissAction = { Text("OK") },
                    ) { Text(message) }
                }
            }

        if (sheet != PlayerSheet.None) {
            PlayerSheetContent(vm, sheet, onDismiss = { sheet = PlayerSheet.None }, sheetState)
        }
    }
}

@Composable
private fun SmallControl(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(icon, contentDescription = label, tint = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun TimeLabel(ms: Long) {
    val total = ms / 1000
    Text(
        "%02d:%02d".format(total / 60, total % 60),
        color = Color.White,
        style = MaterialTheme.typography.labelLarge,
    )
}

private fun adjustVolume(context: Context, delta: Float) {
    val manager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
    manager.adjustStreamVolume(
        AudioManager.STREAM_MUSIC,
        if (delta > 0) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER,
        AudioManager.FLAG_SHOW_UI,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PlayerSheetContent(
    vm: ComposePlayerViewModel,
    sheet: PlayerSheet,
    onDismiss: () -> Unit,
    sheetState: androidx.compose.material3.SheetState,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        when (sheet) {
            PlayerSheet.Speed -> {
                Text("Playback speed", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(16.dp))
                val speeds = listOf(0.25f, 0.5f, 1f, 1.25f, 1.5f, 2f)
                val current by vm.speed.collectAsState()
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
                ) {
                    speeds.forEachIndexed { index, value ->
                        ToggleButton(
                            checked = current == value,
                            onCheckedChange = { vm.setSpeed(value) },
                            shapes = when (index) {
                                0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                                speeds.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                                else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                            },
                            modifier = Modifier.weight(1f).height(48.dp),
                        ) { Text("${value}x") }
                    }
                }
            }
            PlayerSheet.Subtitles, PlayerSheet.Audio, PlayerSheet.Tracks, PlayerSheet.Settings -> {
                Text(
                    when (sheet) {
                        PlayerSheet.Subtitles -> "Subtitles"
                        PlayerSheet.Audio -> "Audio tracks"
                        PlayerSheet.Tracks -> "Video tracks"
                        else -> "Settings"
                    },
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(16.dp),
                )
            }
            PlayerSheet.None -> Unit
        }
        Spacer(Modifier.height(24.dp))
    }
}
