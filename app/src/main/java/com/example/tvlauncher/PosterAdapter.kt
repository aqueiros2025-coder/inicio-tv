package com.example.tvlauncher

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

// Cartões de poster (filmes e séries). O título só aparece por baixo do cartão em foco.
class PosterAdapter(
    private val onOpen: (MediaItem) -> Unit,
    private val onLong: (MediaItem) -> Unit
) : RecyclerView.Adapter<PosterAdapter.VH>() {

    private var items: List<MediaItem> = emptyList()

    fun submit(list: List<MediaItem>) {
        items = list
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = items.size

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val img: ImageView = view.findViewById(R.id.img)
        val label: TextView = view.findViewById(R.id.label)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_poster, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.label.text = item.name
        holder.label.alpha = 0f
        holder.label.isSelected = false
        holder.img.setImageDrawable(null)
        holder.img.tag = item.poster
        ImageLoader.load(item.poster, 224, 336) { bmp ->
            if (bmp != null && holder.img.tag == item.poster) holder.img.setImageBitmap(bmp)
        }

        val v = holder.itemView
        v.setOnClickListener { onOpen(item) }
        v.setOnLongClickListener {
            onLong(item)
            true
        }
        v.setOnFocusChangeListener { view, hasFocus ->
            val scale = if (hasFocus) 1.15f else 1f
            view.animate().scaleX(scale).scaleY(scale).setDuration(150).start()
            view.translationZ = if (hasFocus) 24f else 0f
            holder.label.isSelected = hasFocus
            holder.label.animate().alpha(if (hasFocus) 1f else 0f).setDuration(150).start()
        }
    }
}
