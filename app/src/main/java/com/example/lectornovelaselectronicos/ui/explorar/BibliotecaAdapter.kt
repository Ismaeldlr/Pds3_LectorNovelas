package com.example.lectornovelaselectronicos.ui.explorar

import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.lectornovelaselectronicos.R
import com.example.lectornovelaselectronicos.databinding.ItemBibliotecaBinding

data class BibliotecaUi(
    val nombre: String,
    val direccion: String,
    val lat: Double,
    val lng: Double,
    val distanciaKm: Double
)

class BibliotecaAdapter(
    private val items: MutableList<BibliotecaUi> = mutableListOf()
) : RecyclerView.Adapter<BibliotecaAdapter.VH>() {

    fun submit(list: List<BibliotecaUi>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    inner class VH(val b: ItemBibliotecaBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemBibliotecaBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return VH(b)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        val ctx = holder.b.root.context

        holder.b.tvNombre.text = item.nombre
        holder.b.tvDireccion.text = item.direccion
        holder.b.tvDistancia.text = ctx.getString(R.string.distancia_km, item.distanciaKm)

        holder.b.btnAbrir.setOnClickListener {
            val uri = Uri.parse("geo:${item.lat},${item.lng}?q=${item.lat},${item.lng}(${Uri.encode(item.nombre)})")
            ctx.startActivity(Intent(Intent.ACTION_VIEW, uri))
        }
    }

    override fun getItemCount(): Int = items.size
}
