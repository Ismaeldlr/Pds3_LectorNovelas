package com.example.lectornovelaselectronicos.Fragmentos

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.lectornovelaselectronicos.R
import com.example.lectornovelaselectronicos.databinding.FragmentMapaExplorarBinding
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.gson.Gson
import okhttp3.OkHttpClient
import okhttp3.Request
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.ScaleBarOverlay
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import kotlin.math.*

class MapaBibliotecasFragment : Fragment() {

    private var _binding: FragmentMapaExplorarBinding? = null
    private val binding get() = _binding!!

    private val client = OkHttpClient()
    private var myLocationOverlay: MyLocationNewOverlay? = null

    private val pedirPermisos = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { p ->
        val ok = p[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                p[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (ok) obtenerUbicacion() else toast(getString(R.string.permiso_denegado))
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentMapaExplorarBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // User-Agent requerido por osmdroid/Overpass
        Configuration.getInstance().userAgentValue = requireContext().packageName

        // Config básica del mapa
        binding.osmMap.setTileSource(TileSourceFactory.MAPNIK)
        binding.osmMap.controller.setZoom(12.0)
        binding.osmMap.setMultiTouchControls(true)

        // Barra de escala (opcional)
        val scale = ScaleBarOverlay(binding.osmMap)
        binding.osmMap.overlays.add(scale)

        checkPermisos()
    }

    private fun checkPermisos() {
        val fine = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION)
        if (fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED) {
            obtenerUbicacion()
        } else {
            pedirPermisos.launch(arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ))
        }
    }

    @SuppressLint("MissingPermission")
    private fun obtenerUbicacion() {
        binding.progreso.visibility = View.VISIBLE
        val fused = LocationServices.getFusedLocationProviderClient(requireContext())

        fused.lastLocation.addOnSuccessListener { loc ->
            if (loc != null) {
                mostrarMiUbicacion(loc)
                buscarPOIsOverpass(loc)
            } else {
                fused.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null)
                    .addOnSuccessListener { l2 ->
                        if (l2 != null) {
                            mostrarMiUbicacion(l2)
                            buscarPOIsOverpass(l2)
                        } else {
                            binding.progreso.visibility = View.GONE
                            toast(getString(R.string.error_generico))
                        }
                    }.addOnFailureListener {
                        binding.progreso.visibility = View.GONE
                        toast(getString(R.string.error_generico))
                    }
            }
        }.addOnFailureListener {
            binding.progreso.visibility = View.GONE
            toast(getString(R.string.error_generico))
        }
    }

    @SuppressLint("MissingPermission")
    private fun mostrarMiUbicacion(loc: Location) {
        val p = GeoPoint(loc.latitude, loc.longitude)
        binding.osmMap.controller.setCenter(p)
        binding.osmMap.controller.setZoom(15.0)

        // Punto azul siguiendo ubicación
        myLocationOverlay?.disableMyLocation()
        myLocationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(requireContext()), binding.osmMap).also {
            it.enableMyLocation()
            binding.osmMap.overlays.add(it)
        }

        // Marcador "Mi ubicación"
        val m = Marker(binding.osmMap)
        m.position = p
        m.title = getString(R.string.mi_ubicacion)
        m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        binding.osmMap.overlays.add(m)
        binding.osmMap.invalidate()
    }

    /** Busca bibliotecas (amenity=library) y librerías (shop=books) con radios crecientes. */
    private fun buscarPOIsOverpass(loc: Location) {
        val radios = listOf(2000, 4000, 7000) // 2km, 4km, 7km

        fun queryFor(radius: Int): String =
            "https://overpass-api.de/api/interpreter?data=[out:json];(" +
                    "node(around:$radius,${loc.latitude},${loc.longitude})[amenity=library];" +
                    "way(around:$radius,${loc.latitude},${loc.longitude})[amenity=library];" +
                    "relation(around:$radius,${loc.latitude},${loc.longitude})[amenity=library];" +
                    "node(around:$radius,${loc.latitude},${loc.longitude})[shop=books];" +
                    "way(around:$radius,${loc.latitude},${loc.longitude})[shop=books];" +
                    "relation(around:$radius,${loc.latitude},${loc.longitude})[shop=books];" +
                    ");out center;"

        fun lanzar(index: Int) {
            if (index >= radios.size) {
                if (isAdded) activity?.runOnUiThread {
                    binding.progreso.visibility = View.GONE
                    toast(getString(R.string.sin_resultados))
                }
                return
            }

            val url = queryFor(radios[index])
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", requireContext().packageName) // Overpass requiere UA
                .build()

            client.newCall(req).enqueue(object : okhttp3.Callback {
                override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                    lanzar(index + 1) // intenta con el siguiente radio
                }

                override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                    val body = response.body?.string()
                    if (!response.isSuccessful || body == null) {
                        lanzar(index + 1); return
                    }

                    try {
                        val dto = Gson().fromJson(body, OverpassResp::class.java)

                        // Normaliza coordenadas (usa center para ways/relations)
                        val sitios = dto.elements.mapNotNull { e ->
                            val lat = e.lat ?: e.center?.lat
                            val lon = e.lon ?: e.center?.lon
                            if (lat == null || lon == null) return@mapNotNull null

                            val isBiblioteca = e.tags?.get("amenity") == "library"
                            val isLibreria  = e.tags?.get("shop") == "books"
                            if (!isBiblioteca && !isLibreria) return@mapNotNull null

                            val nombreBase = e.tags?.get("name:es")
                                ?: e.tags?.get("name")
                                ?: if (isBiblioteca) getString(R.string.tipo_biblioteca) else getString(R.string.tipo_libreria)

                            val tipoSufijo = if (isBiblioteca) getString(R.string.tipo_biblioteca) else getString(R.string.tipo_libreria)
                            val nombre = if (nombreBase.contains("(", true)) nombreBase else "$nombreBase $tipoSufijo"

                            val d = distanciaKm(loc.latitude, loc.longitude, lat, lon)
                            PoiOverpass(nombre, lat, lon, d)
                        }
                            .sortedBy { it.distKm }
                            .take(5)

                        if (sitios.isEmpty()) {
                            lanzar(index + 1) // aumenta radio
                        } else if (isAdded) {
                            activity?.runOnUiThread {
                                binding.progreso.visibility = View.GONE
                                // Limpia marcadores anteriores (conserva "Mi ubicación")
                                binding.osmMap.overlays.removeAll { it is Marker && it.title != getString(R.string.mi_ubicacion) }

                                // Agrega marcadores de resultados
                                sitios.forEach { s ->
                                    val mk = Marker(binding.osmMap)
                                    mk.position = GeoPoint(s.lat, s.lon)
                                    mk.title = s.nombre
                                    mk.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                                    binding.osmMap.overlays.add(mk)
                                }
                                binding.osmMap.invalidate()
                            }
                        }
                    } catch (_: Exception) {
                        lanzar(index + 1)
                    }
                }
            })
        }

        binding.progreso.visibility = View.VISIBLE
        lanzar(0)
    }

    private fun distanciaKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val R = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat/2).pow(2.0) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon/2).pow(2.0)
        val c = 2 * atan2(sqrt(a), sqrt(1-a))
        return R * c
    }

    override fun onResume() {
        super.onResume()
        binding.osmMap.onResume()
    }

    override fun onPause() {
        super.onPause()
        binding.osmMap.onPause()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        myLocationOverlay?.disableMyLocation()
        _binding = null
    }

    private fun toast(msg: String) =
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()

    // --------- DTO Overpass ----------
    data class OverpassResp(val elements: List<Element> = emptyList())
    data class Element(
        val type: String,
        val id: Long,
        val lat: Double?,            // null en way/relation
        val lon: Double?,            // null en way/relation
        val center: Center?,         // disponible por "out center"
        val tags: Map<String, String>?
    )
    data class Center(val lat: Double, val lon: Double)

    data class PoiOverpass(val nombre: String, val lat: Double, val lon: Double, val distKm: Double)

    companion object {
        @JvmStatic
        fun newInstance() = MapaBibliotecasFragment()
    }
}
