package com.example.lectornovelaselectronicos.Fragmentos

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.lectornovelaselectronicos.R
import com.example.lectornovelaselectronicos.databinding.FragmentExplorarBinding
import com.example.lectornovelaselectronicos.ui.explorar.BooksStoreFragment

class Explorar : Fragment() {

    private var _binding: FragmentExplorarBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentExplorarBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnBuscarBiblios.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.FragmentL1, MapaBibliotecasFragment.newInstance()) // Corrected container ID
                .addToBackStack(null)
                .commit()
        }

        binding.btnExplorarLibros.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.FragmentL1, BooksStoreFragment.newInstance())
                .addToBackStack(null)
                .commit()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
