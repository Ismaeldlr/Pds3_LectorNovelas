package com.example.lectornovelaselectronicos.Fragmentos

import android.app.AlertDialog
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.lectornovelaselectronicos.Fragmentos.Biblioteca_Items.BookAdapter
import com.example.lectornovelaselectronicos.Fragmentos.Biblioteca_Items.BookItem
import com.example.lectornovelaselectronicos.Fragmentos.Biblioteca_Items.SpacingDecoration
import com.example.lectornovelaselectronicos.R
import com.google.firebase.database.*

class Biblioteca : Fragment() {

    private lateinit var recycler: RecyclerView
    private lateinit var toolbar: androidx.appcompat.widget.Toolbar

    // Inicializar el adapter pasándole la acción a ejecutar al hacer clic
    private val adapter = BookAdapter { book ->
        Toast.makeText(requireContext(), "Has pulsado en: ${book.title}", Toast.LENGTH_SHORT).show()
    }

    private val dbRef: DatabaseReference by lazy {
        FirebaseDatabase.getInstance().getReference("books")
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_biblioteca, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        toolbar = view.findViewById(R.id.toolbar)
        toolbar.title = "Biblioteca"
        toolbar.inflateMenu(R.menu.menu_biblioteca)
        toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_add -> { showAddDialog(); true }
                else -> false
            }
        }

        recycler = view.findViewById(R.id.rvLibrary)
        recycler.layoutManager = GridLayoutManager(requireContext(), 2)
        recycler.adapter = adapter
        recycler.addItemDecoration(SpacingDecoration(resources.getDimensionPixelSize(R.dimen.grid_gap)))

        listenBooks()

        // Si la base está vacía, insertamos DUMMY una sola vez
        seedIfEmpty()
    }

    private fun listenBooks() {
        dbRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = mutableListOf<BookItem>()
                for (child in snapshot.children) {
                    val item = child.getValue(BookItem::class.java)
                    item?.id = child.key
                    if (item != null) list.add(item)
                }
                adapter.submit(list)
            }
            override fun onCancelled(error: DatabaseError) { /* puedes loggear */ }
        })
    }

    private fun seedIfEmpty() {
        dbRef.get().addOnSuccessListener { snap ->
            if (!snap.hasChildren()) {
                val demo = listOf(
                    BookItem(title = "Shaman King", chapters = 305, coverUrl = "https://m.media-amazon.com/images/I/816k5LqS9kL._AC_UF1000,1000_QL80_.jpg"),
                    BookItem(title = "Gachiakuta", chapters = 148, coverUrl = "https://m.media-amazon.com/images/I/81xZp3u2-YL._AC_UF1000,1000_QL80_.jpg"),
                    BookItem(title = "D.Gray-man", chapters = 529, coverUrl = "https://m.media-amazon.com/images/I/71u2eA+XySL._AC_UF1000,1000_QL80_.jpg"),
                    BookItem(title = "Chainsaw Man", chapters = 203, coverUrl = "https://m.media-amazon.com/images/I/81s8x021-CL._AC_UF1000,1000_QL80_.jpg")
                )
                demo.forEach { dbRef.push().setValue(it) }
            }
        }
    }

    private fun showAddDialog() {
        val ctx = requireContext()

        val layout = android.widget.LinearLayout(ctx).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(48, 24, 48, 0)
        }

        val etTitle = EditText(ctx).apply { hint = "Título" }
        val etCh = EditText(ctx).apply {
            hint = "Capítulos"
            inputType = InputType.TYPE_CLASS_NUMBER
        }
        val etCover = EditText(ctx).apply { hint = "URL portada (opcional)" }

        layout.addView(etTitle)
        layout.addView(etCh)
        layout.addView(etCover)

        AlertDialog.Builder(ctx)
            .setTitle("Agregar libro")
            .setView(layout)
            .setPositiveButton("Guardar") { d, _ ->
                val title = etTitle.text.toString().trim()
                val chapters = etCh.text.toString().toIntOrNull() ?: 0
                val cover = etCover.text.toString().trim().ifEmpty { null }
                if (title.isNotEmpty()) {
                    val book = BookItem(title = title, chapters = chapters, coverUrl = cover)
                    dbRef.push().setValue(book)
                }
                d.dismiss()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
}