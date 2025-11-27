package com.example.lectornovelaselectronicos.data

import android.content.ContentResolver
import android.net.Uri
import androidx.core.text.HtmlCompat
import com.example.lectornovelaselectronicos.Fragmentos.Biblioteca_Items.BookItem
import com.example.lectornovelaselectronicos.Fragmentos.Biblioteca_Items.ChapterSummary
import nl.siegmann.epublib.domain.Book
import nl.siegmann.epublib.domain.Resource
import nl.siegmann.epublib.domain.TOCReference
import nl.siegmann.epublib.epub.EpubReader
import java.nio.charset.Charset
import java.util.Locale

data class ImportedBook(val book: BookItem, val coverBytes: ByteArray?)

class EpubImporter(private val contentResolver: ContentResolver) {

    /**
     * Lee un archivo EPUB y devuelve un [BookItem] listo para guardarse en Firebase.
     * Se intenta extraer toda la información posible para soportar variaciones del estándar EPUB.
     */
    fun parse(uri: Uri): ImportedBook? {
        return try {
            contentResolver.openInputStream(uri)?.use { stream ->
                val epubBook = EpubReader().readEpub(stream)
                val bookItem = buildBookItem(epubBook)
                val cover = extractCoverBytes(epubBook)
                ImportedBook(bookItem, cover)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun buildBookItem(epubBook: Book): BookItem {
        val metadata = epubBook.metadata
        val title = metadata.titles.firstOrNull()?.takeIf { it.isNotBlank() } ?: "EPUB sin título"
        val author = metadata.authors.firstOrNull()?.let { listOfNotNull(it.firstname, it.lastname).joinToString(" ").trim() }
            ?.takeIf { it.isNotBlank() } ?: "Autor desconocido"
        val description = metadata.descriptions.firstOrNull()?.toString() ?: ""
        val language = metadata.languages.firstOrNull()?.takeIf { it.isNotBlank() }
            ?: Locale.getDefault().language

        val chapterList = extractChapters(epubBook, language)
        val chaptersMap = chapterList.mapIndexed { index, chapter ->
            val adjusted = chapter.copy(index = index + 1)
            "ch${index + 1}" to adjusted
        }.toMap()

        val subjects = metadata.subjects.mapNotNull { it?.toString()?.trim() }.filter { it.isNotEmpty() }
        val keywords = subjects

        return BookItem(
            title = title,
            author = author,
            description = description,
            language = language,
            status = "imported",
            genres = subjects,
            tags = keywords,
            chapterCount = chapterList.size,
            chapters = if (chaptersMap.isNotEmpty()) chaptersMap else null,
        )
    }

    private fun extractChapters(epubBook: Book, language: String): List<ChapterSummary> {
        val tocTitles = flattenToc(epubBook.tableOfContents.tocReferences)
            .mapNotNull { ref ->
                val href = ref.completeHref
                val title = ref.title?.takeIf { it.isNotBlank() }
                href?.let { it to (title ?: "") }
            }
            .toMap()

        val spineItems = epubBook.spine.spineReferences
        if (spineItems.isEmpty()) return emptyList()

        return spineItems.mapIndexed { index, spineRef ->
            val resource = spineRef.resource
            val rawTitle = resource.title?.takeIf { it.isNotBlank() }
                ?: tocTitles[resource.href]?.takeIf { it.isNotBlank() }
                ?: "Capítulo ${index + 1}"
            val body = readResourceText(resource)
            val plainContent = HtmlCompat.fromHtml(body, HtmlCompat.FROM_HTML_MODE_COMPACT).toString().trim()
            ChapterSummary(
                index = index + 1,
                title = rawTitle,
                variant = "EPUB",
                language = language,
                content = plainContent,
            )
        }
    }

    private fun flattenToc(items: List<TOCReference>): List<TOCReference> {
        val result = mutableListOf<TOCReference>()
        items.forEach { ref ->
            result += ref
            if (ref.children.isNotEmpty()) {
                result += flattenToc(ref.children)
            }
        }
        return result
    }

    private val TOCReference.completeHref: String?
        get() = resource?.href ?: children.firstOrNull()?.completeHref

    private fun extractCoverBytes(book: Book): ByteArray? {
        val coverResource: Resource? = book.coverImage
            ?: book.resources.getByIdOrNull("cover")
            ?: book.resources.getByHrefOrNull("cover.jpg")
            ?: book.resources.getByHrefOrNull("cover.png")
        return coverResource?.data
    }

    private fun readResourceText(resource: Resource): String {
        val charset = resource.inputEncoding ?: "UTF-8"
        return resource.inputStream.bufferedReader(Charset.forName(charset)).use { reader ->
            reader.readText()
        }
    }

    private fun nl.siegmann.epublib.domain.Resources.getByIdOrNull(id: String): Resource? = try {
        getById(id)
    } catch (_: Exception) {
        null
    }

    private fun nl.siegmann.epublib.domain.Resources.getByHrefOrNull(href: String): Resource? = try {
        getByHref(href)
    } catch (_: Exception) {
        null
    }
}
