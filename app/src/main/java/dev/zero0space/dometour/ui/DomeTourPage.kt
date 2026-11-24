package dev.zero0space.dometour.ui

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.pico.spatial.ui.design.Button
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.Text
import com.pico.spatial.ui.design.windows.Subwindow
import com.pico.spatial.ui.foundation.content.SpatialView
import com.pico.spatial.ui.foundation.gesture.detectSpatialDragGesture
import com.pico.spatial.ui.foundation.gesture.detectSpatialScaleGesture
import dev.zero0space.dometour.scene.CitySceneHandles
import dev.zero0space.dometour.scene.buildCity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

/**
 * Main window: the dome-city diorama plus tour controls. The 3D scene lives
 * in a [SpatialView]; two floating [Subwindow]s carry the district details
 * and the tour timeline. All state lives in this composable — one container,
 * one composition, no cross-container sync.
 */

/** Degrees of yaw per unit of horizontal drag. Tune on the emulator. */
private const val DRAG_YAW_SENSITIVITY = 120f

/** Degrees of pitch per unit of vertical drag. Tune on the emulator. */
private const val DRAG_PITCH_SENSITIVITY = 60f
private const val MIN_SCALE = 0.3f
private const val MAX_SCALE = 1.8f
private const val TOUR_STOP_MS = 8_000L
private const val PHASE_CYCLE_MS = 15_000L

/** Drone patrol speed around the ring road, degrees per second. */
private const val DRONE_SPEED_DEG_PER_SEC = 14f

/** Rotor spin speed, degrees per second (2 revolutions per second). */
private const val ROTOR_SPEED_DEG_PER_SEC = 720f

private const val TAG = "DomeTourPage"

/** Holds the live scene handles outside snapshot state (see DisposableEffect). */
private class SceneHolder {
    var handles: CitySceneHandles? = null
}

@Composable
fun DomeTourPage(modifier: Modifier = Modifier) {
    var selectedDistrict by remember { mutableStateOf(0) }
    var tourPlaying by remember { mutableStateOf(false) }
    var phaseIndex by remember { mutableStateOf(3) } // Start at night: neon reads best.
    var autoCycle by remember { mutableStateOf(true) }
    var showDistrictPanel by remember { mutableStateOf(false) }
    var showTourPanel by remember { mutableStateOf(false) }
    var yaw by remember { mutableFloatStateOf(0f) }
    var pitch by remember { mutableFloatStateOf(-18f) }
    var scale by remember { mutableFloatStateOf(1f) }
    var sceneReady by remember { mutableStateOf(false) }
    var sceneFailed by remember { mutableStateOf(false) }
    val holder = remember { SceneHolder() }
    val context = LocalContext.current
    val phase = DAY_PHASES[phaseIndex]
    val district = DISTRICTS[selectedDistrict]

    // Guided tour: advance one stop at a time while playing.
    LaunchedEffect(tourPlaying) {
        while (tourPlaying) {
            delay(TOUR_STOP_MS)
            selectedDistrict = (selectedDistrict + 1) % DISTRICTS.size
        }
    }

    // Day-night cycle: ~60s full loop while auto-cycle is on.
    LaunchedEffect(autoCycle) {
        while (autoCycle) {
            delay(PHASE_CYCLE_MS)
            phaseIndex = (phaseIndex + 1) % DAY_PHASES.size
        }
    }

    // Loading watchdog: if SpatialView never calls `initial`, the diorama
    // would sit on "加载中…" forever. After a grace period, fail over to
    // the fallback message instead.
    LaunchedEffect(Unit) {
        delay(10_000L)
        if (!sceneReady && !sceneFailed) {
            Log.e(TAG, "Scene init timed out; showing fallback")
            sceneFailed = true
        }
    }

    // Animation driver: Compose frame callbacks advance the drone patrol,
    // rotor spin, and airship bobbing once per frame. This runs in UI-layer
    // code (withFrameNanos), so it does not depend on ECS systems ticking
    // inside SpatialView scenes — an earlier revision drove the drone from a
    // custom DronePatrolSystem, whose per-frame ticking there is unverified
    // and can never be checked here. The tour play/pause state only gates the
    // drone and the rotors (pausing parks the drone in place, exactly as
    // before); the airships bob ambiently either way. Angles live in plain
    // locals, not snapshot state, so animation never triggers recomposition.
    val playingState = rememberUpdatedState(tourPlaying)
    LaunchedEffect(Unit) {
        var lastNanos = -1L
        var droneAngleDeg = 0f
        var rotorAngleDeg = 0f
        var ambientTimeSec = 0f
        var animationFailed = false
        while (true) {
            withFrameNanos { nanos ->
                if (lastNanos < 0L) lastNanos = nanos
                val dtSec = ((nanos - lastNanos) / 1_000_000_000f).coerceIn(0f, 0.1f)
                lastNanos = nanos
                if (playingState.value) {
                    droneAngleDeg =
                        (droneAngleDeg + DRONE_SPEED_DEG_PER_SEC * dtSec) % 360f
                    rotorAngleDeg =
                        (rotorAngleDeg + ROTOR_SPEED_DEG_PER_SEC * dtSec) % 360f
                }
                ambientTimeSec += dtSec
                if (!animationFailed) {
                    try {
                        holder.handles?.applyAnimation(
                            droneAngleDeg, rotorAngleDeg, ambientTimeSec
                        )
                    } catch (e: Exception) {
                        // One bad scene must not spam the log every frame.
                        Log.e(TAG, "Scene animation failed; stopping animation updates", e)
                        animationFailed = true
                    }
                }
            }
        }
    }

    // Scene disposal. The holder (not snapshot state) is read here so
    // onDispose always sees the latest handles.
    DisposableEffect(Unit) {
        onDispose {
            // Null first, then dispose: this closes the window in which a
            // frame callback could still reach already-destroyed handles.
            val h = holder.handles
            holder.handles = null
            h?.dispose()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Title row.
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(text = "赛博穹顶漫游", style = PicoTheme.typography.headlineLarge)
                    Text(
                        text = "环形穹顶城 · 昼夜漫游 — ${phase.name} · ${phase.description}",
                        style = PicoTheme.typography.bodyMedium
                    )
                }
                Spacer(Modifier.weight(1f))
                Button(onClick = { showDistrictPanel = true }) {
                    Text(text = "城区介绍", style = PicoTheme.typography.titleMedium)
                }
                Spacer(Modifier.width(12.dp))
                Button(onClick = { showTourPanel = true }) {
                    Text(text = "导览", style = PicoTheme.typography.titleMedium)
                }
            }

            // Diorama.
            Box(
                modifier =
                    Modifier.fillMaxWidth().weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(phase.skyColor)
            ) {
                SpatialView(
                    modifier =
                        Modifier.fillMaxSize()
                            .pointerInput(Unit) {
                                // If spatial drag gestures fail on this
                                // device, the window must keep working: the
                                // on-screen zoom/reset buttons below are the
                                // guaranteed path. Cancellation is rethrown
                                // so disposal still works.
                                try {
                                    detectSpatialDragGesture(
                                        targetedToEntity = null,
                                        context = context,
                                        onDrag = { value ->
                                            yaw += value.dragAmount.x * DRAG_YAW_SENSITIVITY
                                            pitch =
                                                (pitch + value.dragAmount.y * DRAG_PITCH_SENSITIVITY)
                                                    .coerceIn(-55f, 25f)
                                        }
                                    )
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    Log.w(TAG, "Spatial drag gestures unavailable", e)
                                }
                            }
                            .pointerInput(Unit) {
                                try {
                                    detectSpatialScaleGesture(
                                        targetedToEntity = null,
                                        context = context,
                                        onScale = { value ->
                                            scale = (scale * value.scaleValue)
                                                .coerceIn(MIN_SCALE, MAX_SCALE)
                                        }
                                    )
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    Log.w(TAG, "Spatial scale gestures unavailable", e)
                                }
                            },
                    initial = { content, _ ->
                        // A failed scene build must not take down the window:
                        // title, controls, and subwindow buttons stay usable
                        // and the diorama area shows a fallback message.
                        // Guard against a second call: rebuilding would leak
                        // the first scene and show two overlapping cities.
                        if (holder.handles == null) {
                            try {
                                holder.handles = buildCity(content)
                                if (holder.handles == null) {
                                    Log.e(TAG, "Scene build returned null")
                                    sceneFailed = true
                                } else {
                                    sceneReady = true
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Scene init failed; controls stay available", e)
                                sceneFailed = true
                            }
                        }
                    },
                    update = { _, _ ->
                        if (!sceneFailed) {
                            try {
                                holder.handles?.applyState(
                                    yawDeg = yaw,
                                    pitchDeg = pitch,
                                    scale = scale,
                                    selectedDistrict = selectedDistrict,
                                    phase = phase
                                )
                            } catch (e: Exception) {
                                // Freeze scene updates after the first
                                // failure instead of crashing every frame.
                                Log.e(TAG, "Scene update failed; freezing scene updates", e)
                                sceneFailed = true
                            }
                        }
                    },
                    attachments = {}
                )
                if (sceneFailed) {
                    Text(
                        text = "穹顶城市未能加载 · 窗口控制仍可使用",
                        style = PicoTheme.typography.bodyMedium,
                        modifier = Modifier.align(Alignment.Center)
                    )
                } else if (!sceneReady) {
                    Text(
                        text = "穹顶城市加载中…",
                        style = PicoTheme.typography.bodyMedium,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }

            // District chips.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "城区：", style = PicoTheme.typography.titleMedium)
                DISTRICTS.forEach { item ->
                    Button(
                        onClick = { selectedDistrict = item.index }
                    ) {
                        Text(
                            text = if (item.index == selectedDistrict) "● ${item.name}" else item.name,
                            style = PicoTheme.typography.titleMedium
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                Text(
                    text = "当前：${district.name} · ${district.englishName}",
                    style = PicoTheme.typography.bodyMedium
                )
            }

            // Tour + day-night + zoom controls.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(onClick = { tourPlaying = !tourPlaying }) {
                    Text(
                        text = if (tourPlaying) "暂停导览" else "开始导览",
                        style = PicoTheme.typography.titleMedium
                    )
                }
                Button(
                    onClick = {
                        selectedDistrict =
                            (selectedDistrict + DISTRICTS.size - 1) % DISTRICTS.size
                    }
                ) {
                    Text(text = "上一站", style = PicoTheme.typography.titleMedium)
                }
                Button(
                    onClick = { selectedDistrict = (selectedDistrict + 1) % DISTRICTS.size }
                ) {
                    Text(text = "下一站", style = PicoTheme.typography.titleMedium)
                }
                Spacer(Modifier.width(8.dp))
                Text(text = "昼夜：", style = PicoTheme.typography.titleMedium)
                DAY_PHASES.forEachIndexed { index, item ->
                    Button(
                        onClick = {
                            phaseIndex = index
                            autoCycle = false
                        }
                    ) {
                        Text(
                            text = if (index == phaseIndex) "● ${item.name}" else item.name,
                            style = PicoTheme.typography.titleMedium
                        )
                    }
                }
                Button(onClick = { autoCycle = !autoCycle }) {
                    Text(
                        text = if (autoCycle) "循环：开" else "循环：关",
                        style = PicoTheme.typography.titleMedium
                    )
                }
                Spacer(Modifier.weight(1f))
                // On-screen zoom/reset: the guaranteed path if spatial
                // gestures fail. These buttons only touch snapshot state, so
                // they work independently of all gesture code.
                Button(onClick = { scale = (scale - 0.2f).coerceAtLeast(MIN_SCALE) }) {
                    Text(text = "缩小", style = PicoTheme.typography.titleMedium)
                }
                Button(onClick = { scale = (scale + 0.2f).coerceAtMost(MAX_SCALE) }) {
                    Text(text = "放大", style = PicoTheme.typography.titleMedium)
                }
                Button(
                    onClick = {
                        yaw = 0f
                        pitch = -18f
                        scale = 1f
                    }
                ) {
                    Text(text = "复位视角", style = PicoTheme.typography.titleMedium)
                }
            }
        }

        if (showDistrictPanel) {
            DistrictInfoSubwindow(
                district = district,
                onClose = { showDistrictPanel = false }
            )
        }
        if (showTourPanel) {
            TourSubwindow(
                selectedDistrict = selectedDistrict,
                onSelectDistrict = { selectedDistrict = it },
                tourPlaying = tourPlaying,
                onToggleTour = { tourPlaying = !tourPlaying },
                phaseIndex = phaseIndex,
                onSelectPhase = { phaseIndex = it },
                autoCycle = autoCycle,
                onToggleAutoCycle = { autoCycle = !autoCycle },
                onClose = { showTourPanel = false }
            )
        }
    }
}

@Composable
private fun DistrictInfoSubwindow(district: District, onClose: () -> Unit) {
    Subwindow {
        Column(
            modifier =
                Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp))
                    .padding(horizontal = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Spacer(Modifier.height(24.dp))
            Text(text = district.name, style = PicoTheme.typography.headlineLarge)
            Text(text = district.englishName, style = PicoTheme.typography.bodyMedium)
            Text(text = district.flavor, style = PicoTheme.typography.bodyMedium)
            district.facts.forEach { fact ->
                Text(text = "· $fact", style = PicoTheme.typography.bodyMedium)
            }
            Spacer(Modifier.weight(1f))
            Button(
                modifier = Modifier.fillMaxWidth().align(Alignment.CenterHorizontally),
                onClick = onClose
            ) {
                Text(text = "关闭", style = PicoTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun TourSubwindow(
    selectedDistrict: Int,
    onSelectDistrict: (Int) -> Unit,
    tourPlaying: Boolean,
    onToggleTour: () -> Unit,
    phaseIndex: Int,
    onSelectPhase: (Int) -> Unit,
    autoCycle: Boolean,
    onToggleAutoCycle: () -> Unit,
    onClose: () -> Unit
) {
    Subwindow {
        Column(
            modifier =
                Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp))
                    .padding(horizontal = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Spacer(Modifier.height(24.dp))
            Text(text = "信使导览路线", style = PicoTheme.typography.headlineLarge)
            Text(
                text = "跟随信使无人机的巡城路线，依次停靠四个环区。",
                style = PicoTheme.typography.bodyMedium
            )
            DISTRICTS.forEach { item ->
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onSelectDistrict(item.index) }
                ) {
                    Text(
                        text =
                            "${item.index + 1}. ${item.name} · ${item.englishName}" +
                                if (item.index == selectedDistrict) "  ● 当前" else "",
                        style = PicoTheme.typography.titleMedium
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onToggleTour) {
                    Text(
                        text = if (tourPlaying) "暂停导览" else "开始导览",
                        style = PicoTheme.typography.titleMedium
                    )
                }
                Button(onClick = onToggleAutoCycle) {
                    Text(
                        text = if (autoCycle) "昼夜循环：开" else "昼夜循环：关",
                        style = PicoTheme.typography.titleMedium
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(text = "昼夜：", style = PicoTheme.typography.titleMedium)
                DAY_PHASES.forEachIndexed { index, item ->
                    Button(onClick = { onSelectPhase(index) }) {
                        Text(
                            text = if (index == phaseIndex) "● ${item.name}" else item.name,
                            style = PicoTheme.typography.titleMedium
                        )
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            Button(
                modifier = Modifier.fillMaxWidth().align(Alignment.CenterHorizontally),
                onClick = onClose
            ) {
                Text(text = "关闭", style = PicoTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
