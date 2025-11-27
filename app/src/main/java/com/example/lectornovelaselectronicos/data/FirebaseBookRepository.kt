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
import com.google.firebase.storage.StorageReference

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
    private val coversRef: StorageReference by lazy { storage.reference.child("covers") }

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
     * Guarda un libro importado en el catálogo y opcionalmente sube su portada a Firebase Storage.
     */
    fun saveImportedBook(book: BookItem, coverBytes: ByteArray?, onComplete: (Boolean) -> Unit) {
        if (coverBytes != null) {
            uploadCover(coverBytes) { url ->
                url?.let { book.coverUrl = it }
                pushBook(book, onComplete)
            }
        } else {
            pushBook(book, onComplete)
        }
    }

    private fun pushBook(book: BookItem, onComplete: (Boolean) -> Unit) {
        val ref = booksRef.push()
        ref.setValue(book).addOnCompleteListener { task ->
            val success = task.isSuccessful
            if (success) {
                ref.key?.let { addToUserLibrary(it) }
            }
            onComplete(success)
        }
    }

    private fun uploadCover(bytes: ByteArray, onComplete: (String?) -> Unit) {
        val filename = "${System.currentTimeMillis()}_${bytes.hashCode()}.jpg"
        val target = coversRef.child(filename)
        target.putBytes(bytes)
            .continueWithTask { upload ->
                if (!upload.isSuccessful) {
                    throw upload.exception ?: java.lang.Exception("Upload failed")
                }
                target.downloadUrl
            }
            .addOnSuccessListener { uri -> onComplete(uri.toString()) }
            .addOnFailureListener { onComplete(null) }
    }
}
