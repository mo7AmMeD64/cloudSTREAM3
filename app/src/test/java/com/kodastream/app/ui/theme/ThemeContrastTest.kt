package com.kodastream.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WCAG guard for every seed palette, mode and style. It follows the shipping
 * pipeline: tinted pages, and in dark also the AMOLED override, which replaces
 * the tinted pages in [KodaStreamTheme].
 */
class ThemeContrastTest {

    private data class Check(val name: String, val foreground: Color, val background: Color, val minimum: Float)

    @Test
    fun everyPaletteMeetsContrast() {
        val failures = mutableListOf<String>()
        for (dark in listOf(false, true)) {
            for (style in PaletteStyle.entries) {
                for (palette in APP_PALETTES) {
                    val base = buildPaletteColorScheme(palette, dark, style)
                    check(
                        "${palette.id}/${style.storageId}/${if (dark) "dark" else "light"}",
                        base.withTintedPages(dark),
                        failures,
                    )
                    if (dark) {
                        check("${palette.id}/${style.storageId}/amoled", base.toAmoled(), failures)
                    }
                }
            }
        }
        assertTrue(failures.joinToString("\n", prefix = "Contrast failures:\n"), failures.isEmpty())
    }

    private fun check(label: String, scheme: ColorScheme, failures: MutableList<String>) {
        val checks = listOf(
            Check("onSurface/surface", scheme.onSurface, scheme.surface, 4.5f),
            Check("onSurface/background", scheme.onSurface, scheme.background, 4.5f),
            Check("onSurfaceVariant/surface", scheme.onSurfaceVariant, scheme.surface, 4.5f),
            Check("onSurfaceVariant/surfaceContainer", scheme.onSurfaceVariant, scheme.surfaceContainer, 4.5f),
            Check("onPrimary/primary", scheme.onPrimary, scheme.primary, 4.5f),
            Check("onPrimaryContainer/primaryContainer", scheme.onPrimaryContainer, scheme.primaryContainer, 4.5f),
            Check("onSecondaryContainer/secondaryContainer", scheme.onSecondaryContainer, scheme.secondaryContainer, 4.5f),
            Check("onTertiaryContainer/tertiaryContainer", scheme.onTertiaryContainer, scheme.tertiaryContainer, 4.5f),
            Check("onSurface/surfaceContainerHigh", scheme.onSurface, scheme.surfaceContainerHigh, 4.5f),
            Check("primary/background", scheme.primary, scheme.background, 3.0f),
            Check("outline/background", scheme.outline, scheme.background, 3.0f),
        )
        for (c in checks) {
            val ratio = contrast(c.foreground, c.background)
            if (ratio < c.minimum) {
                failures += "$label ${c.name} = %.2f (needs %.1f)".format(ratio, c.minimum)
            }
        }
    }

    private fun contrast(a: Color, b: Color): Float {
        val la = a.luminance()
        val lb = b.luminance()
        return (maxOf(la, lb) + 0.05f) / (minOf(la, lb) + 0.05f)
    }
}
