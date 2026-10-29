package com.kodastream.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.PhotoSizeSelectLarge
import androidx.compose.material.icons.rounded.PhotoSizeSelectSmall
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.kodastream.app.R
import com.kodastream.app.data.POSTER_WIDTH_STEPS
import kotlin.math.roundToInt

/**
 * The header's poster-size control: a grid icon that opens a stepped slider.
 * Five positions map to [POSTER_WIDTH_STEPS]; the two end icons step one size
 * and disable at their end, so the control also works without dragging.
 * The row reverses in RTL, keeping "larger" on the reading end.
 */
@Composable
fun PosterSizeButton(
    step: Int,
    onStepChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val stepCount = POSTER_WIDTH_STEPS.size
    val title = stringResource(R.string.poster_size)
    val stateText = stringResource(R.string.poster_size_state, step + 1, stepCount)

    Box(modifier) {
        IconButton(onClick = { expanded = true }) {
            Icon(
                imageVector = Icons.Rounded.GridView,
                contentDescription = title,
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            Column(
                modifier = Modifier
                    .width(280.dp)
                    .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { onStepChange(step - 1) },
                        enabled = step > 0,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PhotoSizeSelectSmall,
                            contentDescription = stringResource(R.string.poster_size_smaller),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Slider(
                        value = step.toFloat(),
                        onValueChange = { onStepChange(it.roundToInt()) },
                        valueRange = 0f..(stepCount - 1).toFloat(),
                        steps = stepCount - 2,
                        modifier = Modifier
                            .weight(1f)
                            .semantics {
                                contentDescription = title
                                stateDescription = stateText
                            },
                    )
                    IconButton(
                        onClick = { onStepChange(step + 1) },
                        enabled = step < stepCount - 1,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PhotoSizeSelectLarge,
                            contentDescription = stringResource(R.string.poster_size_larger),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }
    }
}
