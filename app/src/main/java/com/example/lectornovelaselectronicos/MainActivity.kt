package com.example.lectornovelaselectronicos

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.lectornovelaselectronicos.Fragmentos.Biblioteca
import com.example.lectornovelaselectronicos.Fragmentos.Explorar
import com.example.lectornovelaselectronicos.Fragmentos.Historial
import com.example.lectornovelaselectronicos.Fragmentos.Cuenta
import com.example.lectornovelaselectronicos.databinding.ActivityMainBinding
import android.content.Intent


class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        //VerFragmentBiblioteca()

        binding.BottomNV.setOnItemSelectedListener { Item ->
            when (Item.itemId) {
                R.id.Item_Biblioteca->{
                    //VerFragmentBiblioteca()
                    true
                }
                R.id.Item_Explorar->{
                    //VerFragmentExplorar()
                    true
                }
                R.id.Item_Historial->{
                    //VerFragmentHistorial()
                    true
                }
                R.id.Item_Cuenta->{
                    val intent = Intent(this, Login_email::class.java) // Tu actividad Login_email
                    startActivity(intent) // Esto abre la pantalla de login
                    true

                    //VerFragmentCuenta()
                    true
                }
                else->{
                    false
                }
            }
        }
    }
    private fun VerFragmentBiblioteca(){
        binding.TituloRL.setText("Biblioteca")
        val fragment = Biblioteca()
        val fragmentTransition = supportFragmentManager.beginTransaction()
        fragmentTransition.replace(binding.FragmentL1.id, fragment, "Biblioteca").commit()
        fragmentTransition.commit()
    }

    private fun VerFragmentExplorar(){
        binding.TituloRL.setText("Explorar")
        val fragment = Explorar()
        val fragmentTransition = supportFragmentManager.beginTransaction()
        fragmentTransition.replace(binding.FragmentL1.id, fragment, "Explorar").commit()
        fragmentTransition.commit()
    }

    private fun VerFragmentHistorial(){
        binding.TituloRL.setText("Historial")
        val fragment = Historial()
        val fragmentTransition = supportFragmentManager.beginTransaction()
        fragmentTransition.replace(binding.FragmentL1.id, fragment, "Historial").commit()
        fragmentTransition.commit()
    }

    private fun VerFragmentCuenta() {
        binding.TituloRL.setText("Cuenta")
        val fragment = Cuenta()
        val fragmentTransition = supportFragmentManager.beginTransaction()
        fragmentTransition.replace(binding.FragmentL1.id, fragment, "Cuenta").commit()
        fragmentTransition.commit()
    }
}