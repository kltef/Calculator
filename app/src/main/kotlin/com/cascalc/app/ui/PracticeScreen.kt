package com.cascalc.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cascalc.app.PracticeUiState
import com.cascalc.engine.PracticeProblems

/** V7's practice mode: generated problems, marked by value not by spelling. */
@Composable
fun PracticeScreen(
    state: PracticeUiState,
    onTopicSelected: (PracticeProblems.Topic) -> Unit,
    onAnswerChanged: (String) -> Unit,
    onSubmit: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(PracticeProblems.Topic.entries.size) { index ->
                val topic = PracticeProblems.Topic.entries[index]
                FilterChip(
                    selected = state.topic == topic,
                    onClick = { onTopicSelected(topic) },
                    label = { Text(topic.label) },
                )
            }
        }

        Text(
            text = "Score: ${state.correct} / ${state.attempted}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        state.problem?.let { problem ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(problem.prompt, style = MaterialTheme.typography.bodySmall)
                    Text(problem.question, style = MaterialTheme.typography.headlineSmall)
                }
            }

            OutlinedTextField(
                value = state.answer,
                onValueChange = onAnswerChanged,
                label = { Text("Your answer") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            state.mark?.let { mark ->
                val (text, color) = when (mark) {
                    PracticeProblems.Mark.Correct ->
                        "Correct" to MaterialTheme.colorScheme.primary
                    is PracticeProblems.Mark.Incorrect ->
                        "Not quite — the answer is ${mark.expected}" to MaterialTheme.colorScheme.error
                    PracticeProblems.Mark.Unreadable ->
                        "Couldn't read that answer" to MaterialTheme.colorScheme.error
                }
                Text(text, color = color, style = MaterialTheme.typography.bodyLarge)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onSubmit, enabled = state.mark == null) { Text("Check") }
                TextButton(onClick = onNext) { Text("Next problem") }
            }
        }
    }
}
