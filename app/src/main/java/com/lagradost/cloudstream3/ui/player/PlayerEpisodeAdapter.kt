package com.lagradost.cloudstream3.ui.player

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.kodastream.app.R
import com.kodastream.app.databinding.PlayerEpisodesBinding
import com.lagradost.cloudstream3.ui.result.ResultEpisode
import com.lagradost.cloudstream3.ui.result.VideoWatchState

/**
 * The player's episode list. The original used the result screen's adapter
 * (with download buttons, cast and long-press options); the player only needs
 * selection, so this is the minimal equivalent.
 */
class PlayerEpisodeAdapter(
    private val onClick: (ResultEpisode) -> Unit,
) : RecyclerView.Adapter<PlayerEpisodeAdapter.ViewHolder>() {

    private var episodes: List<ResultEpisode> = emptyList()

    class ViewHolder(val binding: PlayerEpisodesBinding) : RecyclerView.ViewHolder(binding.root)

    fun submitList(newEpisodes: List<ResultEpisode>) {
        val old = episodes
        episodes = newEpisodes
        DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = old.size
            override fun getNewListSize() = newEpisodes.size
            override fun areItemsTheSame(o: Int, n: Int) = old[o].id == newEpisodes[n].id
            override fun areContentsTheSame(o: Int, n: Int) = old[o] == newEpisodes[n]
        }).dispatchUpdatesTo(this)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(
            PlayerEpisodesBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )
    }

    override fun getItemCount() = episodes.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val episode = episodes[position]
        holder.binding.apply {
            playerEpisodeNumber.text = episode.episode.toString()
            playerEpisodeTitle.text = episode.name ?: episode.headerName
            playerEpisodeMeta.text = listOfNotNull(
                episode.season?.takeIf { it > 0 }?.let { "S$it" },
                episode.runTime?.let { "${it}m" },
            ).joinToString(" · ")
            playerEpisodeMeta.isVisible = playerEpisodeMeta.text.isNotBlank()
            playerEpisodeWatched.isVisible =
                episode.videoWatchState == VideoWatchState.Watched
            root.setOnClickListener { onClick(episode) }
        }
    }
}
