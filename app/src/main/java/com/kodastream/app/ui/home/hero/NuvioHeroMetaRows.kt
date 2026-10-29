/*
 * Ported from Nuvio (https://github.com/NuvioMedia/NuvioMobile), GPL-3.0. Adapted for KodaStream.
 * Original: composeApp/src/commonMain/kotlin/com/nuvio/app/features/home/components/HomeHeroMetaRows.kt
 */
package com.kodastream.app.ui.home.hero

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.kodastream.app.R
import com.kodastream.app.ui.home.typeLabel

@Composable
internal fun HomeHeroMetaRows(
    item: HeroItem,
    isTablet: Boolean,
) {
    val type = item.type.trim().lowercase()
    val isSeries = type == "series" || type == "tv"
    val typeLabel = typeLabel(item.type)
    val genre = item.genres.firstOrNull()?.takeIf { it.isNotBlank() }
    val runtime = item.runtime
        ?.toIntOrNull()
        ?.takeIf { it > 0 }
        ?.let { stringResource(R.string.minutes_short, it) }
        ?: item.runtime?.takeIf { it.isNotBlank() }
    val release = item.releaseInfo?.takeIf { it.isNotBlank() }
    val status = item.status?.trim()?.takeIf { it.isNotBlank() }
        ?.let { localizedMediaStatusLabel(it, isSeries).uppercase() }
    val badges = listOfNotNull(item.ageRating?.trim()?.takeIf { it.isNotBlank() }, status)
    val language = item.language?.trim()?.takeIf { it.isNotBlank() }?.uppercase()
    val imdbRating = item.imdbRating?.trim()
        ?.takeIf { raw -> raw.toDoubleOrNull()?.let { it > 0.0 } == true }
    val primaryImdbRating = imdbRating?.takeIf { !isSeries && badges.isEmpty() }
    val secondaryImdbRating = imdbRating?.takeIf { primaryImdbRating == null }
    val horizontalAlignment = if (isTablet) Alignment.Start else Alignment.CenterHorizontally
    val rowArrangement = Arrangement.spacedBy(8.dp, horizontalAlignment)

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = horizontalAlignment,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = rowArrangement,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HeroMetaText(text = typeLabel)
            genre?.let {
                HeroMetaDot()
                HeroMetaText(text = it, modifier = Modifier.weight(1f, fill = false))
            }
            runtime?.let {
                HeroMetaDot()
                HeroMetaText(text = it)
            }
            release?.let {
                HeroMetaDot()
                HeroMetaText(text = it)
            }
            primaryImdbRating?.let {
                HeroMetaDot()
                HeroImdbRating(rating = it)
            }
        }

        if (badges.isNotEmpty() || secondaryImdbRating != null || language != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = rowArrangement,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (badges.isNotEmpty()) {
                    DetailHeroMetaBadge(
                        text = badges.first(),
                        secondaryText = badges.getOrNull(1),
                        contentColor = MaterialTheme.colorScheme.onBackground,
                    )
                }
                secondaryImdbRating?.let {
                    if (badges.isNotEmpty()) HeroMetaDot()
                    HeroImdbRating(rating = it)
                }
                language?.let {
                    if (badges.isNotEmpty() || secondaryImdbRating != null) HeroMetaDot()
                    HeroMetaText(text = it)
                }
            }
        }
    }
}

@Composable
private fun HeroImdbRating(rating: String) {
    val textStyle = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = Icons.Rounded.Star,
            contentDescription = null,
            tint = ImdbYellow,
            modifier = Modifier.size(16.dp),
        )
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            text = rating,
            style = textStyle,
            color = ImdbYellow,
            maxLines = 1,
        )
    }
}

@Composable
private fun HeroMetaText(
    text: String,
    modifier: Modifier = Modifier,
) {
    val textDirection = if (LocalLayoutDirection.current == LayoutDirection.Rtl) {
        TextDirection.Rtl
    } else {
        TextDirection.Ltr
    }
    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.labelLarge.copy(textDirection = textDirection),
        color = MaterialTheme.colorScheme.onBackground,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun HeroMetaDot() {
    Box(
        modifier = Modifier
            .size(4.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)),
    )
}
