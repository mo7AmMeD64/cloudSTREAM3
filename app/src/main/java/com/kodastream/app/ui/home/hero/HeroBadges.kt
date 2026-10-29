/*
 * Ported from Nuvio (https://github.com/NuvioMedia/NuvioMobile), GPL-3.0. Adapted for KodaStream.
 * Original: composeApp/src/commonMain/kotlin/com/nuvio/app/features/details/components/DetailMetaInfo.kt
 * (DetailHeroMetaBadge, MetaBadgeText and ImdbYellow only)
 */
package com.kodastream.app.ui.home.hero

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
internal fun DetailHeroMetaBadge(
    text: String,
    contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    secondaryText: String? = null,
) {
    val borderColor = contentColor.copy(alpha = 0.55f)
    Row(
        modifier = Modifier
            .border(
                border = BorderStroke(1.dp, borderColor),
                shape = RoundedCornerShape(6.dp),
            )
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        MetaBadgeText(text = text, color = contentColor)
        secondaryText?.let { secondary ->
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(12.dp)
                    .background(borderColor),
            )
            MetaBadgeText(text = secondary, color = contentColor)
        }
    }
}

@Composable
private fun MetaBadgeText(
    text: String,
    color: Color,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

internal val ImdbYellow = Color(0xFFF5C518)
