package com.kodastream.app.manga.reader

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.view.View
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import com.kodastream.app.manga.translate.PageTranslation
import com.kodastream.app.manga.translate.TranslatedBlock
import kotlin.math.min

/**
 * Draws the translated text as a layer over the page. Text is laid out in the
 * page's own pixel space and then drawn through the view transform, so it stays
 * sharp while zooming instead of being a stretched bitmap.
 */
internal class TranslationOverlayView(context: Context) : View(context) {

    private var translation: PageTranslation? = null
    private var paged: SubsamplingScaleImageView? = null
    private var stripWidth = 0
    private var stripHeight = 0

    private val layouts = HashMap<String, StaticLayout>()
    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        typeface = Typeface.DEFAULT
    }
    private val bubblePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val box = RectF()

    init {
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    fun bindPaged(view: SubsamplingScaleImageView) {
        paged = view
        stripWidth = 0
        stripHeight = 0
        invalidate()
    }

    fun bindStrip(width: Int, height: Int) {
        paged = null
        stripWidth = width
        stripHeight = height
        invalidate()
    }

    fun setTranslation(value: PageTranslation?) {
        translation = value
        layouts.clear()
        invalidate()
    }

    fun clear() {
        translation = null
        layouts.clear()
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val blocks = translation?.blocks ?: return
        if (blocks.isEmpty()) return
        val source = paged
        if (source != null) {
            if (!source.isReady || source.sWidth <= 0 || source.sHeight <= 0) return
            val origin = source.sourceToViewCoord(0f, 0f) ?: return
            val corner = source.sourceToViewCoord(source.sWidth.toFloat(), source.sHeight.toFloat())
                ?: return
            val scaleX = (corner.x - origin.x) / source.sWidth
            val scaleY = (corner.y - origin.y) / source.sHeight
            if (scaleX <= 0f || scaleY <= 0f) return
            canvas.save()
            canvas.translate(origin.x, origin.y)
            canvas.scale(scaleX, scaleY)
            drawBlocks(canvas, blocks, source.sWidth.toFloat(), source.sHeight.toFloat())
            canvas.restore()
            return
        }
        if (stripWidth <= 0 || stripHeight <= 0 || width <= 0) return
        val scale = width.toFloat() / stripWidth
        if (scale <= 0f) return
        canvas.save()
        canvas.scale(scale, scale)
        drawBlocks(canvas, blocks, stripWidth.toFloat(), stripHeight.toFloat())
        canvas.restore()
    }

    private fun drawBlocks(
        canvas: Canvas,
        blocks: List<TranslatedBlock>,
        width: Float,
        height: Float,
    ) {
        if (width <= 0f || height <= 0f) return
        for (block in blocks) {
            if (block.type == "sfx") continue
            val fraction = block.normalizedBox() ?: continue
            val left = fraction[0] * width
            val top = fraction[1] * height
            val right = fraction[2] * width
            val bottom = fraction[3] * height
            val boxWidth = right - left
            val boxHeight = bottom - top
            if (boxWidth < 6f || boxHeight < 6f) continue

            val radius = min(boxWidth, boxHeight) * 0.16f
            box.set(left, top, right, bottom)
            // The sampled fill hides the original lettering without a white patch
            // over coloured bubbles; 240 alpha leaves a trace of the artwork.
            val fill = block.background.takeIf { it != 0 } ?: FALLBACK_FILL
            bubblePaint.color = Color.argb(240, Color.red(fill), Color.green(fill), Color.blue(fill))
            canvas.drawRoundRect(box, radius, radius, bubblePaint)

            val padding = min(boxWidth, boxHeight) * 0.08f
            val innerWidth = (boxWidth - padding * 2).toInt().coerceAtLeast(1)
            val innerHeight = (boxHeight - padding * 2).toInt().coerceAtLeast(1)
            val textColor = if (isDark(fill)) Color.WHITE else Color.BLACK
            val layout = layout(block.translation, innerWidth, innerHeight, textColor) ?: continue
            canvas.save()
            canvas.clipRect(left, top, right, bottom)
            canvas.translate(left + padding, top + padding)
            layout.draw(canvas)
            canvas.restore()
        }
    }

    private fun layout(text: String, width: Int, height: Int, textColor: Int): StaticLayout? {
        if (text.isBlank()) return null
        val key = "$width|$height|$textColor|$text"
        layouts[key]?.let { return it }

        var size = min(height * 0.62f, width * 0.34f).coerceAtLeast(6f)
        var built: StaticLayout? = null
        while (size >= 6f) {
            val candidate = buildLayout(text, width, size, textColor)
            if (candidate.height <= height) {
                built = candidate
                break
            }
            size *= 0.86f
        }
        val result = built ?: buildLayout(text, width, 6f, textColor)
        if (layouts.size > MAX_CACHED_LAYOUTS) layouts.clear()
        layouts[key] = result
        return result
    }

    private fun buildLayout(
        text: String,
        width: Int,
        textSize: Float,
        textColor: Int,
    ): StaticLayout {
        val paint = TextPaint(textPaint).apply {
            this.textSize = textSize
            color = textColor
        }
        return StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setIncludePad(false)
            .setLineSpacing(0f, 1.05f)
            .setEllipsize(TextUtils.TruncateAt.END)
            .setMaxLines(MAX_LINES)
            .build()
    }

    private companion object {
        const val MAX_CACHED_LAYOUTS = 160
        const val MAX_LINES = 20

        /** Used when the page bitmap could not be sampled (for example a cached entry). */
        val FALLBACK_FILL = Color.rgb(255, 255, 255)

        fun isDark(color: Int): Boolean =
            (0.299f * Color.red(color) + 0.587f * Color.green(color) +
                0.114f * Color.blue(color)) / 255f < 0.5f
    }
}
