package com.example.facecheckpoc

import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import com.example.facecheckpoc.databinding.ActivityMainBinding
import com.example.facecheckpoc.verification.Verification2Fragment


class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: UserViewModel
    private var userFace: ByteArray? = null
    private lateinit var verification2Fragment: Verification2Fragment


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)


        binding = ActivityMainBinding.inflate(layoutInflater)
        viewModel = ViewModelProvider(this)[UserViewModel::class.java]
        enableEdgeToEdge()
        setContentView(binding.root)

        binding.buttonVerify.setOnClickListener {
            cpfVerifcation()
        }

        observe()
    }

    private fun navigate() {

        // Atribui a instância da Verification2Fragment
        verification2Fragment = Verification2Fragment().apply {
            arguments = Bundle().apply {
                putByteArray("face", userFace)
            }
        }

        // Adicione a fragment ao gerenciador de fragments
        supportFragmentManager.beginTransaction()
            .add(R.id.fragment_container, verification2Fragment)
            .commit()
    }


    private fun cpfVerifcation() {
        viewModel.getUserByCpf(binding.editCpf.text.toString())
        val inputCpf = binding.inputCpf
        if (binding.editCpf.text.isNullOrBlank()) {
            inputCpf.error = "Campo obrigatório"
            return
        } else if (userFace == null) {
            inputCpf.error = "Usuário não identificado"
            return
        } else {
            inputCpf.error = null
            fecharTeclado()
            navigate()

        }
    }

    private fun observe() {
        viewModel.userModel.observe(this, Observer {
            if (it != null) {
                userFace = it.face

            }
        })
    }

    fun fecharTeclado() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(currentFocus?.windowToken ?: View(this).windowToken, 0)
    }
}


