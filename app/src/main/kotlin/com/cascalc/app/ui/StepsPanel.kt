package com.cascalc.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cascalc.engine.SolutionStep

/**
 * A worked solution, one numbered step at a time.
 *
 * Shown only when the engine could actually derive the steps — see [com.cascalc.engine.StepSolver].
 */
@Composable
fun StepsPanel(steps: List<SolutionStep>, modifier: Modifier = Modifier) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
        ) {
            steps.forEachIndexed { index, step ->
                Text(
                    text = "${index + 1}. ${step.explanation}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                step.expression?.let { expression ->
                    Text(
                        text = expression,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(start = 16.dp, top = 2.dp, bottom = 8.dp),
                    )
                }
                if (step.expression == null) {
                    Text(text = "", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
