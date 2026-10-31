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

// Mosaicos quadrados. Quando labelOnFocus é verdadeiro (filas grandes) o nome só aparece
// por baixo do mosaico que está em foco, como na PS5; nas filas pequenas o nome está sempre visível.
class AppAdapter(
    private val layoutRes: Int,
    private val labelOnFocus: Boolean,
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
        holder.img.setImageDrawable(entry.icon)
        holder.label.text = entry.label
        holder.label.alpha = if (labelOnFocus) 0f else 1f
        holder.label.isSelected = false

        val v = holder.itemView
        v.setOnClickListener { onOpen(entry) }
        v.setOnLongClickListener {
            onInfo(entry)
            true
        }
        v.setOnFocusChangeListener { view, hasFocus ->
            val scale = if (hasFocus) (if (labelOnFocus) 1.2f else 1.12f) else 1f
            view.animate().scaleX(scale).scaleY(scale).setDuration(150).start()
            view.translationZ = if (hasFocus) 24f else 0f
            holder.label.isSelected = hasFocus
            if (labelOnFocus) {
                holder.label.animate().alpha(if (hasFocus) 1f else 0f).setDuration(150).start()
            }
        }
    }
}
