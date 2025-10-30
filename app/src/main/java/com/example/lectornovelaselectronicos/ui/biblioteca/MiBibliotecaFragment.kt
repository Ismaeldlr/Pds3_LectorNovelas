package com.example.lectornovelaselectronicos.ui.biblioteca

import android.os.Bundle
import android.view.*
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.lectornovelaselectronicos.data.BooksRepository
import com.example.lectornovelaselectronicos.data.LibraryStore
import com.example.lectornovelaselectronicos.databinding.FragmentBookStoreBinding
import com.example.lectornovelaselectronicos.databinding.ItemBookStoreBinding
import com.example.lectornovelaselectronicos.models.Book

class MiBibliotecaFragment : Fragment() {

    private var _binding: FragmentBookStoreBinding? = null
    private val binding get() = _binding!!

    private val adapter = BibliotecaAdapter()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentBookStoreBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.tvTitulo.text = getString(com.example.lectornovelaselectronicos.R.string.titulo_mi_biblioteca)
        binding.rvLibros.layoutManager = LinearLayoutManager(requireContext())
        binding.rvLibros.adapter = adapter
    }

    override fun onResume() {
        super.onResume()
        val ids = LibraryStore.getMisIds(requireContext())
        val libros = ids.mapNotNull { BooksRepository.getById(it) }
        adapter.submit(libros)
    }

    override fun onDestroyView() {
        super.onDestroyView(); _binding = null
    }
}

/* Reuso layout/item_book_store.xml para mostrar solo título/autor (sin botones) */

private class BibliotecaAdapter : androidx.recyclerview.widget.RecyclerView.Adapter<BibVH>() {
    private val items = mutableListOf<Book>()
    fun submit(list: List<Book>) { items.clear(); items.addAll(list); notifyDataSetChanged() }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BibVH {
        val b = ItemBookStoreBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        // ocultar botones
        b.btnPreview.visibility = View.GONE
        b.btnAgregar.visibility = View.GONE
        return BibVH(b)
    }
    override fun onBindViewHolder(holder: BibVH, position: Int) = holder.bind(items[position])
    override fun getItemCount() = items.size
}

private class BibVH(private val b: ItemBookStoreBinding) : androidx.recyclerview.widget.RecyclerView.ViewHolder(b.root) {
    fun bind(book: Book) {
        val ctx = b.root.context
        b.tvTitulo.text = book.titulo
        b.tvAutor.text = ctx.getString(com.example.lectornovelaselectronicos.R.string.formato_autor, book.autor)
    }
}
