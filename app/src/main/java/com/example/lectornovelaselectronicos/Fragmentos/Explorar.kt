package com.example.lectornovelaselectronicos.Fragmentos

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import com.example.lectornovelaselectronicos.Fragmentos.Biblioteca_Items.BookItem
import com.example.lectornovelaselectronicos.Fragmentos.Biblioteca_Items.SpacingDecoration
import com.example.lectornovelaselectronicos.R
import com.example.lectornovelaselectronicos.data.FirebaseBookRepository
import com.example.lectornovelaselectronicos.databinding.FragmentExplorarBinding
import com.example.lectornovelaselectronicos.ui.explorar.CatalogAdapter
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class Explorar : Fragment() {

    private var _binding: FragmentExplorarBinding? = null
    private val binding get() = _binding!!

    private val adapter = CatalogAdapter { book -> showBookDetails(book) }

    private var catalogListener: ValueEventListener? = null
    private var libraryListener: ValueEventListener? = null
    private var libraryUid: String? = null
    private var libraryIds: Set<String> = emptySet()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentExplorarBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.toolbarExplorar.title = getString(R.string.title_explorar)
        binding.toolbarExplorar.inflateMenu(R.menu.menu_explorar)
        binding.toolbarExplorar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_open_map -> {
                    parentFragmentManager.beginTransaction()
                        .replace(R.id.FragmentL1, MapaBibliotecasFragment.newInstance())
                        .addToBackStack(null)
                        .commit()
                    true
                }
                else -> false
            }
        }

        val spanCount = if (resources.configuration.screenWidthDp >= 600) 4 else 3
        binding.rvCatalogo.layoutManager = GridLayoutManager(requireContext(), spanCount)
        binding.rvCatalogo.adapter = adapter
        binding.rvCatalogo.addItemDecoration(SpacingDecoration(resources.getDimensionPixelSize(R.dimen.grid_gap)))

        binding.progressCatalogo.isVisible = true
        listenCatalog()
        listenUserLibrary()
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
        _binding = null
    }

    private fun listenCatalog() {
        catalogListener = FirebaseBookRepository.listenCatalog(
            onData = { list ->
                binding.progressCatalogo.isVisible = false
                adapter.submit(list)
                binding.tvEmptyCatalogo.text = getString(R.string.catalogo_vacio)
                binding.tvEmptyCatalogo.isVisible = list.isEmpty()
            },
            onError = { error -> handleError(error) },
        )
    }

    private fun listenUserLibrary() {
        val uid = FirebaseBookRepository.currentUserId()
        if (uid == null) {
            libraryUid = null
            libraryIds = emptySet()
            return
        }
        libraryUid = uid
        libraryListener = FirebaseBookRepository.listenUserLibrary(
            uid,
            onData = { ids -> libraryIds = ids },
            onError = { error -> handleError(error) },
        )
    }

    private fun showBookDetails(book: BookItem) {
        val uid = FirebaseBookRepository.currentUserId()
        var message = buildBookMessage(book)
        if (uid == null) {
            message = buildString {
                append(message)
                if (message.isNotEmpty()) append("\n\n")
                append(getString(R.string.inicia_sesion_para_guardar))
            }
        }

        val builder = AlertDialog.Builder(requireContext())
            .setTitle(book.title)
            .setMessage(message)

        val bookId = book.id
        if (bookId != null && uid != null) {
            val enBiblioteca = libraryIds.contains(bookId)
            val actionText = if (enBiblioteca) R.string.quitar_biblioteca else R.string.agregar_biblioteca
            builder.setPositiveButton(actionText) { dialog, _ ->
                if (enBiblioteca) {
                    FirebaseBookRepository.removeFromUserLibrary(bookId)
                } else {
                    FirebaseBookRepository.addToUserLibrary(bookId)
                }
                dialog.dismiss()
            }
            builder.setNegativeButton(R.string.cancelar, null)
        } else {
            builder.setPositiveButton(R.string.cerrar, null)
        }

        builder.show()
    }

    private fun buildBookMessage(book: BookItem): String {
        return buildString {
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
    }

    private fun handleError(error: DatabaseError) {
        binding.progressCatalogo.isVisible = false
        binding.tvEmptyCatalogo.isVisible = true
        binding.tvEmptyCatalogo.text = getString(R.string.error_cargar_catalogo, error.message)
    }

    companion object {
        fun newInstance() = Explorar()
    }
}
