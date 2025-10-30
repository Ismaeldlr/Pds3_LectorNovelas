package com.example.lectornovelaselectronicos.data

import com.example.lectornovelaselectronicos.models.Book

object BooksRepository {
    val catalogo: List<Book> = listOf(
        Book("b1", "La sombra del viento", "Carlos Ruiz Zafón",
            "Un misterio literario en la Barcelona de posguerra."),
        Book("b2", "El nombre del viento", "Patrick Rothfuss",
            "Crónica de la vida de Kvothe: magia, música y leyenda."),
        Book("b3", "Cien años de soledad", "Gabriel García Márquez",
            "La saga de los Buendía en Macondo."),
        Book("b4", "1984", "George Orwell",
            "Distopía sobre vigilancia y libertad."),
        Book("b5", "El Psicoanalista", "John Katzenbach",
            "Thriller psicológico con una cuenta regresiva mortal.")
    )
    fun getById(id: String) = catalogo.find { it.id == id }
}
