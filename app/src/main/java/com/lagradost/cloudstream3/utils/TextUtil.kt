package com.lagradost.cloudstream3.utils

import android.content.Context
import android.text.Spanned
import android.text.TextUtils
import android.util.Log
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.core.text.HtmlCompat
import androidx.core.text.toSpanned
import androidx.core.view.isGone
import androidx.core.view.isVisible
import com.lagradost.cloudstream3.mvvm.logError

/**
 * UiText and the txt() builders from the original app's TextUtil, brought
 * over for the ported player's ViewBinding screens. The html() helper that
 * lived in AppContextUtils is inlined here so the whole 904-line file could
 * stay behind.
 */
private const val TAG = "TEXT_VIEW"

sealed class UiText {
    data class DynamicString(val value: String) : UiText() {
        override fun toString(): String = value
    }

    class StringResource(
        @StringRes val resId: Int,
        val args: List<Any>,
    ) : UiText() {
        override fun toString(): String =
            "resId = $resId\nargs = ${args.map { "(${it::class} = $it)" }}"
    }

    fun asStringNull(context: Context?): String? {
        return try {
            asString(context ?: return null)
        } catch (e: Exception) {
            Log.e(TAG, "Got invalid data from $this")
            logError(e)
            null
        }
    }

    fun asString(context: Context): String {
        return when (this) {
            is DynamicString -> value
            is StringResource -> {
                val str = context.getString(resId)
                if (args.isEmpty()) {
                    str
                } else {
                    str.format(*args.map {
                        when (it) {
                            is UiText -> it.asString(context)
                            else -> it
                        }
                    }.toTypedArray())
                }
            }
        }
    }
}

fun txt(value: String): UiText {
    return UiText.DynamicString(value)
}

fun txt(value: String?): UiText? {
    return value?.let { txt(it) }
}

fun txt(@StringRes resId: Int, vararg args: Any): UiText {
    return UiText.StringResource(resId, args.toList())
}

@JvmName("txtNull")
fun txt(@StringRes resId: Int?, vararg args: Any?): UiText? {
    if (resId == null || args.any { it == null }) {
        return null
    }
    return UiText.StringResource(resId, args.filterNotNull().toList())
}

fun TextView?.setText(text: UiText?) {
    if (this == null) return
    if (text == null) {
        this.isVisible = false
    } else {
        val str = text.asStringNull(context)?.let {
            if (this.maxLines == 1) {
                it.replace("\n", " ")
            } else {
                it
            }
        }

        this.isGone = str.isNullOrBlank()
        this.text = str
    }
}

/** Inlined from AppContextUtils. */
private fun getHtmlText(text: String): Spanned {
    return try {
        HtmlCompat.fromHtml(text, HtmlCompat.FROM_HTML_MODE_LEGACY)
    } catch (e: Exception) {
        logError(e)
        text.toSpanned()
    }
}

fun String?.html(): Spanned {
    return getHtmlText(this ?: return "".toSpanned())
}

fun TextView?.setTextHtml(text: UiText?) {
    if (this == null) return
    if (text == null) {
        this.isVisible = false
    } else {
        val str = text.asStringNull(context)
        this.isGone = str.isNullOrBlank()
        this.text = str.html()
    }
}
