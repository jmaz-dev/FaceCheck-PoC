package com.example.facecheckpoc

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import com.example.facecheckpoc.data.UserModel
import com.example.facecheckpoc.databinding.ActivityFormUserBinding
import java.io.ByteArrayOutputStream

class UserFormActivity() : AppCompatActivity(), View.OnClickListener {

    private lateinit var binding: ActivityFormUserBinding
    private lateinit var viewModel: UserViewModel
    private lateinit var takePictureLauncher: ActivityResultLauncher<Void?>
    private var encodedImage: String = ""

    companion object {
        private const val REQUEST_CAMERA_PERMISSION = 1
        private var IS_PICTURE_TAKED = 0
        private const val TAG = "UserFormActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        viewModel = ViewModelProvider(this).get(UserViewModel::class.java)
        binding = ActivityFormUserBinding.inflate(layoutInflater)

        enableEdgeToEdge()

        setContentView(binding.root)

        supportActionBar?.setTitle(getString(R.string.form_title))

        binding.buttonSubmit.setOnClickListener(this)
        binding.imageCapture.setOnClickListener(this)

        pictureLauncher()

        observeViewModel()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.action_settings, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_settings_sub1 -> {
                startActivity(Intent(this, MainActivity::class.java))
                return true
            }

            else -> return super.onOptionsItemSelected(item)
        }
    }

    override fun onClick(v: View) {
        when (v.id) {
            R.id.button_submit -> {
                handleFormSubmit()
            }

            R.id.image_capture -> {
                handleImageCapture()
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                // Permission was granted, launch the camera
                takePictureLauncher.launch(null)
            } else {
                // Permission denied
                useToast("Permissão da câmera negada")
            }
        }
    }

    private fun pictureLauncher() {
        takePictureLauncher =
            registerForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
                if (bitmap != null) {
                    // Converta o bitmap para um array de bytes
                    val byteArrayOutputStream = ByteArrayOutputStream()
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, byteArrayOutputStream)
                    val byteArray = byteArrayOutputStream.toByteArray()

                    // Converta o array de bytes para uma string Base64
                    encodedImage = Base64.encodeToString(byteArray, Base64.DEFAULT)

                    // Exiba a imagem no ImageView, se necessário
                    binding.imageCapture.setImageBitmap(bitmap)

                    IS_PICTURE_TAKED = 1
                } else {
                    Log.d(TAG, "Bitmap is null")
                    useToast("Failed to capture image")
                }
            }
    }

    private fun handleImageCapture() {

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                REQUEST_CAMERA_PERMISSION
            )
        } else {
            takePictureLauncher.launch(null)
        }
    }

    private fun handleFormSubmit() {
        val name = binding.editName.text.toString()
        val cpf = binding.editCpf.text.toString()

        if (name.isNotEmpty() && cpf.isNotEmpty() && IS_PICTURE_TAKED == 1) {
            val user = UserModel()
            user.name = name
            user.cpf = cpf
            user.face = encodedImage
            viewModel.submitUser(user)

        } else {
            useToast("Preencha todos os campos")
        }
    }

    private fun observeViewModel() {
        viewModel.userSubmit.observe(this) {
            if (it) {
                useToast("Usuário cadastrado com sucesso")
                binding.editName.setText("")
                binding.editCpf.setText("")
                binding.imageCapture.setImageResource(R.drawable.ic_image)
                IS_PICTURE_TAKED = 0
            } else {
                useToast("Ocorreu um erro ao tentar cadastrar")
            }
        }
    }

    private fun useToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}