package com.example.lectornovelaselectronicos.ui.detail

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
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

        setSupportActionBar(binding.toolbarDetail)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbarDetail.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        chapterAdapter = ChapterAdapter()
        binding.rvChapters.apply {
            layoutManager = LinearLayoutManager(this@BookDetailActivity)
            adapter = chapterAdapter
        }

        val json = intent.getStringExtra(EXTRA_BOOK_JSON)
        if (json.isNullOrEmpty()) {
            finish()
            return
        }

        val parsedBook = runCatching { gson.fromJson(json, BookItem::class.java) }.getOrNull()
        if (parsedBook == null) {
            finish()
            return
        }

        renderBook(parsedBook)
        observeLibraryState(parsedBook)
    }

    override fun onDestroy() {
        super.onDestroy()
        libraryListener?.let { listener ->
            libraryRef?.removeEventListener(listener)
        }
        libraryListener = null
        libraryRef = null
    }

    private fun renderBook(book: BookItem) {
        binding.toolbarDetail.title = book.title
        binding.tvTitle.text = book.title

        if (book.author.isNotBlank()) {
            binding.tvAuthor.text = getString(R.string.book_detail_author, book.author)
            binding.tvAuthor.visibility = View.VISIBLE
        } else {
            binding.tvAuthor.visibility = View.GONE
        }

        val chipColor = ContextCompat.getColor(this, R.color.md_light_onSurface)
        val chipBackground = R.color.md_light_surfaceVariant

        val status = book.status.trim()
        binding.chipStatus.apply {
            isVisible = status.isNotEmpty()
            if (status.isNotEmpty()) {
                text = status.uppercase(Locale.getDefault())
                setChipBackgroundColorResource(chipBackground)
                setTextColor(chipColor)
                setEnsureMinTouchTargetSize(false)
            }
        }

        val language = book.language.trim()
        binding.chipLanguage.apply {
            isVisible = language.isNotEmpty()
            if (language.isNotEmpty()) {
                text = language.uppercase(Locale.getDefault())
                setChipBackgroundColorResource(chipBackground)
                setTextColor(chipColor)
                setEnsureMinTouchTargetSize(false)
            }
        }

        val chapterCount = book.effectiveChapterCount
        binding.chipChapterCount.apply {
            isVisible = chapterCount > 0
            if (chapterCount > 0) {
                text = resources.getQuantityString(
                    R.plurals.book_detail_chapter_count,
                    chapterCount,
                    chapterCount,
                )
                setChipBackgroundColorResource(chipBackground)
                setTextColor(chipColor)
                setEnsureMinTouchTargetSize(false)
            }
        }

        binding.groupQuickInfo.isVisible =
            binding.chipStatus.isVisible || binding.chipLanguage.isVisible || binding.chipChapterCount.isVisible

        val description = book.description.ifBlank { getString(R.string.book_detail_no_description) }
        binding.tvDescription.text = description

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
        val isVisible = items.isNotEmpty()
        label.isVisible = isVisible
        group.isVisible = isVisible
        if (!isVisible) return

        val textColor = ContextCompat.getColor(this, R.color.md_light_onSurface)

        items.forEach { text ->
            val chip = Chip(this).apply {
                this.text = text
                isCheckable = false
                isClickable = false
                setChipBackgroundColorResource(R.color.md_light_surfaceVariant)
                setTextColor(textColor)
                setEnsureMinTouchTargetSize(false)
            }
            group.addView(chip)
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
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                isInLibrary = snapshot.exists()
                updateLibraryButton()
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@BookDetailActivity, error.message, Toast.LENGTH_SHORT).show()
            }
        }
        libraryListener = listener
        libraryRef?.addValueEventListener(listener)

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
