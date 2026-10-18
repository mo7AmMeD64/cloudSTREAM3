package com.lagradost.cloudstream3.ui

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.core.view.children
import androidx.recyclerview.widget.AsyncDifferConfig
import androidx.recyclerview.widget.AsyncListDiffer
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import androidx.viewbinding.ViewBinding
import java.util.WeakHashMap
import java.util.concurrent.CopyOnWriteArrayList

open class ViewHolderState<T>(val view: ViewBinding) : ViewHolder(view.root) {
    open fun save(): T? = null
    open fun restore(state: T) = Unit
}

abstract class NoStateAdapter<T : Any>(
    diffCallback: DiffUtil.ItemCallback<T> = BaseDiffCallback()
) : BaseAdapter<T, Any>(0, diffCallback)

/** Creates a new shared pool, using the supplied lambda as a constructor.
 *
 * The reason for this complicated structure is that a pool should not be shared between contexts
 * as it makes coil fuck up, and theming.
 * */
fun newSharedPool(lambda: RecyclerView.RecycledViewPool.() -> Unit = { }): Pair<WeakHashMap<Context, RecyclerView.RecycledViewPool>, RecyclerView.RecycledViewPool.() -> Unit> =
    WeakHashMap<Context, RecyclerView.RecycledViewPool>() to lambda

/** Sets the shared pool of the recyclerview */
fun RecyclerView.setRecycledViewPool(pool: Pair<WeakHashMap<Context, RecyclerView.RecycledViewPool>, RecyclerView.RecycledViewPool.() -> Unit>) {
    val ctx = context ?: return
    synchronized(pool.first) {
        this.setRecycledViewPool(pool.first.getOrPut(ctx) {
            RecyclerView.RecycledViewPool().apply(pool.second)
        })
    }
}

/** Clears the shared pool of views */
fun Pair<WeakHashMap<Context, RecyclerView.RecycledViewPool>, RecyclerView.RecycledViewPool.() -> Unit>.clear() {
    synchronized(this.first) {
        for (pool in this.first.values) {
            pool?.clear()
        }
    }
}



class BaseDiffCallback<T : Any>(
    val itemSame: (T, T) -> Boolean = { a, b -> a.hashCode() == b.hashCode() },
    val contentSame: (T, T) -> Boolean = { a, b -> a.hashCode() == b.hashCode() }
) : DiffUtil.ItemCallback<T>() {
    override fun areItemsTheSame(oldItem: T, newItem: T): Boolean = itemSame(oldItem, newItem)
    override fun areContentsTheSame(oldItem: T, newItem: T): Boolean = contentSame(oldItem, newItem)
    override fun getChangePayload(oldItem: T, newItem: T): Any? = Any()
}