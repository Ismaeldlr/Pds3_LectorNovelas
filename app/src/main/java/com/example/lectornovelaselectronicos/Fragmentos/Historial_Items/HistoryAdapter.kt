package com.example.lectornovelaselectronicos.Fragmentos.Historial_Items

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.lectornovelaselectronicos.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HistoryAdapter(
    private val onClick: (HistoryItem) -> Unit,
    private val onRemove: (HistoryItem) -> Unit,
) : RecyclerView.Adapter<HistoryAdapter.VH>() {

    private val items = mutableListOf<HistoryItem>()
    private val dateFormat = SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault())

    fun submit(list: List<HistoryItem>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_history_entry, parent, false)
        return VH(view, onClick, onRemove, dateFormat)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    class VH(
        view: View,
        private val onClick: (HistoryItem) -> Unit,
        private val onRemove: (HistoryItem) -> Unit,
        private val dateFormat: SimpleDateFormat,
    ) : RecyclerView.ViewHolder(view) {

        private val cover: ImageView = view.findViewById(R.id.imgCover)
        private val title: TextView = view.findViewById(R.id.tvTitle)
        private val meta: TextView = view.findViewById(R.id.tvMeta)
        private val progress: TextView = view.findViewById(R.id.tvProgress)
        private val timestamp: TextView = view.findViewById(R.id.tvTimestamp)
        private val progressBar: ProgressBar = view.findViewById(R.id.progressBar)
        private val btnRemove: View = view.findViewById(R.id.btnRemove)

        fun bind(item: HistoryItem) {
            val ctx = itemView.context
            val entry = item.entry
            val book = item.book

            val displayTitle = book?.title?.takeIf { it.isNotBlank() } ?: entry.bookTitle
            val displayAuthor = book?.author?.takeIf { it.isNotBlank() } ?: entry.bookAuthor
            title.text = displayTitle.ifBlank { ctx.getString(R.string.app_name) }
            meta.text = listOfNotNull(displayAuthor.takeIf { it.isNotBlank() })
                .joinToString(separator = " • ")
                .ifBlank { ctx.getString(R.string.historial_sin_autor) }

            val chapterLabel = if (entry.lastChapterIndex > 0) {
                ctx.getString(R.string.historial_capitulo, entry.lastChapterIndex)
            } else {
                ctx.getString(R.string.historial_capitulo, 1)
            }
            progress.text = ctx.getString(
                R.string.historial_resumen_capitulo,
                chapterLabel,
                entry.lastChapterTitle.ifBlank { ctx.getString(R.string.historial_capitulo_desconocido) },
            )

            val percent = item.progressPercent.coerceIn(0, 100)
            progressBar.max = 100
            progressBar.progress = percent
            val displayDate = entry.lastReadAt.takeIf { it > 0 } ?: System.currentTimeMillis()
            timestamp.text = dateFormat.format(Date(displayDate))

            Glide.with(cover)
                .load(book?.coverUrl ?: entry.coverUrl)
                .placeholder(R.drawable.placeholder_cover)
                .into(cover)

            itemView.setOnClickListener { onClick(item) }
            btnRemove.setOnClickListener { onRemove(item) }
        }
    }
}
