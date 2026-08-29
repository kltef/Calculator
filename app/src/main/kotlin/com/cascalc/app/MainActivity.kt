package com.cascalc.app

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cascalc.app.ui.BottomBar
import com.cascalc.app.ui.CalculatorScreen
import com.cascalc.app.ui.CasCalculatorTheme
import com.cascalc.app.ui.CrashBanner
import com.cascalc.app.ui.GraphScreen
import com.cascalc.app.ui.MatrixScreen
import com.cascalc.app.ui.PracticeScreen
import com.cascalc.app.ui.ReferenceScreen
import com.cascalc.app.ui.Screen
import com.cascalc.app.ui.SettingsScreen
import com.cascalc.app.ui.ToolsScreen

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val app = application as CasCalculatorApp

        setContent {
            var appearance by remember { mutableStateOf(app.settings.load()) }

            CasCalculatorTheme(appearance = appearance) {
                var screen by remember { mutableStateOf(Screen.CALCULATOR) }

                Scaffold(
                    modifier = Modifier.windowInsetsPadding(WindowInsets.systemBars),
                    bottomBar = { BottomBar(current = screen, onSelect = { screen = it }) },
                ) { padding ->
                    Column(modifier = Modifier.padding(padding)) {
                        CrashBannerIfAny(app)
                        when (screen) {
                            Screen.CALCULATOR -> CalculatorRoute(
                                onGoToGraph = { screen = Screen.GRAPH },
                            )
                            Screen.GRAPH -> GraphRoute()
                            Screen.MATRIX -> MatrixRoute()
                            Screen.TOOLS -> ToolsRoute()
                            Screen.PRACTICE -> PracticeRoute()
                            Screen.REFERENCE -> ReferenceScreen()
                            Screen.SETTINGS -> SettingsScreen(
                                appearance = appearance,
                                onChange = {
                                    appearance = it
                                    app.settings.save(it)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CrashBannerIfAny(app: CasCalculatorApp) {
    var report by remember { mutableStateOf(app.crashReporter.lastCrash()) }
    report?.let {
        CrashBanner(
            report = it,
            onDismiss = {
                app.crashReporter.clear()
                report = null
            },
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun CalculatorRoute(onGoToGraph: () -> Unit) {
    val viewModel: CalculatorViewModel = viewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val engineDiagnostic by viewModel.engineDiagnostic.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val voiceLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            VoiceInput.transcriptOf(result.data)?.let(viewModel::onSpokenInput)
        }
    }

    engineDiagnostic?.let {
        CrashBanner(
            report = it,
            onDismiss = {},
            title = "The math engine couldn't start",
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }

    CalculatorScreen(
        state = state,
        error = error,
        onExpressionChanged = viewModel::onExpressionChanged,
        onSelectionChanged = viewModel::onSelectionChanged,
        onKey = viewModel::insert,
        onBackspace = viewModel::backspace,
        onClear = viewModel::clear,
        onEvaluate = viewModel::evaluate,
        onAction = viewModel::run,
        onToggleAngleMode = viewModel::toggleAngleMode,
        onToggleHistory = viewModel::toggleHistory,
        onToggleVariables = viewModel::toggleVariables,
        onToggleSteps = viewModel::toggleSteps,
        onRecallInput = viewModel::recallInput,
        onRecallResult = viewModel::recallResult,
        onDeleteHistoryEntry = viewModel::deleteHistoryEntry,
        onClearHistory = viewModel::clearHistory,
        onInsertVariable = viewModel::insert,
        onDeleteVariable = viewModel::deleteVariable,
        onVoiceInput = { voiceLauncher.launch(VoiceInput.intent()) },
        onShare = {
            Sharing.shareText(
                context,
                Sharing.formatSolution(
                    state.expression,
                    state.committed?.exact.orEmpty(),
                    state.steps,
                ),
            )
        },
        onExportPdf = {
            Sharing.sharePdf(
                context,
                title = "Solution",
                body = Sharing.formatSolution(
                    state.expression,
                    state.committed?.exact.orEmpty(),
                    state.steps,
                ),
            )
        },
        onGoToGraph = onGoToGraph,
    )
}

@Composable
private fun GraphRoute() {
    val viewModel: GraphViewModel = viewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    GraphScreen(
        state = state,
        onExpressionChanged = viewModel::setExpression,
        onAddFunction = viewModel::addFunction,
        onRemoveFunction = viewModel::removeFunction,
        onToggleVisible = viewModel::toggleVisible,
        onTransform = viewModel::transform,
        onTrace = viewModel::traceAt,
        onToggleTrace = viewModel::toggleTrace,
        onFindRoots = viewModel::findRoots,
        onFindIntersections = viewModel::findIntersections,
        onResetWindow = viewModel::resetWindow,
    )
}

@Composable
private fun MatrixRoute() {
    val viewModel: MatrixViewModel = viewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    MatrixScreen(
        state = state,
        onCellChanged = viewModel::setCell,
        onResize = viewModel::resize,
        onAction = viewModel::run,
    )
}

@Composable
private fun ToolsRoute() {
    val viewModel: CalculatorViewModel = viewModel()
    ToolsScreen(onInsert = viewModel::insert)
}

@Composable
private fun PracticeRoute() {
    val viewModel: PracticeViewModel = viewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    PracticeScreen(
        state = state,
        onTopicSelected = viewModel::selectTopic,
        onAnswerChanged = viewModel::onAnswerChanged,
        onSubmit = viewModel::submit,
        onNext = viewModel::next,
    )
}
