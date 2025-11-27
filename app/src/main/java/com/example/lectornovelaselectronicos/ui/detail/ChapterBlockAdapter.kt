package com.example.lectornovelaselectronicos.ui.detail

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.example.lectornovelaselectronicos.Fragmentos.Biblioteca_Items.ChapterSummary
import com.example.lectornovelaselectronicos.R

private const val VIEW_TYPE_HEADER = 0
private const val VIEW_TYPE_CHAPTER = 1

class ChapterBlockAdapter(
    private val onChapterClick: (ChapterSummary, Int) -> Unit,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val blocks = mutableListOf<ChapterBlock>()
    private val items = mutableListOf<ListItem>()

    fun submitChapters(chapters: List<ChapterSummary>) {
        blocks.clear()
        if (chapters.isEmpty()) {
            items.clear()
            notifyDataSetChanged()
            return
        }

        val newBlocks = chapters.chunked(CHAPTERS_PER_BLOCK).mapIndexed { blockIndex, chunk ->
            val startLabel = blockIndex * CHAPTERS_PER_BLOCK + 1
            val endLabel = startLabel + chunk.size - 1
            ChapterBlock(
                startLabel = startLabel,
                endLabel = endLabel,
                startPosition = blockIndex * CHAPTERS_PER_BLOCK,
                chapters = chunk,
                expanded = blockIndex == 0,
            )
        }

        blocks.addAll(newBlocks)
        rebuildItems()
    }

    override fun getItemViewType(position: Int): Int =
        when (items[position]) {
            is ListItem.Header -> VIEW_TYPE_HEADER
            is ListItem.Chapter -> VIEW_TYPE_CHAPTER
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_HEADER -> HeaderViewHolder(
                inflater.inflate(R.layout.item_chapter_block_header, parent, false),
            )

            else -> ChapterViewHolder(
                inflater.inflate(R.layout.item_chapter, parent, false),
            )
        }
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is ListItem.Header -> (holder as HeaderViewHolder).bind(item.block)
            is ListItem.Chapter -> (holder as ChapterViewHolder).bind(item.chapter)
        }

        if (holder is ChapterViewHolder) {
            holder.itemView.setOnClickListener {
                val adapterPosition = holder.bindingAdapterPosition
                if (adapterPosition != RecyclerView.NO_POSITION) {
                    val currentItem = items[adapterPosition] as ListItem.Chapter
                    onChapterClick(currentItem.chapter, currentItem.globalPosition)
                }
            }
        }
    }

    private fun rebuildItems() {
        items.clear()
        blocks.forEachIndexed { blockIndex, block ->
            items.add(ListItem.Header(block))
            if (block.expanded) {
                block.chapters.forEachIndexed { indexInBlock, chapter ->
                    val globalPosition = block.startPosition + indexInBlock
                    items.add(ListItem.Chapter(blockIndex, chapter, globalPosition))
                }
            }
        }
        notifyDataSetChanged()
    }

    private fun toggleBlock(block: ChapterBlock) {
        block.expanded = !block.expanded
        rebuildItems()
    }

    private inner class HeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val title: TextView = view.findViewById(R.id.tvBlockTitle)
        private val subtitle: TextView = view.findViewById(R.id.tvBlockSubtitle)
        private val icon: ImageView = view.findViewById(R.id.imgToggle)

        fun bind(block: ChapterBlock) {
            val context = itemView.context
            title.text = context.getString(R.string.chapter_block_label, block.startLabel, block.endLabel)
            subtitle.isVisible = block.chapters.any { it.title.isNotBlank() }
            subtitle.text = context.resources.getQuantityString(
                R.plurals.chapter_block_count,
                block.chapters.size,
                block.chapters.size,
            )

            icon.rotation = if (block.expanded) 180f else 0f
            itemView.setOnClickListener { toggleBlock(block) }
        }
    }

    private class ChapterViewHolder(view: View) : RecyclerView.ViewHolder(view) {
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
}

private data class ChapterBlock(
    val startLabel: Int,
    val endLabel: Int,
    val startPosition: Int,
    val chapters: List<ChapterSummary>,
    var expanded: Boolean,
)

private sealed class ListItem {
    data class Header(val block: ChapterBlock) : ListItem()
    data class Chapter(val blockIndex: Int, val chapter: ChapterSummary, val globalPosition: Int) : ListItem()
}

private const val CHAPTERS_PER_BLOCK = 100
