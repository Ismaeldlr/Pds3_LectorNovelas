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
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.gson.Gson
import okhttp3.*
import java.io.IOException
import kotlin.math.*

// Arriba del archivo
private val DEFAULT_LATLNG = LatLng(19.4326, -99.1332)  // CDMX
private const val DEFAULT_ZOOM = 12f

class MapaBibliotecasFragment : Fragment(), OnMapReadyCallback {

    private var _binding: FragmentMapaExplorarBinding? = null
    private val binding get() = _binding!!

    private var gMap: GoogleMap? = null
    private val client = OkHttpClient()
    private var currentApiCall: Call? = null



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
        (childFragmentManager.findFragmentById(R.id.map) as? SupportMapFragment)
            ?.getMapAsync(this)
    }

    override fun onMapReady(map: GoogleMap) {
        gMap = map
        gMap?.uiSettings?.isZoomControlsEnabled = true

        // Fallback inmediato: muestra algo siempre
        gMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(DEFAULT_LATLNG, DEFAULT_ZOOM))
        gMap?.addMarker(MarkerOptions().position(DEFAULT_LATLNG).title(getString(R.string.mi_ubicacion)))
        binding.progreso.visibility = View.GONE

        // Luego intenta la ubicación real
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

    private var ubicacionObtenida = false

    @SuppressLint("MissingPermission")
    private fun obtenerUbicacion() {
        binding.progreso.visibility = View.VISIBLE
        ubicacionObtenida = false

        // Timeout de 6s: si no llegó ubicación, ocultamos progreso
        binding.root.postDelayed({
            if (!ubicacionObtenida && isAdded) {
                binding.progreso.visibility = View.GONE
                toast(getString(R.string.error_generico))
            }
        }, 6000)

        val fused = LocationServices.getFusedLocationProviderClient(requireContext())
        fused.lastLocation.addOnSuccessListener { loc ->
            if (loc != null) {
                ubicacionObtenida = true
                binding.progreso.visibility = View.GONE
                actualizarMapaConMiUbicacion(loc)
                buscarBibliotecasCercanas(loc)
            } else {
                fused.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null)
                    .addOnSuccessListener { l2 ->
                        ubicacionObtenida = true
                        binding.progreso.visibility = View.GONE
                        if (l2 != null) {
                            actualizarMapaConMiUbicacion(l2)
                            buscarBibliotecasCercanas(l2)
                        } else {
                            // ya quedó el fallback por defecto
                            toast(getString(R.string.error_generico))
                        }
                    }
                    .addOnFailureListener {
                        binding.progreso.visibility = View.GONE
                        toast(getString(R.string.error_generico))
                    }
            }
        }.addOnFailureListener {
            binding.progreso.visibility = View.GONE
            toast(getString(R.string.error_generico))
        }
    }


    private fun buscarBibliotecasCercanas(loc: Location) {
        val key = getString(R.string.google_maps_key)
        val url = "https://maps.googleapis.com/maps/api/place/nearbysearch/json" +
                "?location=${loc.latitude},${loc.longitude}" +
                "&rankby=distance&type=library&language=es" +
                "&key=$key"

        val req = Request.Builder().url(url).build()
        currentApiCall = client.newCall(req)
        currentApiCall?.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { if (isAdded) activity?.runOnUiThread { fallo() } }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string()
                if (!response.isSuccessful || body == null) { if (isAdded) activity?.runOnUiThread { fallo() }; return }

                try {
                    val dto = Gson().fromJson(body, NearbyResponse::class.java)
                    val results = dto.results.take(5)
                    if (isAdded) {
                        activity?.runOnUiThread {
                            binding.progreso.visibility = View.GONE
                            // Limpia y re-agrega tu marcador
                            gMap?.clear()
                            actualizarMapaConMiUbicacion(loc)

                            if (results.isEmpty()) {
                                toast(getString(R.string.sin_resultados))
                            } else {
                                results.forEach { r ->
                                    gMap?.addMarker(
                                        MarkerOptions()
                                            .position(LatLng(r.geometry.location.lat, r.geometry.location.lng))
                                            .title(r.name ?: "")
                                    )
                                }
                            }
                        }
                    }
                } catch (_: Exception) {
                    if (isAdded) activity?.runOnUiThread { fallo() }
                }
            }
        })
    }

    @SuppressLint("MissingPermission")
    private fun actualizarMapaConMiUbicacion(loc: Location) {
        val yo = LatLng(loc.latitude, loc.longitude)
        gMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(yo, 15f))

        val fine = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION)
        if (fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED) {
            try { gMap?.isMyLocationEnabled = true } catch (_: SecurityException) {}
        }
        gMap?.addMarker(MarkerOptions().position(yo).title(getString(R.string.mi_ubicacion)))
    }

    private fun fallo() {
        if (_binding != null) binding.progreso.visibility = View.GONE
        toast(getString(R.string.error_generico))
    }

    private fun toast(msg: String) {
        if (isAdded) Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        currentApiCall?.cancel()
        gMap = null
        _binding = null
    }

    // DTOs mínimos
    data class NearbyResponse(val results: List<Result> = emptyList())
    data class Result(val name: String?, val geometry: Geometry, val vicinity: String?, val formatted_address: String?)
    data class Geometry(val location: L)
    data class L(val lat: Double, val lng: Double)

    companion object {
        fun newInstance() = MapaBibliotecasFragment()
    }
}
