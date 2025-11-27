package com.example.lectornovelaselectronicos.Fragmentos.Historial_Items

import com.example.lectornovelaselectronicos.Fragmentos.Biblioteca_Items.BookItem
import com.example.lectornovelaselectronicos.data.ReadingHistoryEntry

/**
 * Combina los datos del catálogo con el registro de historial
 * para mostrar información completa en la lista.
 */
data class HistoryItem(
    val entry: ReadingHistoryEntry,
    val book: BookItem?,
) {
    val progressPercent: Int
        get() = if (entry.chapterCount > 0) {
            (entry.lastChapterIndex * 100 / entry.chapterCount).coerceIn(0, 100)
        } else {
            0
        }
}
