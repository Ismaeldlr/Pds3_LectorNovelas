package com.example.lectornovelaselectronicos

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.lectornovelaselectronicos.Fragmentos.Biblioteca
import com.example.lectornovelaselectronicos.Fragmentos.Explorar
import com.example.lectornovelaselectronicos.Fragmentos.Historial
import com.example.lectornovelaselectronicos.Fragmentos.Cuenta
import com.example.lectornovelaselectronicos.databinding.ActivityMainBinding
import android.content.Intent
import com.google.firebase.auth.FirebaseAuth


class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var firebaseAuth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        firebaseAuth = FirebaseAuth.getInstance()

        VerFragmentBiblioteca()

        binding.BottomNV.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.Item_Biblioteca -> {
                    VerFragmentBiblioteca(); true
                }
                R.id.Item_Explorar -> {
                    VerFragmentExplorar(); true
                }
                R.id.Item_Historial -> {
                    VerFragmentHistorial(); true
                }
                R.id.Item_Cuenta -> {
                    if (firebaseAuth.currentUser == null) {
                        startActivity(Intent(this, Login_email::class.java))
                        false
                    } else {
                        VerFragmentCuenta(); true
                    }
                }
                else -> false
            }
        }
    }

    private fun VerFragmentBiblioteca() {
        binding.TituloRL.text = "Biblioteca"
        val fragment = Biblioteca()
        supportFragmentManager.beginTransaction()
            .replace(binding.FragmentL1.id, fragment, "Biblioteca")
            .commit()
    }

    private fun VerFragmentExplorar() {
        binding.TituloRL.text = "Explorar"
        val fragment = Explorar()
        supportFragmentManager.beginTransaction()
            .replace(binding.FragmentL1.id, fragment, "Explorar")
            .commit()
    }

    private fun VerFragmentHistorial() {
        binding.TituloRL.text = "Historial"
        val fragment = Historial()
        supportFragmentManager.beginTransaction()
            .replace(binding.FragmentL1.id, fragment, "Historial")
            .commit()
    }

    private fun VerFragmentCuenta() {
        binding.TituloRL.text = "Cuenta"
        val fragment = Cuenta()
        supportFragmentManager.beginTransaction()
            .replace(binding.FragmentL1.id, fragment, "Cuenta")
            .commit()
    }
}