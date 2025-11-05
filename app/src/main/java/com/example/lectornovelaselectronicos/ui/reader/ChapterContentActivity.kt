package com.example.lectornovelaselectronicos.ui.reader

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.example.lectornovelaselectronicos.Fragmentos.Biblioteca_Items.BookItem
import com.example.lectornovelaselectronicos.Fragmentos.Biblioteca_Items.ChapterSummary
import com.example.lectornovelaselectronicos.Fragmentos.Biblioteca_Items.sortedChapters
import com.example.lectornovelaselectronicos.R
import com.example.lectornovelaselectronicos.databinding.ActivityChapterContentBinding
import com.google.gson.Gson

class ChapterContentActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChapterContentBinding
    private lateinit var book: BookItem
    private var chapters = emptyList<ChapterSummary>()
    private var currentPosition: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChapterContentBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        val bookJson = intent.getStringExtra(EXTRA_BOOK_JSON)
        val initialPosition = intent.getIntExtra(EXTRA_CHAPTER_POSITION, 0)

        val parsedBook = bookJson?.let { runCatching { gson.fromJson(it, BookItem::class.java) }.getOrNull() }
        if (parsedBook == null) {
            finish()
            return
        }

        book = parsedBook
        chapters = book.sortedChapters
        if (chapters.isEmpty()) {
            finish()
            return
        }

        binding.toolbar.title = book.title

        setupControls()

        val safePosition = initialPosition.coerceIn(chapters.indices)
        showChapter(safePosition)
    }

    private fun setupControls() {
        val prevButtons = listOf(binding.btnPrevTop, binding.btnPrevBottom)
        val nextButtons = listOf(binding.btnNextTop, binding.btnNextBottom)
        val listButtons = listOf(binding.btnListTop, binding.btnListBottom)

        prevButtons.forEach { button ->
            button.setOnClickListener { showChapter(currentPosition - 1) }
        }

        nextButtons.forEach { button ->
            button.setOnClickListener { showChapter(currentPosition + 1) }
        }

        listButtons.forEach { button ->
            button.setOnClickListener { finish() }
        }
    }

    private fun showChapter(position: Int) {
        val newPosition = position.coerceIn(chapters.indices)
        currentPosition = newPosition
        val chapter = chapters[newPosition]

        val headingText = if (chapter.index > 0) {
            getString(R.string.chapter_heading_number, chapter.index)
        } else {
            getString(R.string.chapter_heading_unknown)
        }
        binding.tvChapterHeading.text = headingText

        val displayTitle = chapter.title.ifBlank {
            getString(R.string.book_detail_chapter_placeholder, chapter.index)
        }
        binding.tvChapterTitle.text = displayTitle

        val metaParts = mutableListOf<String>()
        chapter.variant.takeIf { it.isNotBlank() }?.let {
            metaParts += getString(R.string.chapter_meta_variant, it)
        }

        val chapterLanguage = chapter.language.ifBlank { book.language }
        chapterLanguage.takeIf { it.isNotBlank() }?.let {
            metaParts += getString(R.string.chapter_meta_language, it)
        }

        chapter.releaseDate?.takeIf { it.isNotBlank() }?.let { metaParts += it }

        binding.tvChapterMeta.isVisible = metaParts.isNotEmpty()
        binding.tvChapterMeta.text = metaParts.joinToString(separator = " • ")

        val content = chapter.content.ifBlank { getString(R.string.chapter_content_unavailable) }
        binding.tvChapterContent.text = content

        updateButtonStates()
    }

    private fun updateButtonStates() {
        val hasPrev = currentPosition > 0
        val hasNext = currentPosition < chapters.lastIndex

        listOf(binding.btnPrevTop, binding.btnPrevBottom).forEach { it.isEnabled = hasPrev }
        listOf(binding.btnNextTop, binding.btnNextBottom).forEach { it.isEnabled = hasNext }
    }

    companion object {
        private const val EXTRA_BOOK_JSON = "extra_book_json"
        private const val EXTRA_CHAPTER_POSITION = "extra_chapter_position"
        private val gson = Gson()

        fun start(context: Context, book: BookItem, chapterPosition: Int) {
            val intent = Intent(context, ChapterContentActivity::class.java).apply {
                putExtra(EXTRA_BOOK_JSON, gson.toJson(book))
                putExtra(EXTRA_CHAPTER_POSITION, chapterPosition)
            }
            context.startActivity(intent)
        }
    }
}
