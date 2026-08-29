package com.cascalc.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cascalc.app.ui.CalculatorScreen
import com.cascalc.app.ui.CasCalculatorTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            CasCalculatorTheme {
                val viewModel: CalculatorViewModel = viewModel()
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                val error by viewModel.error.collectAsStateWithLifecycle()

                Surface(modifier = Modifier.windowInsetsPadding(WindowInsets.systemBars)) {
                    CalculatorScreen(
                        state = state,
                        error = error,
                        onExpressionChanged = viewModel::onExpressionChanged,
                        onSelectionChanged = viewModel::onSelectionChanged,
                        onKey = viewModel::insert,
                        onBackspace = viewModel::backspace,
                        onClear = viewModel::clear,
                        onEvaluate = viewModel::evaluate,
                        onToggleAngleMode = viewModel::toggleAngleMode,
                        onToggleHistory = viewModel::toggleHistory,
                        onRecallInput = viewModel::recallInput,
                        onRecallResult = viewModel::recallResult,
                        onDeleteHistoryEntry = viewModel::deleteHistoryEntry,
                        onClearHistory = viewModel::clearHistory,
                    )
                }
            }
        }
    }
}
