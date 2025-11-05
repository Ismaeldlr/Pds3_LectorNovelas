package com.example.lectornovelaselectronicos.ui.detail

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.example.lectornovelaselectronicos.Fragmentos.Biblioteca_Items.BookItem
import com.example.lectornovelaselectronicos.Fragmentos.Biblioteca_Items.effectiveChapterCount
import com.example.lectornovelaselectronicos.Fragmentos.Biblioteca_Items.sortedChapters
import com.example.lectornovelaselectronicos.R
import com.example.lectornovelaselectronicos.data.FirebaseBookRepository
import com.example.lectornovelaselectronicos.databinding.ActivityBookDetailBinding
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.ValueEventListener
import com.google.gson.Gson
import java.util.Locale

class BookDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBookDetailBinding
    private lateinit var chapterAdapter: ChapterAdapter
    private var libraryListener: ValueEventListener? = null
    private var libraryRef: DatabaseReference? = null
    private var isInLibrary: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBookDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        chapterAdapter = ChapterAdapter()
        binding.rvChapters.apply {
            layoutManager = LinearLayoutManager(this@BookDetailActivity)
            adapter = chapterAdapter
        }

        val json = intent.getStringExtra(EXTRA_BOOK_JSON)
        val book = json?.let { runCatching { gson.fromJson(it, BookItem::class.java) }.getOrNull() }
        if (book == null) {
            finish()
            return
        }

        renderBook(book)
        observeLibraryState(book)
        setupTabs()
    }

    override fun onDestroy() {
        super.onDestroy()
        libraryListener?.let { listener ->
            libraryRef?.removeEventListener(listener)
        }
    }

    private fun setupTabs() {
        binding.tabAbout.setOnClickListener {
            binding.boxAbout.visibility = View.VISIBLE
            binding.boxToc.visibility = View.GONE
            binding.tabAbout.setBackgroundResource(R.drawable.bg_segment_left_selected)
            binding.tabToc.setBackgroundResource(R.drawable.bg_segment_right_unselected)
            (it as TextView).setTextColor(getColorFromAttr(com.google.android.material.R.attr.colorOnPrimary))
            binding.tabToc.setTextColor(getColorFromAttr(com.google.android.material.R.attr.colorOnSurface))
        }
        binding.tabToc.setOnClickListener {
            binding.boxAbout.visibility = View.GONE
            binding.boxToc.visibility = View.VISIBLE
            binding.tabAbout.setBackgroundResource(R.drawable.bg_segment_left_unselected)
            binding.tabToc.setBackgroundResource(R.drawable.bg_segment_right_selected)
            (it as TextView).setTextColor(getColorFromAttr(com.google.android.material.R.attr.colorOnPrimary))
            binding.tabAbout.setTextColor(getColorFromAttr(com.google.android.material.R.attr.colorOnSurface))
        }
        // Set initial state
        binding.tabAbout.performClick()
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
        chapterAdapter.submitList(chapters)
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


    companion object {
        private const val EXTRA_BOOK_JSON = "extra_book_json"
        private val gson = Gson()

        fun start(context: Context, book: BookItem) {
            val intent = Intent(context, BookDetailActivity::class.java).apply {
                putExtra(EXTRA_BOOK_JSON, gson.toJson(book))
            }
            context.startActivity(intent)
        }
    }
}
