package com.example.lectornovelaselectronicos.Fragmentos.Biblioteca_Items

import android.graphics.Rect
import android.view.View
import androidx.recyclerview.widget.RecyclerView

class SpacingDecoration(private val space: Int) : RecyclerView.ItemDecoration() {
    override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
        outRect.left = 10
        outRect.right = 10
        outRect.top = 5
        outRect.bottom = space
    }
}