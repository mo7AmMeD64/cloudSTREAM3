@file:Suppress("PackageDirectoryMismatch")

package androidx.recyclerview.widget

import android.content.Context

/**
 * Ported from Mihon's `viewer/webtoon/WebtoonLayoutManager.kt`. Item prefetch
 * is disabled because the extra layout space is what keeps a page decoded just
 * before it scrolls on screen, avoiding black views.
 */
class WebtoonLayoutManager(context: Context, private val extraLayoutSpace: Int) :
    LinearLayoutManager(context) {

    init {
        isItemPrefetchEnabled = false
    }

    @Deprecated("Deprecated in Java")
    override fun getExtraLayoutSpace(state: RecyclerView.State): Int = extraLayoutSpace

    /** The last item whose end edge is visible on screen. */
    fun findLastEndVisibleItemPosition(): Int {
        ensureLayoutState()
        val callback = if (mOrientation == HORIZONTAL) {
            mHorizontalBoundCheck
        } else {
            mVerticalBoundCheck
        }.mCallback
        val start = callback.parentStart
        val end = callback.parentEnd
        for (i in childCount - 1 downTo 0) {
            val child = getChildAt(i) ?: continue
            val childStart = callback.getChildStart(child)
            val childEnd = callback.getChildEnd(child)
            if (childEnd <= end || childStart < start) {
                return getPosition(child)
            }
        }
        return RecyclerView.NO_POSITION
    }
}
