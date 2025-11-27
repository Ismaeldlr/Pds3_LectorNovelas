package com.example.lectornovelaselectronicos.ui.detail

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.example.lectornovelaselectronicos.Fragmentos.Biblioteca_Items.BookItem
import com.example.lectornovelaselectronicos.Fragmentos.Biblioteca_Items.effectiveChapterCount
import com.example.lectornovelaselectronicos.Fragmentos.Biblioteca_Items.sortedChapters
import com.example.lectornovelaselectronicos.R
import com.example.lectornovelaselectronicos.data.BookCache
import com.example.lectornovelaselectronicos.data.FirebaseBookRepository
import com.example.lectornovelaselectronicos.databinding.ActivityBookDetailBinding
import com.example.lectornovelaselectronicos.ui.reader.ChapterContentActivity
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.ValueEventListener
import com.google.gson.Gson
import java.util.Locale

class BookDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBookDetailBinding
    private lateinit var chapterAdapter: ChapterBlockAdapter
    private var libraryListener: ValueEventListener? = null
    private var libraryRef: DatabaseReference? = null
    private var isInLibrary: Boolean = false
    private var selectedCoverBytes: ByteArray? = null

    private val pickCoverLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { handleCoverSelected(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBookDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        binding.rvChapters.layoutManager = LinearLayoutManager(this)

        val book = BookCache.currentBook
        if (book == null) {
            // Por si acaso el proceso fue matado y se perdió el cache
            finish()
            return
        }

        setupChapterAdapter(book)
        renderBook(book)
        observeLibraryState(book)
        setupTabs()
        setupEditForm(book)
    }

    override fun onDestroy() {
        super.onDestroy()
        libraryListener?.let { listener ->
            libraryRef?.removeEventListener(listener)
        }
    }

    private fun setupTabs() {
        binding.tabAbout.setOnClickListener { selectTab(DetailTab.ABOUT) }
        binding.tabToc.setOnClickListener { selectTab(DetailTab.TOC) }
        binding.tabEdit.setOnClickListener { selectTab(DetailTab.EDIT) }
        selectTab(DetailTab.ABOUT)
    }

    private fun setupChapterAdapter(book: BookItem) {
        chapterAdapter = ChapterBlockAdapter { _, position ->
            BookCache.currentBook = book
            ChapterContentActivity.start(this, position)
        }
        binding.rvChapters.adapter = chapterAdapter
    }

    private fun renderBook(book: BookItem) {
        binding.toolbar.title = ""
        binding.tvTitle.text = book.title

        if (book.author.isNotBlank()) {
            binding.tvAuthor.text = getString(R.string.book_detail_author, book.author)
            binding.tvAuthor.visibility = View.VISIBLE
        } else {
            binding.tvAuthor.visibility = View.GONE
        }

        binding.chipStatus.apply {
            isVisible = book.status.isNotBlank()
            if (isVisible) text = book.status.uppercase(Locale.getDefault())
        }

        binding.chipLanguage.apply {
            isVisible = book.language.isNotBlank()
            if (isVisible) text = book.language.uppercase(Locale.getDefault())
        }

        val chapterCount = book.effectiveChapterCount
        binding.chipChapterCount.apply {
            isVisible = chapterCount > 0
            if (isVisible) text = resources.getQuantityString(R.plurals.book_detail_chapter_count, chapterCount, chapterCount)
        }

        binding.tvDescription.text = book.description.ifBlank { getString(R.string.book_detail_no_description) }

        populateChips(binding.chipGenres, binding.labelGenres, book.genres)
        populateChips(binding.chipTags, binding.labelTags, book.tags)

        Glide.with(this)
            .load(book.coverUrl)
            .placeholder(R.drawable.placeholder_cover)
            .into(binding.imgCover)

        val chapters = book.sortedChapters
        chapterAdapter.submitChapters(chapters)
        binding.rvChapters.isVisible = chapters.isNotEmpty()
        binding.tvEmptyChapters.isVisible = chapters.isEmpty()
    }

    private fun populateChips(group: ChipGroup, label: View, values: List<String>?) {
        group.removeAllViews()
        val items = values?.filter { it.isNotBlank() } ?: emptyList()
        val hasItems = items.isNotEmpty()
        label.isVisible = hasItems
        group.isVisible = hasItems

        if (hasItems) {
            items.forEach { text ->
                val chip = Chip(
                    // Use a ContextThemeWrapper to apply the style programmatically
                    android.view.ContextThemeWrapper(this, com.google.android.material.R.style.Widget_Material3_Chip_Assist)
                ).apply {
                    this.text = text
                }
                group.addView(chip)
            }
        }
    }

    private fun setupEditForm(book: BookItem) {
        binding.inputTitle.setText(book.title)
        binding.inputAuthor.setText(book.author)
        binding.inputDescription.setText(book.description)
        binding.inputLanguage.setText(book.language)
        binding.inputStatus.setText(book.status)
        binding.inputGenres.setText(book.genres.joinToString(", "))
        binding.inputTags.setText(book.tags.joinToString(", "))

        Glide.with(this)
            .load(book.coverUrl)
            .placeholder(R.drawable.placeholder_cover)
            .into(binding.imgEditCover)

        binding.btnPickCover.setOnClickListener {
            pickCoverLauncher.launch("image/*")
        }

        binding.btnSaveEdits.setOnClickListener {
            val updatedBook = book.copy(
                title = binding.inputTitle.text?.toString()?.trim().orEmpty(),
                author = binding.inputAuthor.text?.toString()?.trim().orEmpty(),
                description = binding.inputDescription.text?.toString()?.trim().orEmpty(),
                language = binding.inputLanguage.text?.toString()?.trim().orEmpty(),
                status = binding.inputStatus.text?.toString()?.trim().orEmpty(),
                genres = binding.inputGenres.text?.toString()?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }
                    ?: emptyList(),
                tags = binding.inputTags.text?.toString()?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }
                    ?: emptyList(),
            )

            if (updatedBook.title.isBlank()) {
                Toast.makeText(this, R.string.error_titulo_vacio, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.book_detail_confirm_edit)
                .setMessage(R.string.book_detail_confirm_edit_message)
                .setPositiveButton(R.string.guardar) { _, _ ->
                    saveBookEdits(updatedBook)
                }
                .setNegativeButton(R.string.cancelar, null)
                .show()
        }
    }

    private fun saveBookEdits(updatedBook: BookItem) {
        FirebaseBookRepository.updateBookWithOptionalCover(updatedBook, selectedCoverBytes) { success, savedBook ->
            if (success && savedBook != null) {
                BookCache.currentBook = savedBook
                renderBook(savedBook)
                Glide.with(this)
                    .load(savedBook.coverUrl)
                    .placeholder(R.drawable.placeholder_cover)
                    .into(binding.imgEditCover)
                selectedCoverBytes = null
                Toast.makeText(this, R.string.book_detail_edit_success, Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, R.string.book_detail_edit_error, Toast.LENGTH_SHORT).show()
            }
        }
    }


    private fun handleCoverSelected(uri: Uri) {
        val bytes = contentResolver.openInputStream(uri)?.use { input -> input.readBytes() }
        if (bytes == null) {
            Toast.makeText(this, R.string.book_detail_pick_cover_error, Toast.LENGTH_SHORT).show()
            return
        }
        selectedCoverBytes = bytes
        Glide.with(this)
            .load(bytes)
            .placeholder(R.drawable.placeholder_cover)
            .into(binding.imgEditCover)
    }

    private fun selectTab(tab: DetailTab) {
        binding.boxAbout.isVisible = tab == DetailTab.ABOUT
        binding.boxToc.isVisible = tab == DetailTab.TOC
        binding.boxEdit.isVisible = tab == DetailTab.EDIT

        val selectedColor = getColorFromAttr(com.google.android.material.R.attr.colorOnPrimary)
        val unselectedColor = getColorFromAttr(com.google.android.material.R.attr.colorOnSurface)

        when (tab) {
            DetailTab.ABOUT -> {
                binding.tabAbout.setBackgroundResource(R.drawable.bg_segment_left_selected)
                binding.tabToc.setBackgroundResource(R.drawable.bg_segment_middle_unselected)
                binding.tabEdit.setBackgroundResource(R.drawable.bg_segment_right_unselected)
            }

            DetailTab.TOC -> {
                binding.tabAbout.setBackgroundResource(R.drawable.bg_segment_left_unselected)
                binding.tabToc.setBackgroundResource(R.drawable.bg_segment_middle_selected)
                binding.tabEdit.setBackgroundResource(R.drawable.bg_segment_right_unselected)
            }

            DetailTab.EDIT -> {
                binding.tabAbout.setBackgroundResource(R.drawable.bg_segment_left_unselected)
                binding.tabToc.setBackgroundResource(R.drawable.bg_segment_middle_unselected)
                binding.tabEdit.setBackgroundResource(R.drawable.bg_segment_right_selected)
            }
        }

        binding.tabAbout.setTextColor(if (tab == DetailTab.ABOUT) selectedColor else unselectedColor)
        binding.tabToc.setTextColor(if (tab == DetailTab.TOC) selectedColor else unselectedColor)
        binding.tabEdit.setTextColor(if (tab == DetailTab.EDIT) selectedColor else unselectedColor)
    }

    private fun observeLibraryState(book: BookItem) {
        val bookId = book.id
        val uid = FirebaseBookRepository.currentUserId()

        if (bookId.isNullOrEmpty()) {
            binding.btnLibrary.isVisible = false
            return
        }

        if (uid.isNullOrEmpty()) {
            binding.btnLibrary.isVisible = true
            binding.btnLibrary.text = getString(R.string.book_detail_login_to_save)
            binding.btnLibrary.setOnClickListener {
                Toast.makeText(this, R.string.inicia_sesion_para_guardar, Toast.LENGTH_LONG).show()
            }
            return
        }

        binding.btnLibrary.isVisible = true
        libraryRef = FirebaseBookRepository.userLibraryReferenceFor(uid).child(bookId)
        libraryListener = libraryRef?.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                isInLibrary = snapshot.exists()
                updateLibraryButton()
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@BookDetailActivity, error.message, Toast.LENGTH_SHORT).show()
            }
        })

        binding.btnLibrary.setOnClickListener {
            if (isInLibrary) {
                FirebaseBookRepository.removeFromUserLibrary(bookId)
            } else {
                FirebaseBookRepository.addToUserLibrary(bookId)
            }
        }
    }

    private fun updateLibraryButton() {
        val textRes = if (isInLibrary) R.string.quitar_biblioteca else R.string.agregar_biblioteca
        binding.btnLibrary.text = getString(textRes)
    }

    private fun Context.getColorFromAttr(attr: Int): Int {
        val typedValue = android.util.TypedValue()
        theme.resolveAttribute(attr, typedValue, true)
        return typedValue.data
    }

    private enum class DetailTab { ABOUT, TOC, EDIT }

    companion object {
        private const val EXTRA_BOOK_JSON = "extra_book_json"
        private val gson = Gson()

        fun start(context: Context) {
            val intent = Intent(context, BookDetailActivity::class.java)
            context.startActivity(intent)
        }
    }
}
