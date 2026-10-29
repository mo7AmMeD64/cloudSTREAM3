/*
 * Ported from Nuvio (https://github.com/NuvioMedia/NuvioMobile), GPL-3.0. Adapted for KodaStream.
 * Original: composeApp/src/commonMain/kotlin/com/nuvio/app/core/i18n/MediaStatusLabel.kt
 */
package com.kodastream.app.ui.home.hero

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.kodastream.app.R

@Composable
internal fun localizedMediaStatusLabel(status: String, isSeries: Boolean): String {
    val resource = when (status.trim().lowercase()) {
        "ended" -> if (isSeries) R.string.series_status_ended else R.string.movie_status_ended
        "continuing", "returning series" -> if (isSeries) R.string.series_status_continuing else R.string.movie_status_continuing
        "current" -> if (isSeries) R.string.series_status_current else R.string.movie_status_current
        "cancelled", "canceled" -> if (isSeries) R.string.series_status_cancelled else R.string.movie_status_cancelled
        "released" -> if (isSeries) R.string.series_status_released else R.string.movie_status_released
        "planned" -> if (isSeries) R.string.series_status_planned else R.string.movie_status_planned
        "rumored" -> if (isSeries) R.string.series_status_rumored else R.string.movie_status_rumored
        "in production" -> if (isSeries) R.string.series_status_in_production else R.string.movie_status_in_production
        "post production" -> if (isSeries) R.string.series_status_post_production else R.string.movie_status_post_production
        else -> return status.trim()
    }
    return stringResource(resource)
}
