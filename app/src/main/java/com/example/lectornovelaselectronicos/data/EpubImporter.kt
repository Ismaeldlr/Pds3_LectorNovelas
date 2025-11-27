package com.example.lectornovelaselectronicos.data

import android.content.ContentResolver
import android.net.Uri
import androidx.core.text.HtmlCompat
import com.example.lectornovelaselectronicos.Fragmentos.Biblioteca_Items.BookItem
import com.example.lectornovelaselectronicos.Fragmentos.Biblioteca_Items.ChapterSummary
import nl.siegmann.epublib.domain.Book
import nl.siegmann.epublib.domain.Metadata
import nl.siegmann.epublib.domain.Resource
import nl.siegmann.epublib.domain.TOCReference
import nl.siegmann.epublib.epub.EpubReader
import java.nio.charset.Charset
import java.time.LocalDate
import java.time.format.DateTimeFormatter
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
        val author = extractAuthor(metadata) ?: "Autor desconocido"
        val description = extractDescription(metadata)
        val language = metadata.languages.firstOrNull()?.takeIf { it.isNotBlank() }
            ?: Locale.getDefault().language

        val chapterList = extractChapters(epubBook, language, metadata)
        val chaptersMap = chapterList.mapIndexed { index, chapter ->
            val adjusted = chapter.copy(index = index + 1)
            "-Ch${index + 1}" to adjusted
        }.toMap()

        val subjects = metadata.subjects.mapNotNull { it?.toString()?.trim() }.filter { it.isNotEmpty() }
        val keywords = if (subjects.isNotEmpty()) subjects else listOf("EPUB")

        return BookItem(
            title = title,
            author = author,
            description = description,
            language = language,
            status = "imported",
            genres = subjects.ifEmpty { listOf("Imported") },
            tags = keywords,
            chapterCount = chapterList.size,
            chapters = if (chaptersMap.isNotEmpty()) chaptersMap else null,
        )
    }

    private fun extractChapters(epubBook: Book, language: String, metadata: Metadata): List<ChapterSummary> {
        val tocTitles = flattenToc(epubBook.tableOfContents.tocReferences)
            .mapNotNull { ref ->
                val href = ref.completeHref
                val title = ref.title?.takeIf { it.isNotBlank() }
                href?.let { it to (title ?: "") }
            }
            .toMap()

        val textResources = epubBook.spine.spineReferences
            .mapNotNull { it.resource }
            .filter { res -> res.mediaType?.name?.contains("html", ignoreCase = true) == true }
        if (textResources.isEmpty()) return emptyList()

        val releaseDates = buildReleaseDates(textResources.size, metadata)

        return textResources.mapIndexed { index, resource ->
            val rawTitle = resource.title?.takeIf { it.isNotBlank() }
                ?: tocTitles[resource.href]?.takeIf { it.isNotBlank() }
                ?: "Chapter ${index + 1}"
            val body = readResourceText(resource)
            val plainContent = HtmlCompat.fromHtml(body, HtmlCompat.FROM_HTML_MODE_COMPACT).toString().trim()
            ChapterSummary(
                index = index + 1,
                title = rawTitle,
                releaseDate = releaseDates.getOrNull(index),
                variant = "EPUB",
                language = language,
                content = plainContent,
            )
        }
    }

    private fun extractAuthor(metadata: Metadata): String? {
        return metadata.authors.firstOrNull()?.let { author ->
            listOfNotNull(author.firstname, author.lastname)
                .joinToString(" ")
                .trim()
                .takeIf { it.isNotBlank() }
        }
    }

    private fun extractDescription(metadata: Metadata): String {
        val description = metadata.descriptions.firstOrNull()?.toString()?.trim()
        if (!description.isNullOrBlank()) return description
        val subjects = metadata.subjects.mapNotNull { it?.toString()?.trim() }.filter { it.isNotEmpty() }
        return if (subjects.isNotEmpty()) subjects.joinToString(prefix = "Etiquetas: ") else ""
    }

    private fun buildReleaseDates(count: Int, metadata: Metadata): List<String> {
        val formatter = DateTimeFormatter.ISO_DATE
        val baseDate = metadata.dates.firstOrNull()?.value?.toInstant()?.atZone(java.time.ZoneId.systemDefault())?.toLocalDate()
            ?: LocalDate.now()
        return List(count) { index ->
            baseDate.minusDays((count - index).toLong()).format(formatter)
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
