package com.example.lectornovelaselectronicos.data

/**
 * Representa un registro de lectura sincronizado en Firebase para que el usuario
 * pueda reanudar su avance desde cualquier dispositivo.
 */
data class ReadingHistoryEntry(
    var bookId: String = "",
    var bookTitle: String = "",
    var bookAuthor: String = "",
    var coverUrl: String? = null,
    var lastChapterIndex: Int = 0,
    var lastChapterTitle: String = "",
    var chapterCount: Int = 0,
    var wordsRead: Int = 0,
    var lastReadAt: Long = 0L,
)
