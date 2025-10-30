package com.example.lectornovelaselectronicos.data

import com.example.lectornovelaselectronicos.Fragmentos.Biblioteca_Items.BookItem
import com.example.lectornovelaselectronicos.Fragmentos.Biblioteca_Items.effectiveChapterCount
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

/**
 * Funciones utilitarias para acceder al catálogo público de libros y a la biblioteca
 * personal del usuario usando Firebase Realtime Database.
 */
object FirebaseBookRepository {

    private val database: FirebaseDatabase by lazy { FirebaseDatabase.getInstance() }
    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }

    private val booksRef: DatabaseReference by lazy { database.getReference("books") }
    private val userLibrariesRef: DatabaseReference by lazy { database.getReference("user_libraries") }

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
}
