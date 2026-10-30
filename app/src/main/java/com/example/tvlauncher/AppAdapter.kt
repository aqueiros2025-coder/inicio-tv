package com.example.tvlauncher

import android.content.ComponentName
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

data class AppEntry(
    val label: String,
    val packageName: String,
    val component: ComponentName,
    val icon: Drawable,
    val banner: Drawable?
)

class AppAdapter(
    private val layoutRes: Int,
    private val large: Boolean,
    private val onFocus: (AppEntry) -> Unit,
    private val onOpen: (AppEntry) -> Unit,
    private val onInfo: (AppEntry) -> Unit
) : RecyclerView.Adapter<AppAdapter.VH>() {

    private var items: List<AppEntry> = emptyList()

    fun submit(list: List<AppEntry>) {
        items = list
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = items.size

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val img: ImageView = view.findViewById(R.id.img)
        val label: TextView = view.findViewById(R.id.label)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context).inflate(layoutRes, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val entry = items[position]
        holder.label.text = entry.label

        if (large) {
            val pad = (28 * holder.itemView.resources.displayMetrics.density).toInt()
            val banner = entry.banner
            if (banner != null) {
                // Banner de TV (16:9): preenche o cartão e já traz o nome da app.
                holder.img.scaleType = ImageView.ScaleType.CENTER_CROP
                holder.img.setPadding(0, 0, 0, 0)
                holder.img.setImageDrawable(banner)
                holder.label.visibility = View.GONE
            } else {
                holder.img.scaleType = ImageView.ScaleType.FIT_CENTER
                holder.img.setPadding(pad, pad, pad, pad)
                holder.img.setImageDrawable(entry.icon)
                holder.label.visibility = View.VISIBLE
            }
        } else {
            holder.img.setImageDrawable(entry.icon)
        }

        val v = holder.itemView
        v.setOnClickListener { onOpen(entry) }
        v.setOnLongClickListener {
            onInfo(entry)
            true
        }
        v.setOnFocusChangeListener { view, hasFocus ->
            val scale = if (hasFocus) 1.12f else 1f
            view.animate().scaleX(scale).scaleY(scale).setDuration(150).start()
            view.translationZ = if (hasFocus) 24f else 0f
            if (hasFocus) onFocus(entry)
        }
    }
}
