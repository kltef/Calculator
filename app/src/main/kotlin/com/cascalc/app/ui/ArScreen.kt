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
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.clickable
import androidx.compose.material3.LocalContentColor
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

        // Everything the camera has found, drawn in one pass: the frames round
        // each equation, and the pointing cursor on top of them.
        DetectionCanvas(state = state)
        AnswerChips(state = state, onTapEquation = onTapEquation)

        state.handTrackingStatus?.let { status ->
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 12.dp, end = 12.dp, bottom = 76.dp),
            ) {
                Text(
                    text = status,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
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
            visible = state.expandedId != null && state.explanation != null,
            enter = slideInVertically(tween(240)) { it } + fadeIn(tween(240)),
            exit = slideOutVertically(tween(180)) { it } + fadeOut(tween(140)),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            state.explanation?.let { explanation ->
                ExplanationSheet(
                    equation = state.equations.firstOrNull { it.id == state.expandedId }?.text,
                    explanation = explanation,
                    onClose = { state.expandedId?.let(onTapEquation) },
                )
            }
        }
    }
}

/**
 * A reading-order sheet: the equation, what kind of thing it is, how it is
 * said aloud, then each section.
 *
 * Anchored to the bottom rather than the top so it never covers the writing it
 * is describing, and opaque rather than tinted, because small text over a
 * moving camera feed is unreadable.
 */
@Composable
private fun ExplanationSheet(
    equation: String?,
    explanation: com.cascalc.engine.Explainer.Explanation,
    onClose: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
        shadowElevation = 12.dp,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .heightIn(max = 340.dp)
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 8.dp, top = 16.dp, bottom = 20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    equation?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontFamily = FontFamily.Monospace,
                            ),
                        )
                    }
                    Text(
                        text = explanation.headline,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                IconButton(onClick = onClose) {
                    Icon(Icons.Filled.Close, contentDescription = "Close explanation")
                }
            }

            explanation.reading?.let { reading ->
                Text(
                    text = "\u201C$reading\u201D",
                    style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, end = 12.dp),
                )
            }

            explanation.sections.forEach { section ->
                Spacer(Modifier.height(14.dp))
                HorizontalDivider()
                Spacer(Modifier.height(10.dp))
                Text(
                    text = section.title.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(6.dp))
                section.lines.forEach { line ->
                    Row(modifier = Modifier.padding(bottom = 6.dp, end = 12.dp)) {
                        Text(
                            text = "\u2022",
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.width(16.dp),
                        )
                        Text(text = line, style = MaterialTheme.typography.bodyMedium)
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
 * One drawing pass over the camera: a frame round every equation the app has
 * recognised, and the pointing cursor above them.
 *
 * Drawing the frames matters as much as the answers — without them there is no
 * way to tell whether the app has seen your writing at all, or is simply
 * showing nothing because it found nothing.
 */
@Composable
private fun DetectionCanvas(state: ArUiState) {
    val found = MaterialTheme.colorScheme.primary
    val aimed = MaterialTheme.colorScheme.tertiary
    val pointing = state.pointer as? PointerState.Pointing

    Canvas(modifier = Modifier.fillMaxSize()) {
        state.equations.forEach { equation ->
            val targeted = pointing?.targetId == equation.id
            val color = if (targeted) aimed else found
            val box = equation.box
            drawRoundRect(
                color = color.copy(alpha = if (targeted) 0.95f else 0.55f),
                topLeft = Offset(box.left - 6f, box.top - 6f),
                size = Size(box.width + 12f, box.height + 12f),
                cornerRadius = CornerRadius(10f, 10f),
                style = Stroke(width = if (targeted) 4f else 2f),
            )
        }

        pointing ?: return@Canvas
        val center = Offset(pointing.position.x, pointing.position.y)
        val onTarget = pointing.targetId != null

        // Soft halo, so the cursor stays visible against light and dark pages.
        drawCircle(aimed.copy(alpha = 0.25f), radius = if (onTarget) 26f else 18f, center = center)
        drawCircle(aimed, radius = if (onTarget) 10f else 7f, center = center)

        if (onTarget && pointing.dwellProgress > 0f) {
            drawArc(
                color = aimed,
                startAngle = -90f,
                sweepAngle = 360f * pointing.dwellProgress,
                useCenter = false,
                topLeft = Offset(center.x - DWELL_RADIUS, center.y - DWELL_RADIUS),
                size = Size(DWELL_RADIUS * 2, DWELL_RADIUS * 2),
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

/**
 * The answer beside each equation, as a pill anchored to its right edge.
 *
 * The tracker already smooths position; animating on top of that keeps the pill
 * from stepping between recognition intervals, which is the difference between
 * a label that sits on the page and one that twitches beside it.
 */
@Composable
private fun AnswerChips(state: ArUiState, onTapEquation: (Long) -> Unit) {
    val density = LocalDensity.current
    val pointing = state.pointer as? PointerState.Pointing

    state.equations.forEach { equation ->
        val solution = equation.solution ?: return@forEach
        val targeted = pointing?.targetId == equation.id

        val x by animateFloatAsState(equation.box.right, tween(180), label = "chipX")
        val y by animateFloatAsState(equation.box.top, tween(180), label = "chipY")

        Surface(
            color = if (targeted) {
                MaterialTheme.colorScheme.tertiaryContainer
            } else {
                MaterialTheme.colorScheme.primaryContainer
            },
            contentColor = if (targeted) {
                MaterialTheme.colorScheme.onTertiaryContainer
            } else {
                MaterialTheme.colorScheme.onPrimaryContainer
            },
            shape = RoundedCornerShape(14.dp),
            shadowElevation = 6.dp,
            modifier = Modifier.padding(
                start = with(density) { x.toDp() } + 10.dp,
                top = with(density) { y.toDp() },
            ),
        ) {
            Column(
                modifier = Modifier
                    .clickable { onTapEquation(equation.id) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Text(
                    text = "= $solution",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = if (state.expandedId == equation.id) "tap to close" else "point or tap",
                    style = MaterialTheme.typography.labelSmall,
                    color = LocalContentColor.current.copy(alpha = 0.7f),
                )
            }
        }
    }
}
