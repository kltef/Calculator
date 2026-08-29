package com.cascalc.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cascalc.app.ui.CalculatorScreen
import com.cascalc.app.ui.CasCalculatorTheme
import com.cascalc.app.ui.CrashBanner

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            CasCalculatorTheme {
                val viewModel: CalculatorViewModel = viewModel()
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                val error by viewModel.error.collectAsStateWithLifecycle()

                val reporter = (application as CasCalculatorApp).crashReporter
                var crashReport by remember { mutableStateOf(reporter.lastCrash()) }

                Surface(modifier = Modifier.windowInsetsPadding(WindowInsets.systemBars)) {
                    Column {
                        crashReport?.let { report ->
                            CrashBanner(
                                report = report,
                                onDismiss = {
                                    reporter.clear()
                                    crashReport = null
                                },
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
                        )
                    }
                }
            }
        }
    }
}
