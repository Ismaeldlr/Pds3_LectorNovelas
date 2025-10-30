package com.example.lectornovelaselectronicos.Fragmentos.Biblioteca_Items

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import androidx.core.view.isVisible
import com.example.lectornovelaselectronicos.R
import java.util.Locale

class BookAdapter(
    private val onBookClick: (BookItem) -> Unit = {},
    private val onDeleteClick: (BookItem) -> Unit = {},
) : RecyclerView.Adapter<BookAdapter.VH>() {

    private val items = mutableListOf<BookItem>()

    fun submit(list: List<BookItem>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val img: ImageView = v.findViewById(R.id.imgCover)
        val title: TextView = v.findViewById(R.id.tvTitle)
        val badge: TextView = v.findViewById(R.id.tvBadge)
        val status: TextView = v.findViewById(R.id.tvStatus)
        val language: TextView = v.findViewById(R.id.tvLanguage)
        val more: ImageButton = v.findViewById(R.id.btnMore)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_book_card, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(h: VH, position: Int) {
        val book = items[position]
        h.title.text = book.title

        val chapterCount = book.effectiveChapterCount
        h.badge.isVisible = chapterCount > 0
        h.badge.text = if (chapterCount > 0) {
            h.itemView.context.getString(R.string.card_badge_chapters, chapterCount)
        } else ""

        val normalizedStatus = book.status.trim()
        h.status.isVisible = normalizedStatus.isNotEmpty()
        h.status.text = normalizedStatus.uppercase(Locale.getDefault())

        val normalizedLanguage = book.language.trim()
        h.language.isVisible = normalizedLanguage.isNotEmpty()
        h.language.text = normalizedLanguage.uppercase(Locale.getDefault())

        h.itemView.setOnClickListener {
            val idx = h.bindingAdapterPosition
            if (idx != RecyclerView.NO_POSITION) onBookClick(items[idx])
        }

        h.more.visibility = View.VISIBLE
        h.more.setOnClickListener { view ->
            val popup = PopupMenu(view.context, view)
            popup.menu.add(0, R.id.action_delete_card, 0, view.context.getString(R.string.borrar_libro))
            popup.setOnMenuItemClickListener { menuItem ->
                if (menuItem.itemId == R.id.action_delete_card) {
                    val idx = h.bindingAdapterPosition
                    if (idx != RecyclerView.NO_POSITION) onDeleteClick(items[idx])
                    true
                } else false
            }
            popup.show()
        }

        Glide.with(h.img).load(book.coverUrl)
            .placeholder(R.drawable.placeholder_cover)
            .into(h.img)
    }

    override fun getItemCount(): Int = items.size
}
