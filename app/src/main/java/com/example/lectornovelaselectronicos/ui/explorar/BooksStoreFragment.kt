package com.example.lectornovelaselectronicos.ui.explorar

import android.os.Bundle
import android.view.*
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.lectornovelaselectronicos.R
import com.example.lectornovelaselectronicos.data.BooksRepository
import com.example.lectornovelaselectronicos.data.LibraryStore
import com.example.lectornovelaselectronicos.databinding.FragmentBookStoreBinding
import com.example.lectornovelaselectronicos.databinding.ItemBookStoreBinding
import com.example.lectornovelaselectronicos.models.Book

class BooksStoreFragment : Fragment() {

    private var _binding: FragmentBookStoreBinding? = null
    private val binding get() = _binding!!

    private val adapter = StoreAdapter(
        onPreview = { book -> showPreview(book) },
        onToggleAdd = { book -> toggleAdd(book) }
    )

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentBookStoreBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.rvLibros.layoutManager = LinearLayoutManager(requireContext())
        binding.rvLibros.adapter = adapter
        adapter.submit(BooksRepository.catalogo)
    }

    private fun showPreview(book: Book) {
        val msg = getString(R.string.preview_formato, book.titulo, book.autor, book.descripcion)
        Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show()
    }

    private fun toggleAdd(book: Book) {
        val ctx = requireContext()
        val yaEsta = LibraryStore.estaEnMiBiblioteca(ctx, book.id)
        if (yaEsta) {
            LibraryStore.quitar(ctx, book.id)
            Toast.makeText(ctx, getString(R.string.removido_biblioteca, book.titulo), Toast.LENGTH_SHORT).show()
        } else {
            LibraryStore.agregar(ctx, book.id)
            Toast.makeText(ctx, getString(R.string.agregado_biblioteca, book.titulo), Toast.LENGTH_SHORT).show()
        }
        adapter.notifyDataSetChanged()
    }

    override fun onDestroyView() {
        super.onDestroyView(); _binding = null
    }

    companion object { fun newInstance() = BooksStoreFragment() }
}

/* ---------------- Adapter ---------------- */

private class StoreAdapter(
    val onPreview: (Book) -> Unit,
    val onToggleAdd: (Book) -> Unit
) : androidx.recyclerview.widget.RecyclerView.Adapter<StoreVH>() {

    private val items = mutableListOf<Book>()

    fun submit(list: List<Book>) { items.clear(); items.addAll(list); notifyDataSetChanged() }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StoreVH {
        val b = ItemBookStoreBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return StoreVH(b, onPreview, onToggleAdd)
    }

    override fun onBindViewHolder(holder: StoreVH, position: Int) = holder.bind(items[position])

    override fun getItemCount() = items.size
}

private class StoreVH(
    private val b: ItemBookStoreBinding,
    private val onPreview: (Book) -> Unit,
    private val onToggleAdd: (Book) -> Unit
) : androidx.recyclerview.widget.RecyclerView.ViewHolder(b.root) {

    fun bind(book: Book) {
        val ctx = b.root.context
        b.tvTitulo.text = book.titulo
        b.tvAutor.text = ctx.getString(R.string.formato_autor, book.autor)

        // Texto del botón depende de si ya está en la biblioteca
        val enLib = com.example.lectornovelaselectronicos.data.LibraryStore.estaEnMiBiblioteca(ctx, book.id)
        b.btnAgregar.text = if (enLib) ctx.getString(R.string.quitar_biblioteca) else ctx.getString(R.string.agregar_biblioteca)

        b.btnPreview.setOnClickListener { onPreview(book) }
        b.btnAgregar.setOnClickListener { onToggleAdd(book) }
    }
}
