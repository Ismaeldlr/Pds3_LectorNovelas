package com.example.lectornovelaselectronicos

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.example.lectornovelaselectronicos.Fragmentos.Biblioteca
import com.example.lectornovelaselectronicos.Fragmentos.Cuenta
import com.example.lectornovelaselectronicos.Fragmentos.Explorar
import com.example.lectornovelaselectronicos.Fragmentos.Historial
import com.example.lectornovelaselectronicos.databinding.ActivityMainBinding
import com.google.firebase.auth.FirebaseAuth

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }

    // Listener para asegurarnos de que la sesión esté restaurada
    private val authListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        val u = firebaseAuth.currentUser
        Log.d("AUTH", "AuthStateListener user=${u?.uid} email=${u?.email}")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

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
                    val user = auth.currentUser
                    Log.d("AUTH", "BottomNV -> currentUser=${user?.email}")
                    if (user != null) {
                        VerFragmentCuenta(); true
                    } else {
                        startActivity(Intent(this, Login_email::class.java))
                        true
                    }
                }
                else -> false
            }
        }
    }

    override fun onStart() {
        super.onStart()
        auth.addAuthStateListener(authListener)
    }

    override fun onStop() {
        super.onStop()
        auth.removeAuthStateListener(authListener)
    }

    private fun VerFragmentBiblioteca() {
        val fragment = Biblioteca()
        supportFragmentManager.beginTransaction()
            .replace(binding.FragmentL1.id, fragment, "Biblioteca")
            .commit()
    }

    private fun VerFragmentExplorar() {
        val fragment = Explorar()
        supportFragmentManager.beginTransaction()
            .replace(binding.FragmentL1.id, fragment, "Explorar")
            .commit()
    }

    private fun VerFragmentHistorial() {
        val fragment = Historial()
        supportFragmentManager.beginTransaction()
            .replace(binding.FragmentL1.id, fragment, "Historial")
            .commit()
    }

    private fun VerFragmentCuenta() {
        val fragment = Cuenta()
        supportFragmentManager.beginTransaction()
            .replace(binding.FragmentL1.id, fragment, "Cuenta")
            .commit()
    }
}