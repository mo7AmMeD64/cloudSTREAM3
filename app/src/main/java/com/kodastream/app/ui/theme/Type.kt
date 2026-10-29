package com.kodastream.app.ui.theme

import androidx.compose.material3.Typography

/**
 * Material's own type scale on the platform font. The downloadable Roboto
 * Flex and Montserrat families needed GMS and a network fetch before first
 * paint, which delayed text and reflowed every screen when the font arrived.
 * Arabic already fell back to the platform font, so this also makes both
 * languages render the same way on every device.
 */
val Typography = Typography()
