package com.kodastream.app.manga.reader

import okio.BufferedSource

/**
 * Ported from Mihon's `viewer/DualPageSettings.kt`: how a viewer treats a wide
 * page, either rotated to fit the screen or split in two halves.
 */
interface DualPageSettings {
    val dualPageSplit: Boolean
    val dualPageInvert: Boolean
    val dualPageRotateToFit: Boolean
    val dualPageRotateToFitInvert: Boolean
}

/**
 * Ported from Mihon's `viewer/PageImageProcessing.kt`. The image of [page] as
 * the pager shows it: a wide page rotated to fit or split in two, the half on
 * the reading side first. [onPageSplit] receives the [InsertPage] for the
 * second half.
 */
fun DualPageSettings.processPagedImage(
    page: ReaderPage,
    imageSource: BufferedSource,
    isLeftToRight: Boolean,
    onPageSplit: (InsertPage) -> Unit,
): BufferedSource {
    if (dualPageRotateToFit) {
        return rotateDualPage(imageSource)
    }
    if (!dualPageSplit) {
        return imageSource
    }
    if (page is InsertPage) {
        return splitInHalf(imageSource, page, isLeftToRight)
    }
    if (!ReaderImageUtil.isWideImage(imageSource)) {
        return imageSource
    }
    onPageSplit(InsertPage(page))
    return splitInHalf(imageSource, page, isLeftToRight)
}

private fun DualPageSettings.rotateDualPage(imageSource: BufferedSource): BufferedSource {
    if (!ReaderImageUtil.isWideImage(imageSource)) return imageSource
    val rotation = if (dualPageRotateToFitInvert) -90f else 90f
    return ReaderImageUtil.rotateImage(imageSource, rotation)
}

private fun DualPageSettings.splitInHalf(
    imageSource: BufferedSource,
    page: ReaderPage,
    isLeftToRight: Boolean,
): BufferedSource {
    var side = when {
        isLeftToRight && page is InsertPage -> ReaderImageUtil.Side.RIGHT
        !isLeftToRight && page is InsertPage -> ReaderImageUtil.Side.LEFT
        isLeftToRight -> ReaderImageUtil.Side.LEFT
        else -> ReaderImageUtil.Side.RIGHT
    }
    if (dualPageInvert) {
        side = if (side == ReaderImageUtil.Side.RIGHT) ReaderImageUtil.Side.LEFT else ReaderImageUtil.Side.RIGHT
    }
    return ReaderImageUtil.splitInHalf(imageSource, side)
}
