package com.example.lectornovelaselectronicos.Fragmentos

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
import com.example.lectornovelaselectronicos.data.BookCache
import com.example.lectornovelaselectronicos.data.FirebaseBookRepository
import com.example.lectornovelaselectronicos.databinding.FragmentExplorarBinding
import com.example.lectornovelaselectronicos.ui.detail.BookDetailActivity
import com.example.lectornovelaselectronicos.ui.explorar.CatalogAdapter
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener

class Explorar : Fragment() {

    private var _binding: FragmentExplorarBinding? = null
    private val binding get() = _binding!!

    private val adapter = CatalogAdapter { book -> showBookDetails(book) }

    private var catalogListener: ValueEventListener? = null

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
    }

    override fun onDestroyView() {
        super.onDestroyView()
        catalogListener?.let { FirebaseBookRepository.catalogReference().removeEventListener(it) }
        catalogListener = null
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

    private fun showBookDetails(book: BookItem) {
        BookCache.currentBook = book
        BookDetailActivity.start(requireContext())
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
