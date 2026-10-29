/*
 * Ported from Nuvio (https://github.com/NuvioMedia/NuvioMobile), GPL-3.0. Adapted for KodaStream.
 * New for KodaStream: selects which Home hero is shown.
 */
package com.kodastream.app.ui.home.hero

import androidx.annotation.StringRes
import com.kodastream.app.R

enum class HeroStyle(
    val storageId: String,
    @StringRes val labelRes: Int,
    @StringRes val descriptionRes: Int,
) {
    CAROUSEL("carousel", R.string.hero_style_carousel, R.string.hero_style_carousel_desc),
    CINEMATIC("cinematic", R.string.hero_style_cinematic, R.string.hero_style_cinematic_desc),
    OFF("off", R.string.hero_style_off, R.string.hero_style_off_desc);

    companion object {
        /** Unknown or missing values keep the carousel the app shipped with. */
        fun parse(value: String?): HeroStyle =
            entries.firstOrNull { it.storageId == value } ?: CAROUSEL
    }
}
