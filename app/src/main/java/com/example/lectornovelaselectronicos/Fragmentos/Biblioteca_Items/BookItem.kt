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
    var chapters: Int = 0,
    var coverUrl: String? = null
)
