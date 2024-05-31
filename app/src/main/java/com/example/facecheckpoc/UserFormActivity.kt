package com.example.facecheckpoc

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Base64
import android.util.Log
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

class UserFormActivity : AppCompatActivity(), View.OnClickListener {

    lateinit var binding: ActivityFormUserBinding
    lateinit var viewModel: FormViewModel
    var encodedImage: String = ""
    private lateinit var takePictureLauncher: ActivityResultLauncher<Void?>

    companion object {
        private const val REQUEST_CAMERA_PERMISSION = 1
        private const val TAG = "UserFormActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        viewModel = ViewModelProvider(this)[FormViewModel::class.java]
        binding = ActivityFormUserBinding.inflate(layoutInflater)
        enableEdgeToEdge()
        setContentView(binding.root)


        binding.buttonSubmit.setOnClickListener(this)
        binding.imageCapture.setOnClickListener(this)

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
                } else {
                    Log.d(TAG, "Bitmap is null")
                    useToast("Failed to capture image")
                }
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

        if (name.isNotEmpty() && cpf.isNotEmpty() && encodedImage.isNotEmpty()) {
            val user = UserModel()
            user.name = name
            user.cpf = cpf
            user.face = encodedImage
            viewModel.submitUser(user)
        } else {
            useToast("Preencha todos os campos")
        }
    }

    private fun useToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}