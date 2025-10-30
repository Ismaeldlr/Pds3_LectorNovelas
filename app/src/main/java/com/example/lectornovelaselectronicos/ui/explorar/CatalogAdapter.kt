package com.example.lectornovelaselectronicos.ui.explorar

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.lectornovelaselectronicos.Fragmentos.Biblioteca_Items.BookItem
import com.example.lectornovelaselectronicos.R

class CatalogAdapter(
    private val onBookClick: (BookItem) -> Unit,
) : RecyclerView.Adapter<CatalogAdapter.VH>() {

    private val items = mutableListOf<BookItem>()

    fun submit(list: List<BookItem>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_book_card, parent, false)
        return VH(view, onBookClick)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    class VH(
        view: View,
        private val onBookClick: (BookItem) -> Unit,
    ) : RecyclerView.ViewHolder(view) {

        private val img: ImageView = view.findViewById(R.id.imgCover)
        private val title: TextView = view.findViewById(R.id.tvTitle)
        private val badge: TextView = view.findViewById(R.id.tvBadge)
        private val more: View = view.findViewById(R.id.btnMore)

        fun bind(book: BookItem) {
            title.text = book.title
            if (book.chapters > 0) {
                badge.visibility = View.VISIBLE
                badge.text = book.chapters.toString()
            } else {
                badge.visibility = View.GONE
            }
            more.visibility = View.GONE

            itemView.setOnClickListener { onBookClick(book) }

            Glide.with(img)
                .load(book.coverUrl)
                .placeholder(R.drawable.placeholder_cover)
                .into(img)
        }
    }
}
