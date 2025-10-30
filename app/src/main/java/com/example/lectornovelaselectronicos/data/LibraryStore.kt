package com.example.lectornovelaselectronicos.data

import android.content.Context
import org.json.JSONArray

object LibraryStore {
    private const val PREF = "library_store"
    private const val KEY_IDS = "book_ids"

    fun getMisIds(ctx: Context): MutableSet<String> {
        val json = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY_IDS, "[]") ?: "[]"
        val arr = JSONArray(json)
        val set = mutableSetOf<String>()
        for (i in 0 until arr.length()) set.add(arr.getString(i))
        return set
    }

    fun estaEnMiBiblioteca(ctx: Context, id: String) = getMisIds(ctx).contains(id)

    fun agregar(ctx: Context, id: String) {
        val set = getMisIds(ctx)
        if (set.add(id)) guardar(ctx, set)
    }

    fun quitar(ctx: Context, id: String) {
        val set = getMisIds(ctx)
        if (set.remove(id)) guardar(ctx, set)
    }

    private fun guardar(ctx: Context, ids: Set<String>) {
        val arr = JSONArray()
        ids.forEach { arr.put(it) }
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit().putString(KEY_IDS, arr.toString()).apply()
    }
}
