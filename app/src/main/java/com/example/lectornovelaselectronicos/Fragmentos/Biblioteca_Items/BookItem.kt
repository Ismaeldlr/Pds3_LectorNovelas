package com.example.lectornovelaselectronicos.Fragmentos.Biblioteca_Items

data class BookItem(
    var id: String? = null,
    var title: String = "",
    var chapters: Int = 0,
    var coverUrl: String? = null
)