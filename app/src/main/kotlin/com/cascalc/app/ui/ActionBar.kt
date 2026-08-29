package com.cascalc.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.cascalc.app.R
import com.cascalc.engine.Action

/**
 * The symbolic operations, alongside the keypad's `=`.
 *
 * These are separate buttons rather than something inferred from the input,
 * because the same text means different things depending on intent: `x^2 - 4`
 * can be expanded, factored, or solved, and guessing which would be wrong two
 * times out of three.
 */
@Composable
fun ActionBar(
    onAction: (Action) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val actions = listOf(
        Action.SIMPLIFY to R.string.action_simplify,
        Action.EXPAND to R.string.action_expand,
        Action.FACTOR to R.string.action_factor,
        Action.SOLVE to R.string.action_solve,
    )

    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(actions) { (action, labelRes) ->
            AssistChip(
                onClick = { onAction(action) },
                enabled = enabled,
                label = { Text(stringResource(labelRes)) },
            )
        }
    }
}
