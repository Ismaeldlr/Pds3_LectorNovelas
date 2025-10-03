package com.example.lectornovelaselectronicos.Fragmentos

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.example.lectornovelaselectronicos.R
import com.example.lectornovelaselectronicos.Login_email
import com.example.lectornovelaselectronicos.databinding.FragmentCuentaBinding
import com.google.firebase.auth.FirebaseAuth
class Cuenta : Fragment() {

    private lateinit var binding: FragmentCuentaBinding
    private lateinit var firebaseAuth: FirebaseAuth
    private lateinit var nContext: Context

    override fun onAttach(context: Context) {
        nContext = context
        super.onAttach(context)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentCuentaBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        firebaseAuth = FirebaseAuth.getInstance()

        // Rellena la info si ya la tienes
        val user = firebaseAuth.currentUser
        binding.tvEmail.text = user?.email ?: "—"
        binding.tvNombre.text = user?.displayName ?: "—"

        binding.BtnCerrarSesion.setOnClickListener {
            FirebaseAuth.getInstance().signOut()
            startActivity(Intent(nContext, Login_email::class.java))
            activity?.finish()
        }

        binding.btnEditarPerfil.setOnClickListener {
            // Navega a tu pantalla de edición de perfil
            // findNavController().navigate(R.id.action_cuenta_to_editarPerfil)
        }
    }
}
