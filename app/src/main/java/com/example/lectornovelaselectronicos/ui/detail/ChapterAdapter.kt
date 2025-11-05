package com.example.lectornovelaselectronicos.ui.detail

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.lectornovelaselectronicos.Fragmentos.Biblioteca_Items.ChapterSummary
import com.example.lectornovelaselectronicos.R

class ChapterAdapter(
    private val onChapterClick: (ChapterSummary, Int) -> Unit,
) : ListAdapter<ChapterSummary, ChapterAdapter.VH>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_chapter, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position))
        holder.itemView.setOnClickListener {
            val adapterPosition = holder.bindingAdapterPosition
            if (adapterPosition != RecyclerView.NO_POSITION) {
                onChapterClick(getItem(adapterPosition), adapterPosition)
            }
        }
    }

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        private val title: TextView = view.findViewById(R.id.tvChapterTitle)
        private val meta: TextView = view.findViewById(R.id.tvChapterMeta)

        fun bind(chapter: ChapterSummary) {
            val displayTitle = chapter.title.ifBlank {
                itemView.context.getString(R.string.book_detail_chapter_placeholder, chapter.index)
            }
            title.text = displayTitle

            val context = itemView.context
            val parts = mutableListOf<String>()

            if (chapter.index > 0) {
                parts += "#${chapter.index}"
            } else {
                parts += "#?"
            }

            chapter.variant.takeIf { it.isNotBlank() }?.let {
                parts += context.getString(R.string.chapter_meta_variant, it)
            }

            chapter.language.takeIf { it.isNotBlank() }?.let {
                parts += context.getString(R.string.chapter_meta_language, it)
            }

            chapter.releaseDate?.takeIf { it.isNotBlank() }?.let { parts += it }

            meta.text = parts.joinToString(separator = " • ")
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<ChapterSummary>() {
        override fun areItemsTheSame(oldItem: ChapterSummary, newItem: ChapterSummary): Boolean {
            return oldItem.index == newItem.index && oldItem.title == newItem.title
        }

        override fun areContentsTheSame(oldItem: ChapterSummary, newItem: ChapterSummary): Boolean {
            return oldItem == newItem
        }
    }
}
