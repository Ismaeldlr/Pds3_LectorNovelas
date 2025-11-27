package com.example.lectornovelaselectronicos.data

import com.example.lectornovelaselectronicos.Fragmentos.Biblioteca_Items.BookItem
import com.example.lectornovelaselectronicos.Fragmentos.Biblioteca_Items.ChapterSummary
import com.example.lectornovelaselectronicos.Fragmentos.Biblioteca_Items.effectiveChapterCount
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.MutableData
import com.google.firebase.database.Transaction
import com.google.firebase.database.ValueEventListener
import com.google.firebase.storage.FirebaseStorage

/**
 * Funciones utilitarias para acceder al catálogo público de libros y a la biblioteca
 * personal del usuario usando Firebase Realtime Database.
 */
object FirebaseBookRepository {

    private val database: FirebaseDatabase by lazy { FirebaseDatabase.getInstance() }
    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val storage: FirebaseStorage by lazy { FirebaseStorage.getInstance() }

    private val booksRef: DatabaseReference by lazy { database.getReference("books") }
    private val userLibrariesRef: DatabaseReference by lazy { database.getReference("user_libraries") }
    private val historyRef: DatabaseReference by lazy { database.getReference("user_history") }

    // Carpeta raíz para portadas: "novelcovers/<bookId>/cover.img"
    private val novelCoversRef by lazy { storage.reference.child("novelCovers") }

    fun listenCatalog(
        onData: (List<BookItem>) -> Unit,
        onError: (DatabaseError) -> Unit,
    ): ValueEventListener {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = snapshot.children.mapNotNull { child ->
                    val item = child.getValue(BookItem::class.java)
                    item?.apply {
                        id = child.key
                        chapterCount = effectiveChapterCount
                    }
                }
                onData(list)
            }

            override fun onCancelled(error: DatabaseError) {
                onError(error)
            }
        }
        booksRef.addValueEventListener(listener)
        return listener
    }

    fun listenUserLibrary(
        uid: String,
        onData: (Set<String>) -> Unit,
        onError: (DatabaseError) -> Unit,
    ): ValueEventListener {
        val ref = userLibrariesRef.child(uid)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val ids = snapshot.children.mapNotNull { it.key }.toSet()
                onData(ids)
            }

            override fun onCancelled(error: DatabaseError) {
                onError(error)
            }
        }
        ref.addValueEventListener(listener)
        return listener
    }

    fun addToUserLibrary(bookId: String, onComplete: (Boolean) -> Unit = {}) {
        val uid = auth.currentUser?.uid ?: return onComplete(false)
        userLibrariesRef.child(uid).child(bookId).setValue(true)
            .addOnCompleteListener { onComplete(it.isSuccessful) }
    }

    fun removeFromUserLibrary(bookId: String, onComplete: (Boolean) -> Unit = {}) {
        val uid = auth.currentUser?.uid ?: return onComplete(false)
        userLibrariesRef.child(uid).child(bookId).removeValue()
            .addOnCompleteListener { onComplete(it.isSuccessful) }
    }

    fun currentUserId(): String? = auth.currentUser?.uid

    fun catalogReference(): DatabaseReference = booksRef

    fun userLibraryReferenceFor(uid: String): DatabaseReference = userLibrariesRef.child(uid)

    fun historyReferenceFor(uid: String): DatabaseReference = historyRef.child(uid)

    /**
     * Agrega un libro nuevo al catálogo. Si se proporcionan bytes de portada,
     * la sube a "dq novelcovers/<bookId>/cover.img" y guarda la URL en coverUrl.
     */
    fun addBookWithOptionalCover(
        book: BookItem,
        coverBytes: ByteArray? = null,
        onComplete: (Boolean) -> Unit = {},
    ) {
        val catalogRef = catalogReference()
        val newId = catalogRef.push().key
        if (newId == null) {
            onComplete(false)
            return
        }

        val bookWithId = book.copy(id = newId)

        // Sin portada: solo guardar el libro
        if (coverBytes == null) {
            catalogRef.child(newId).setValue(bookWithId)
                .addOnCompleteListener { onComplete(it.isSuccessful) }
            return
        }

        // Con portada: primero subir imagen, luego guardar libro con coverUrl
        uploadCoverForBook(newId, coverBytes) { success, url ->
            if (!success || url == null) {
                onComplete(false)
                return@uploadCoverForBook
            }
            val withCover = bookWithId.copy(coverUrl = url)
            catalogRef.child(newId).setValue(withCover)
                .addOnCompleteListener { onComplete(it.isSuccessful) }
        }
    }

    /**
     * Actualiza un libro existente. Si hay bytes de portada, los sube a
     * "dq novelcovers/<bookId>/cover.img" y actualiza coverUrl.
     */
    fun updateBookWithOptionalCover(
        book: BookItem,
        coverBytes: ByteArray? = null,
        onComplete: (Boolean, BookItem?) -> Unit = { _, _ -> },
    ) {
        val bookId = book.id
        if (bookId.isNullOrBlank()) {
            onComplete(false, null)
            return
        }

        val bookRef = booksRef.child(bookId)

        // Sin nueva portada: solo actualizar datos
        if (coverBytes == null) {
            bookRef.setValue(book)
                .addOnCompleteListener { onComplete(it.isSuccessful, book) }
            return
        }

        // Con nueva portada
        uploadCoverForBook(bookId, coverBytes) { success, url ->
            if (!success || url == null) {
                onComplete(false, null)
                return@uploadCoverForBook
            }
            val updatedBook = book.copy(coverUrl = url)
            bookRef.setValue(updatedBook)
                .addOnCompleteListener { onComplete(it.isSuccessful, updatedBook) }
        }
    }

    fun listenUserHistory(
        uid: String,
        onData: (List<ReadingHistoryEntry>) -> Unit,
        onError: (DatabaseError) -> Unit,
    ): ValueEventListener {
        val ref = historyRef.child(uid)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val entries = snapshot.children.mapNotNull { it.getValue(ReadingHistoryEntry::class.java) }
                onData(entries)
            }

            override fun onCancelled(error: DatabaseError) {
                onError(error)
            }
        }
        ref.addValueEventListener(listener)
        return listener
    }

    fun removeHistoryEntry(bookId: String, onComplete: (Boolean) -> Unit = {}) {
        val uid = auth.currentUser?.uid ?: return onComplete(false)
        historyRef.child(uid).child(bookId).removeValue()
            .addOnCompleteListener { onComplete(it.isSuccessful) }
    }

    fun recordReadingProgress(book: BookItem, chapter: ChapterSummary, wordsRead: Int = 0) {
        val uid = auth.currentUser?.uid ?: return
        val bookId = book.id ?: return
        val normalizedWords = wordsRead.coerceAtLeast(0)
        val path = historyRef.child(uid).child(bookId)
        path.runTransaction(object : Transaction.Handler {
            override fun doTransaction(currentData: MutableData): Transaction.Result {
                val current = currentData.getValue(ReadingHistoryEntry::class.java) ?: ReadingHistoryEntry()
                val newLastIndex = maxOf(current.lastChapterIndex, chapter.index)
                val shouldAccumulate = chapter.index > current.lastChapterIndex
                val updatedWords = if (shouldAccumulate) current.wordsRead + normalizedWords else current.wordsRead

                currentData.value = current.copy(
                    bookId = bookId,
                    bookTitle = book.title,
                    bookAuthor = book.author,
                    coverUrl = book.coverUrl,
                    lastChapterIndex = newLastIndex,
                    lastChapterTitle = chapter.title.ifBlank { chapter.index.toString() },
                    chapterCount = book.effectiveChapterCount,
                    wordsRead = updatedWords,
                    lastReadAt = System.currentTimeMillis(),
                )
                return Transaction.success(currentData)
            }

            override fun onComplete(error: DatabaseError?, committed: Boolean, currentData: DataSnapshot?) {
                // No-op
            }
        })
    }

    /**
     * Sube la portada a la ruta:
     *   dq novelcovers/<bookId>/cover.img
     * y devuelve la URL de descarga.
     */
    private fun uploadCoverForBook(
        bookId: String,
        coverBytes: ByteArray,
        onComplete: (Boolean, String?) -> Unit,
    ) {
        val ref = novelCoversRef.child("$bookId/cover.img")
        ref.putBytes(coverBytes)
            .continueWithTask { task ->
                if (!task.isSuccessful) {
                    throw task.exception ?: Exception("Upload failed")
                }
                ref.downloadUrl
            }
            .addOnSuccessListener { uri ->
                onComplete(true, uri.toString())
            }
            .addOnFailureListener {
                onComplete(false, null)
            }
    }
}
