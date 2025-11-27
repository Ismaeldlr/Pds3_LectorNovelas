package com.example.lectornovelaselectronicos.Fragmentos

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.lectornovelaselectronicos.Fragmentos.Historial_Items.HistoryAdapter
import com.example.lectornovelaselectronicos.Fragmentos.Historial_Items.HistoryItem
import com.example.lectornovelaselectronicos.R
import com.example.lectornovelaselectronicos.data.BookCache
import com.example.lectornovelaselectronicos.data.FirebaseBookRepository
import com.example.lectornovelaselectronicos.data.ReadingHistoryEntry
import com.example.lectornovelaselectronicos.ui.reader.ChapterContentActivity
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener

class Historial : Fragment() {

    private lateinit var recycler: RecyclerView
    private lateinit var emptyView: TextView
    private lateinit var toolbar: androidx.appcompat.widget.Toolbar
    private lateinit var statsContainer: View
    private lateinit var tvNovelas: TextView
    private lateinit var tvPalabras: TextView
    private lateinit var tvProgreso: TextView

    private val adapter = HistoryAdapter { item -> onHistoryClick(item) }

    private val catalogMap = linkedMapOf<String, com.example.lectornovelaselectronicos.Fragmentos.Biblioteca_Items.BookItem>()
    private var historyEntries: List<ReadingHistoryEntry> = emptyList()

    private var catalogListener: ValueEventListener? = null
    private var historyListener: ValueEventListener? = null
    private var historyUid: String? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_historial, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        toolbar = view.findViewById(R.id.toolbar)
        toolbar.title = getString(R.string.titulo_historial)

        recycler = view.findViewById(R.id.rvHistory)
        recycler.layoutManager = LinearLayoutManager(requireContext())
        recycler.adapter = adapter

        emptyView = view.findViewById(R.id.tvEmpty)
        statsContainer = view.findViewById(R.id.groupStats)
        tvNovelas = view.findViewById(R.id.tvNovelas)
        tvPalabras = view.findViewById(R.id.tvPalabras)
        tvProgreso = view.findViewById(R.id.tvProgreso)

        listenCatalog()
        listenUserHistory()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        catalogListener?.let { FirebaseBookRepository.catalogReference().removeEventListener(it) }
        historyListener?.let { listener ->
            historyUid?.let { uid ->
                FirebaseBookRepository.historyReferenceFor(uid).removeEventListener(listener)
            }
        }
        catalogListener = null
        historyListener = null
        historyUid = null
    }

    private fun listenCatalog() {
        catalogListener = FirebaseBookRepository.listenCatalog(
            onData = { list ->
                catalogMap.clear()
                list.forEach { book ->
                    val key = book.id
                    if (key != null) catalogMap[key] = book
                }
                updateHistory()
            },
            onError = { error -> handleError(error) },
        )
    }

    private fun listenUserHistory() {
        val uid = FirebaseBookRepository.currentUserId()
        if (uid == null) {
            historyUid = null
            historyEntries = emptyList()
            adapter.submit(emptyList())
            emptyView.visibility = View.VISIBLE
            emptyView.setText(R.string.historial_inicia_sesion)
            statsContainer.visibility = View.GONE
            return
        }

        historyUid = uid
        historyListener = FirebaseBookRepository.listenUserHistory(
            uid,
            onData = { entries ->
                historyEntries = entries.sortedByDescending { it.lastReadAt }
                updateHistory()
            },
            onError = { error -> handleError(error) },
        )
    }

    private fun updateHistory() {
        if (historyUid == null) return

        val items = historyEntries.map { entry ->
            val book = catalogMap[entry.bookId]
            HistoryItem(entry, book)
        }

        adapter.submit(items)
        emptyView.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        emptyView.setText(R.string.historial_vacio)

        updateStats(items)
    }

    private fun updateStats(items: List<HistoryItem>) {
        if (items.isEmpty()) {
            statsContainer.visibility = View.GONE
            return
        }

        statsContainer.visibility = View.VISIBLE
        val novelasLeidas = items.size
        val palabrasLeidas = items.sumOf { it.entry.wordsRead }
        val progresoPromedio = if (items.isNotEmpty()) {
            val totalPorcentajes = items.sumOf { it.progressPercent }
            totalPorcentajes / items.size
        } else 0

        tvNovelas.text = getString(R.string.historial_stat_novelas, novelasLeidas)
        tvPalabras.text = getString(R.string.historial_stat_palabras, palabrasLeidas)
        tvProgreso.text = getString(R.string.historial_stat_progreso, progresoPromedio)
    }

    private fun onHistoryClick(item: HistoryItem) {
        val book = item.book
        if (book == null) {
            Toast.makeText(requireContext(), R.string.historial_libro_no_disponible, Toast.LENGTH_SHORT).show()
            return
        }

        // Guardamos el libro en el cache en memoria
        BookCache.currentBook = book

        // lastChapterIndex es 1-based, lo convertimos a índice 0-based
        val chapterIndex = (item.entry.lastChapterIndex - 1).coerceAtLeast(0)

        // Solo pasamos el índice por Intent
        ChapterContentActivity.start(
            requireContext(),
            chapterIndex,
        )
    }


    private fun handleError(error: DatabaseError) {
        Toast.makeText(requireContext(), error.message, Toast.LENGTH_SHORT).show()
    }
}
