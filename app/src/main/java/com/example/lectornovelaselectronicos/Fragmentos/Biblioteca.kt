package com.example.lectornovelaselectronicos.Fragmentos

import android.app.AlertDialog
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.lectornovelaselectronicos.Fragmentos.Biblioteca_Items.BookAdapter
import com.example.lectornovelaselectronicos.Fragmentos.Biblioteca_Items.BookItem
import com.example.lectornovelaselectronicos.Fragmentos.Biblioteca_Items.SpacingDecoration
import com.example.lectornovelaselectronicos.R
import com.example.lectornovelaselectronicos.data.FirebaseBookRepository
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class Biblioteca : Fragment() {

    private lateinit var recycler: RecyclerView
    private lateinit var toolbar: androidx.appcompat.widget.Toolbar
    private lateinit var emptyView: TextView

    private val adapter = BookAdapter(
        onBookClick = { book -> showDetails(book) },
        onDeleteClick = { book -> confirmRemove(book) },
    )

    private val catalogMap = linkedMapOf<String, BookItem>()
    private var libraryIds: Set<String> = emptySet()

    private var catalogListener: ValueEventListener? = null
    private var libraryListener: ValueEventListener? = null
    private var libraryUid: String? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_biblioteca, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        toolbar = view.findViewById(R.id.toolbar)
        emptyView = view.findViewById(R.id.tvEmpty)
        toolbar.title = getString(R.string.titulo_biblioteca)
        toolbar.inflateMenu(R.menu.menu_biblioteca)
        toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_add -> {
                    showAddDialog(); true
                }
                else -> false
            }
        }

        recycler = view.findViewById(R.id.rvLibrary)
        recycler.layoutManager = GridLayoutManager(requireContext(), 2)
        recycler.adapter = adapter
        recycler.addItemDecoration(SpacingDecoration(resources.getDimensionPixelSize(R.dimen.grid_gap)))

        listenCatalog()
        listenUserLibrary()
        seedIfEmpty()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        catalogListener?.let { FirebaseBookRepository.catalogReference().removeEventListener(it) }
        libraryListener?.let { listener ->
            libraryUid?.let { uid ->
                FirebaseDatabase.getInstance().getReference("user_libraries").child(uid).removeEventListener(listener)
            }
        }
        catalogListener = null
        libraryListener = null
        libraryUid = null
    }

    private fun listenCatalog() {
        catalogListener = FirebaseBookRepository.listenCatalog(
            onData = { list ->
                catalogMap.clear()
                list.forEach { book ->
                    val key = book.id
                    if (key != null) catalogMap[key] = book
                }
                updateLibrary()
            },
            onError = { error -> handleError(error) },
        )
    }

    private fun listenUserLibrary() {
        val uid = FirebaseBookRepository.currentUserId()
        if (uid == null) {
            libraryUid = null
            libraryIds = emptySet()
            adapter.submit(emptyList())
            emptyView.visibility = View.VISIBLE
            emptyView.setText(R.string.inicia_sesion_para_guardar)
            return
        }

        libraryUid = uid
        libraryListener = FirebaseBookRepository.listenUserLibrary(
            uid,
            onData = { ids ->
                libraryIds = ids
                updateLibrary()
            },
            onError = { error -> handleError(error) },
        )
    }

    private fun updateLibrary() {
        if (libraryUid == null) {
            emptyView.visibility = View.VISIBLE
            emptyView.setText(R.string.inicia_sesion_para_guardar)
            return
        }
        val items = libraryIds.mapNotNull { catalogMap[it] }
        adapter.submit(items)
        if (items.isEmpty()) {
            emptyView.visibility = View.VISIBLE
            emptyView.setText(R.string.mi_biblioteca_vacia)
        } else {
            emptyView.visibility = View.GONE
        }
    }

    private fun showDetails(book: BookItem) {
        val message = buildString {
            if (book.author.isNotBlank()) {
                append(getString(R.string.formato_autor, book.author)).append('\n')
            }
            if (book.chapters > 0) {
                append(getString(R.string.formato_capitulos, book.chapters)).append('\n')
            }
            if (book.description.isNotBlank()) {
                append('\n').append(book.description)
            }
        }.ifBlank { getString(R.string.sin_detalles_disponibles) }

        AlertDialog.Builder(requireContext())
            .setTitle(book.title)
            .setMessage(message)
            .setPositiveButton(R.string.cerrar, null)
            .show()
    }

    private fun confirmRemove(book: BookItem) {
        val id = book.id ?: return
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.borrar_libro_titulo, book.title))
            .setMessage(R.string.borrar_libro_confirmacion)
            .setPositiveButton(R.string.quitar_biblioteca) { dialog, _ ->
                FirebaseBookRepository.removeFromUserLibrary(id)
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancelar, null)
            .show()
    }

    private fun seedIfEmpty() {
        FirebaseBookRepository.catalogReference().get().addOnSuccessListener { snap ->
            if (!snap.hasChildren()) {
                val demo = listOf(
                    BookItem(
                        title = "Shaman King",
                        author = "Hiroyuki Takei",
                        chapters = 305,
                        coverUrl = "https://m.media-amazon.com/images/I/816k5LqS9kL._AC_UF1000,1000_QL80_.jpg",
                        description = getString(R.string.demo_shaman_king)
                    ),
                    BookItem(
                        title = "Gachiakuta",
                        author = "Kei Urana",
                        chapters = 148,
                        coverUrl = "https://m.media-amazon.com/images/I/81xZp3u2-YL._AC_UF1000,1000_QL80_.jpg",
                        description = getString(R.string.demo_gachiakuta)
                    ),
                    BookItem(
                        title = "D.Gray-man",
                        author = "Katsura Hoshino",
                        chapters = 529,
                        coverUrl = "https://m.media-amazon.com/images/I/71u2eA+XySL._AC_UF1000,1000_QL80_.jpg",
                        description = getString(R.string.demo_dgrayman)
                    ),
                    BookItem(
                        title = "Chainsaw Man",
                        author = "Tatsuki Fujimoto",
                        chapters = 203,
                        coverUrl = "https://m.media-amazon.com/images/I/81s8x021-CL._AC_UF1000,1000_QL80_.jpg",
                        description = getString(R.string.demo_chainsaw)
                    )
                )
                demo.forEach { FirebaseBookRepository.catalogReference().push().setValue(it) }
            }
        }
    }

    private fun showAddDialog() {
        val ctx = requireContext()

        val layout = android.widget.LinearLayout(ctx).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(48, 24, 48, 0)
        }

        val etTitle = EditText(ctx).apply { hint = getString(R.string.hint_titulo) }
        val etAuthor = EditText(ctx).apply { hint = getString(R.string.hint_autor) }
        val etCh = EditText(ctx).apply {
            hint = getString(R.string.hint_capitulos)
            inputType = InputType.TYPE_CLASS_NUMBER
        }
        val etCover = EditText(ctx).apply { hint = getString(R.string.hint_url_portada) }
        val etDescription = EditText(ctx).apply {
            hint = getString(R.string.hint_descripcion)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        }

        layout.addView(etTitle)
        layout.addView(etAuthor)
        layout.addView(etCh)
        layout.addView(etCover)
        layout.addView(etDescription)

        AlertDialog.Builder(ctx)
            .setTitle(R.string.titulo_dialogo_agregar)
            .setView(layout)
            .setPositiveButton(R.string.guardar) { d, _ ->
                val title = etTitle.text.toString().trim()
                if (title.isEmpty()) {
                    Toast.makeText(ctx, R.string.error_titulo_vacio, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val book = BookItem(
                    title = title,
                    author = etAuthor.text.toString().trim(),
                    chapters = etCh.text.toString().toIntOrNull() ?: 0,
                    coverUrl = etCover.text.toString().trim().ifEmpty { null },
                    description = etDescription.text.toString().trim()
                )
                FirebaseBookRepository.catalogReference().push().setValue(book)
                d.dismiss()
            }
            .setNegativeButton(R.string.cancelar, null)
            .show()
    }

    private fun handleError(error: DatabaseError) {
        Toast.makeText(requireContext(), error.message, Toast.LENGTH_SHORT).show()
    }
}
