package com.example.lectornovelaselectronicos.Fragmentos.Biblioteca_Items

/**
 * Representa tanto los libros públicos del catálogo como los títulos que el usuario
 * guarda en su biblioteca personal. Todos los campos son opcionales para que Firebase
 * pueda deserializar objetos con atributos faltantes sin provocar fallos.
 */
data class BookItem(
    var id: String? = null,
    var title: String = "",
    var author: String = "",
    var description: String = "",
    var coverUrl: String? = null,
    var language: String = "",
    var status: String = "",
    var genres: List<String> = emptyList(),
    var tags: List<String> = emptyList(),
    var chapterCount: Int = 0,
    var chapters: Map<String, ChapterSummary>? = null,
)

data class ChapterSummary(
    var index: Int = 0,
    var title: String = "",
    var releaseDate: String? = null,
)

val BookItem.effectiveChapterCount: Int
    get() = when {
        chapterCount > 0 -> chapterCount
        chapters?.isNotEmpty() == true -> chapters!!.values.size
        else -> 0
    }

val BookItem.sortedChapters: List<ChapterSummary>
    get() = chapters?.values
        ?.sortedWith(compareBy<ChapterSummary> { it.index }.thenBy { it.title })
        ?: emptyList()
