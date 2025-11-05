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
import com.example.lectornovelaselectronicos.Fragmentos.Biblioteca_Items.ChapterSummary
import com.example.lectornovelaselectronicos.Fragmentos.Biblioteca_Items.SpacingDecoration
import com.example.lectornovelaselectronicos.R
import com.example.lectornovelaselectronicos.data.FirebaseBookRepository
import com.example.lectornovelaselectronicos.ui.detail.BookDetailActivity
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
        BookDetailActivity.start(requireContext(), book)
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
                        title = "The Clockwork Archivist",
                        author = "Lena Farrow",
                        description = getString(R.string.demo_clockwork_archivist),
                        coverUrl = "https://example.com/covers/clockwork-archivist.jpg",
                        language = "en",
                        status = "ongoing",
                        genres = listOf("Fantasy", "Mystery", "Steampunk"),
                        tags = listOf("found family", "sentient machines", "slow burn"),
                        chapterCount = 3,
                        chapters = mapOf(
                            "ch1" to ChapterSummary(
                                index = 1,
                                title = "Chapter 1: The Stalled Automatons",
                                releaseDate = "2024-05-01",
                                variant = "RAW",
                                language = "en",
                                content = """
                                    Lena recorrió los pasillos de la biblioteca mecánica mientras el olor a aceite rancio flotaba en el aire.
                                    Cada autómata detenido era un misterio anotado en su cuaderno, una promesa de despertar.
                                    Cuando descubrió la rendija de luz bajo la puerta del archivo prohibido, supo que aquella noche no dormiría.
                                """.trimIndent(),
                            ),
                            "ch2" to ChapterSummary(
                                index = 2,
                                title = "Chapter 2: Dust and Declarations",
                                releaseDate = "2024-05-08",
                                variant = "RAW",
                                language = "en",
                                content = """
                                    Las declaraciones oficiales ocultaban más engranajes rotos que soluciones.
                                    Lena desenterró un manifiesto olvidado y, con él, una alianza con los viejos custodios de vapor.
                                    La ciudad dormía ajena a que las máquinas planeaban su propia resurrección.
                                """.trimIndent(),
                            ),
                            "ch3" to ChapterSummary(
                                index = 3,
                                title = "Chapter 3: Gears in the Dark",
                                releaseDate = "2024-05-15",
                                variant = "RAW",
                                language = "en",
                                content = """
                                    Entre engranajes ocultos Lena encontró un corazón de cristal latiendo en silencio.
                                    Cada giro hacía vibrar los muros, como si la biblioteca recordara un antiguo juramento.
                                    La sombra que la observaba desde lo alto activó mecanismos que jamás debieron volver a moverse.
                                """.trimIndent(),
                            ),
                        ),
                    ),
                    BookItem(
                        title = "Azure Dynasty Online",
                        author = "Kai Tanaka",
                        description = getString(R.string.demo_azure_dynasty),
                        coverUrl = "https://example.com/covers/azure-dynasty.jpg",
                        language = "es",
                        status = "hiatus",
                        genres = listOf("Sci-Fi", "LitRPG"),
                        tags = listOf("virtual reality", "time skip", "guild politics"),
                        chapterCount = 5,
                        chapters = mapOf(
                            "ch1" to ChapterSummary(
                                index = 1,
                                title = "Chapter 1: Patch Notes of Ruin",
                                releaseDate = "2023-11-12",
                                variant = "WEB",
                                language = "es",
                                content = """
                                    El parche 5.0 prometía equilibrio, pero convirtió las llanuras iniciales en un campo de ruinas imposibles.
                                    Kai revisó las notas oficiales mientras la cuenta regresiva inundaba su visión.
                                    «Reagrupamos en el refugio antiguo», ordenó, sabiendo que sólo quedaban minutos antes del reinicio.
                                """.trimIndent(),
                            ),
                            "ch2" to ChapterSummary(
                                index = 2,
                                title = "Chapter 2: Reunion at Neo-Shinjuku",
                                releaseDate = "2023-11-19",
                                variant = "WEB",
                                language = "es",
                                content = """
                                    Las luces de Neo-Shinjuku parpadeaban con el reflejo de miles de jugadores que regresaban del exilio.
                                    Kai estrechó manos, calculó alianzas y recordó promesas hechas cinco años atrás.
                                    El gremio Fénix le entregó los planos de un servidor fantasma capaz de engañar al sistema.
                                """.trimIndent(),
                            ),
                            "ch3" to ChapterSummary(
                                index = 3,
                                title = "Chapter 3: Duel Under Neon Rain",
                                releaseDate = "2023-11-26",
                                variant = "WEB",
                                language = "es",
                                content = """
                                    La lluvia de neón caía pesada mientras Kai enfrentaba al duelista sintético designado por la administración.
                                    Cada golpe resonaba con ecos de código y chispas reales.
                                    Ganó apostando su propia barra de vida para romper el patrón predictivo del enemigo.
                                """.trimIndent(),
                            ),
                            "ch4" to ChapterSummary(
                                index = 4,
                                title = "Chapter 4: The Hidden Patch Vault",
                                releaseDate = "2023-12-03",
                                variant = "WEB",
                                language = "es",
                                content = """
                                    La bóveda estaba enterrada bajo capas de datos olvidados y custodios fractales.
                                    Con ayuda del gremio, Kai abrió el acceso y descubrió parches descartados por ser demasiado arriesgados.
                                    Encontró uno capaz de revertir el tiempo del servidor, pero a costa de borrar recuerdos.
                                """.trimIndent(),
                            ),
                            "ch5" to ChapterSummary(
                                index = 5,
                                title = "Chapter 5: System Rollback",
                                releaseDate = "2023-12-10",
                                variant = "WEB",
                                language = "es",
                                content = """
                                    Cuando activaron el rollback, el cielo digital se partió y mostró líneas de código desnudo.
                                    Los jugadores sintieron cómo sus logros se desvanecían, pero el mundo recuperó su estabilidad.
                                    Kai guardó el registro secreto del parche, decidido a usarlo sólo si el futuro volvía a derrumbarse.
                                """.trimIndent(),
                            ),
                        ),
                    ),
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
        val etChapterCount = EditText(ctx).apply {
            hint = getString(R.string.hint_chapter_count)
            inputType = InputType.TYPE_CLASS_NUMBER
        }
        val etCover = EditText(ctx).apply { hint = getString(R.string.hint_url_portada) }
        val etDescription = EditText(ctx).apply {
            hint = getString(R.string.hint_descripcion)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        }
        val etLanguage = EditText(ctx).apply { hint = getString(R.string.hint_idioma) }
        val etStatus = EditText(ctx).apply { hint = getString(R.string.hint_estado) }
        val etGenres = EditText(ctx).apply { hint = getString(R.string.hint_generos) }
        val etTags = EditText(ctx).apply { hint = getString(R.string.hint_etiquetas) }

        layout.addView(etTitle)
        layout.addView(etAuthor)
        layout.addView(etChapterCount)
        layout.addView(etCover)
        layout.addView(etDescription)
        layout.addView(etLanguage)
        layout.addView(etStatus)
        layout.addView(etGenres)
        layout.addView(etTags)

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
                    chapterCount = etChapterCount.text.toString().toIntOrNull() ?: 0,
                    coverUrl = etCover.text.toString().trim().ifEmpty { null },
                    description = etDescription.text.toString().trim(),
                    language = etLanguage.text.toString().trim(),
                    status = etStatus.text.toString().trim(),
                    genres = etGenres.text.toString().split(',').map { it.trim() }.filter { it.isNotEmpty() },
                    tags = etTags.text.toString().split(',').map { it.trim() }.filter { it.isNotEmpty() },
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
