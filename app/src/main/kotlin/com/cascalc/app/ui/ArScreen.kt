package com.cascalc.app.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageAnalysis
import androidx.camera.mlkit.vision.MlKitAnalyzer
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.core.content.ContextCompat
import java.util.concurrent.Executors
import com.cascalc.app.ArUiState
import com.cascalc.app.ar.HandAndTextAnalyzer
import com.cascalc.app.ar.HandDetector
import com.cascalc.app.ar.HandModel
import com.cascalc.app.ar.ThrottledAnalyzer
import com.cascalc.engine.ar.Box as EquationBox
import com.cascalc.engine.ar.DetectedText
import com.cascalc.engine.ar.HandLandmarks
import com.cascalc.engine.ar.Point2
import com.cascalc.engine.ar.PointerState
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

/**
 * V8: the AR overlay. Point the camera at written maths and the answer appears
 * beside it, following the writing as the camera moves.
 *
 * **Screen-space, not world-anchored, and deliberately so.** ARCore anchors
 * would need ARCore to own the camera and render the feed through OpenGL, which
 * rules out a CameraX preview and ML Kit analysis on the same stream. Stability
 * instead comes from `EquationTracker`: overlays are matched by position,
 * eased toward each new reading, and held through dropped frames. That is the
 * risk the roadmap names, and it is unit-tested rather than eyeballed.
 */
@Composable
fun ArScreen(
    state: ArUiState,
    onFrame: (List<DetectedText>) -> Unit,
    onHand: (HandLandmarks?) -> Unit,
    onHandStatus: (String?) -> Unit,
    onTapEquation: (Long) -> Unit,
    onToggleScanning: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> hasPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    if (!hasPermission) {
        CameraPermissionPrompt(
            onRequest = { permissionLauncher.launch(Manifest.permission.CAMERA) },
            modifier = modifier,
        )
        return
    }

    Box(modifier = modifier.fillMaxSize()) {
        CameraPreview(
            onFrame = onFrame,
            onHand = onHand,
            onHandStatus = onHandStatus,
            modifier = Modifier.fillMaxSize(),
        )
        EquationOverlay(state = state, onTapEquation = onTapEquation)
        PointerOverlay(state.pointer)

        state.handTrackingStatus?.let { status ->
            // Sits above the controls rather than at the top, where the
            // expanded explanation card would cover it.
            Text(
                text = status,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 12.dp, end = 12.dp, bottom = 76.dp)
                    .background(
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                        RoundedCornerShape(6.dp),
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(onClick = onToggleScanning) {
                Text(if (state.scanning) "Freeze" else "Resume")
            }
            TextButton(onClick = onReset) { Text("Clear") }
        }

        AnimatedVisibility(
            visible = state.expandedId != null &&
                (state.steps.isNotEmpty() || state.explanation != null),
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(200)),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                ),
            ) {
                Column(
                    modifier = Modifier
                        .padding(12.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    state.explanation?.let { explanation ->
                        Text(explanation.headline, style = MaterialTheme.typography.titleMedium)
                        explanation.facts.forEach { fact ->
                            Text(
                                "• $fact",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    state.steps.forEachIndexed { index, step ->
                        Text(
                            "${index + 1}. ${step.explanation}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        step.expression?.let {
                            Text(it, style = MaterialTheme.typography.titleSmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CameraPermissionPrompt(onRequest: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Camera access needed", style = MaterialTheme.typography.titleMedium)
        Text(
            "AR mode reads equations through the camera. Nothing leaves the device — " +
                "recognition and solving both run locally.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 12.dp),
        )
        Button(onClick = onRequest) { Text("Allow camera") }
    }
}

/**
 * The pointing cursor and its dwell ring.
 *
 * The ring filling is the whole contract with the user: nothing opens without
 * it, so a selection is never a surprise, and holding still is visibly what
 * causes it.
 */
@Composable
private fun PointerOverlay(pointer: PointerState) {
    val pointing = pointer as? PointerState.Pointing ?: return
    val color = MaterialTheme.colorScheme.tertiary
    val onTarget = pointing.targetId != null

    Canvas(modifier = Modifier.fillMaxSize()) {
        val center = Offset(pointing.position.x, pointing.position.y)
        drawCircle(color, radius = if (onTarget) 14f else 9f, center = center, alpha = 0.9f)
        if (onTarget && pointing.dwellProgress > 0f) {
            drawArc(
                color = color,
                startAngle = -90f,
                sweepAngle = 360f * pointing.dwellProgress,
                useCenter = false,
                topLeft = Offset(center.x - DWELL_RADIUS, center.y - DWELL_RADIUS),
                size = androidx.compose.ui.geometry.Size(DWELL_RADIUS * 2, DWELL_RADIUS * 2),
                style = Stroke(width = 5f),
            )
        }
    }
}

private const val DWELL_RADIUS = 28f

@Composable
private fun CameraPreview(
    onFrame: (List<DetectedText>) -> Unit,
    onHand: (HandLandmarks?) -> Unit,
    onHandStatus: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var detector by remember { mutableStateOf<HandDetector?>(null) }

    // Fetch the hand model in the background. AR mode is usable before it
    // arrives - and if it never arrives - so nothing here blocks the camera.
    LaunchedEffect(Unit) {
        onHandStatus("Preparing hand tracking…")
        val outcome = withContext(Dispatchers.IO) {
            when (val state = HandModel(context).load()) {
                is HandModel.State.Ready -> HandDetector.create(context, state.buffer)
                is HandModel.State.Failed -> HandDetector.Outcome.Failed(state.reason)
                else -> HandDetector.Outcome.Failed("Hand model unavailable")
            }
        }
        when (outcome) {
            is HandDetector.Outcome.Ready -> {
                detector = outcome.detector
                onHandStatus(null)
            }
            is HandDetector.Outcome.Failed -> {
                detector = null
                // Report the real reason, not a shrug. Without it the only
                // information is "it didn't work", which fixes nothing.
                onHandStatus("Hand tracking off — ${outcome.reason}")
            }
        }
    }

    DisposableEffect(detector) {
        onDispose { detector?.close() }
    }

    val recognizer = remember {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }
    val controller = remember { LifecycleCameraController(context) }

    DisposableEffect(Unit) {
        // Analysis must NOT run on the main thread. Text recognition and hand
        // detection each take tens of milliseconds per frame; on the UI thread
        // that is a frozen screen and, before long, an ANR. CameraX drops
        // frames while this executor is busy, which is the desired behaviour.
        val executor = Executors.newSingleThreadExecutor()
        val analyzer = MlKitAnalyzer(
            listOf(recognizer),
            // View-referenced coordinates come back already mapped to the
            // preview, so overlays line up without hand-rolled matrix maths.
            ImageAnalysis.COORDINATE_SYSTEM_VIEW_REFERENCED,
            executor,
        ) { result ->
            val text = result.getValue(recognizer)
            val detections = text?.textBlocks
                ?.flatMap { block -> block.lines }
                ?.mapNotNull { line ->
                    val rect = line.boundingBox ?: return@mapNotNull null
                    DetectedText(
                        text = line.text,
                        box = EquationBox(
                            rect.left.toFloat(),
                            rect.top.toFloat(),
                            rect.right.toFloat(),
                            rect.bottom.toFloat(),
                        ),
                    )
                }
                .orEmpty()
            onFrame(detections)
        }

        val composite = HandAndTextAnalyzer(
            delegate = ThrottledAnalyzer(analyzer),
            detectHand = { proxy ->
                val handDetector = detector ?: return@HandAndTextAnalyzer null
                val bitmap = runCatching { proxy.toBitmap() }.getOrNull()
                    ?: return@HandAndTextAnalyzer null
                handDetector.detect(bitmap, proxy.imageInfo.rotationDegrees)
            },
            onHand = { hand, transform ->
                onHand(hand?.let { mapToView(it, transform) })
            },
        )
        controller.setImageAnalysisAnalyzer(executor, composite)
        controller.bindToLifecycle(lifecycleOwner)

        onDispose {
            controller.clearImageAnalysisAnalyzer()
            controller.unbind()
            recognizer.close()
            executor.shutdown()
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { viewContext ->
            PreviewView(viewContext).apply {
                this.controller = controller
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }
        },
    )
}

/**
 * Moves landmarks from sensor pixels into view coordinates.
 *
 * Equation boxes already arrive view-referenced from CameraX, so the cursor has
 * to be put in the same space before the two can be compared — otherwise
 * pointing lands nowhere near what it looks like it is pointing at.
 */
private fun mapToView(hand: HandLandmarks, transform: android.graphics.Matrix?): HandLandmarks {
    val matrix = transform ?: return hand
    val coordinates = FloatArray(hand.points.size * 2)
    hand.points.forEachIndexed { index, point ->
        coordinates[index * 2] = point.x
        coordinates[index * 2 + 1] = point.y
    }
    matrix.mapPoints(coordinates)
    return HandLandmarks(
        hand.points.indices.map { Point2(coordinates[it * 2], coordinates[it * 2 + 1]) },
    )
}

/** Draws an answer beside each tracked equation, easing as the camera moves. */
@Composable
private fun EquationOverlay(state: ArUiState, onTapEquation: (Long) -> Unit) {
    val density = LocalDensity.current

    state.equations.forEach { equation ->
        val solution = equation.solution ?: return@forEach

        // The tracker already smooths position; animating on top of it keeps
        // the label from stepping between recognition intervals.
        val x by animateFloatAsState(equation.box.right, tween(180), label = "x")
        val y by animateFloatAsState(equation.box.top, tween(180), label = "y")

        Box(
            modifier = Modifier
                .padding(
                    start = with(density) { x.toDp() } + 8.dp,
                    top = with(density) { y.toDp() },
                ),
        ) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.92f),
                ),
                shape = RoundedCornerShape(10.dp),
            ) {
                Column(
                    modifier = Modifier
                        .background(Color.Transparent)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = "= $solution",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    TextButton(
                        onClick = { onTapEquation(equation.id) },
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                    ) {
                        Text(
                            if (state.expandedId == equation.id) "Hide steps" else "Steps",
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
        }
    }
}
