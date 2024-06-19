package com.example.facecheckpoc

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.ExperimentalGetImage
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.graphics.scale
import androidx.lifecycle.ViewModelProvider
import com.example.facecheckpoc.data.UserModel
import com.example.facecheckpoc.databinding.ActivityFormUserBinding
import com.example.facecheckpoc.utils.scale
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val bitmapSize: Int = 1280

class UserFormActivity() : AppCompatActivity(), View.OnClickListener {

    private lateinit var binding: ActivityFormUserBinding
    private lateinit var viewModel: UserViewModel
    private lateinit var takePictureLauncher: ActivityResultLauncher<Uri>
    private lateinit var photoFile: File
    private var encodedImage: ByteArray? = null
    private lateinit var photoUri: Uri
    private lateinit var currentPhotoPath: String

    companion object {
        private const val REQUEST_CAMERA_PERMISSION = 1
        private var IS_PICTURE_TAKED = 0
        private const val TAG = "UserFormActivity"
    }

    @RequiresApi(Build.VERSION_CODES.P)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        viewModel = ViewModelProvider(this).get(UserViewModel::class.java)
        binding = ActivityFormUserBinding.inflate(layoutInflater)

        enableEdgeToEdge()

        setContentView(binding.root)

        supportActionBar?.setTitle(getString(R.string.form_title))

        binding.buttonSubmit.setOnClickListener(this)
        binding.imageCapture.setOnClickListener(this)
        binding.imagePreview.setOnClickListener(this)

        pictureLauncher()

        observeViewModel()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.action_settings, menu)
        return true
    }

    @OptIn(ExperimentalGetImage::class)
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

            R.id.image_preview -> {
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

            } else {
                // Permission denied
                useToast("Permissão da câmera negada")
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun pictureLauncher() {
        takePictureLauncher =
            registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
                if (success) {
                    // Imagem capturada com sucesso, agora você pode acessar a
                    val bitmap = ImageDecoder.decodeBitmap(
                        ImageDecoder.createSource(
                            contentResolver,
                            photoUri
                        )
                    )
                    bitmap.scale(bitmapSize)
//                    val bitmap = BitmapFactory.decodeFile(photoFile.absolutePath)
                    Log.d("ImageSize", bitmap.width.toString() + "x" + bitmap.height)

                    binding.imagePreview.visibility = View.VISIBLE
                    binding.imageCapture.visibility = View.INVISIBLE
                    binding.imagePreview.setImageBitmap(bitmap)
                    IS_PICTURE_TAKED = 1

                    // Converta o bitmap para um array de bytes (compressão opcional)
                    val byteArrayOutputStream = ByteArrayOutputStream()
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, byteArrayOutputStream)
                    val byteArray = byteArrayOutputStream.toByteArray()

                    // Armazene o byteArray conforme necessário
                    encodedImage = byteArray
                } else {
                    // Falha ao capturar imagem
                    Log.d(TAG, "Failed to capture image")
                    useToast("Failed to capture image")
                }
            }
    }

//    private fun pictureLauncher() {
//        takePictureLauncher =
//            registerForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
//                if (bitmap != null) {
//
//                    // Exibir a imagem no ImageView
//                    binding.imagePreview.visibility = View.VISIBLE
//                    binding.imageCapture.visibility = View.INVISIBLE
//                    binding.imagePreview.setImageBitmap(bitmap)
//                    IS_PICTURE_TAKED = 1
//
//                    // Converta o bitmap para um array de bytes
//                    val byteArrayOutputStream = ByteArrayOutputStream()
//                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, byteArrayOutputStream)
//                    val byteArray = byteArrayOutputStream.toByteArray()
//
//                    // Converta o array de bytes para uma string Base64
//                    encodedImage = byteArray
//                    Log.d(TAG, "Captured image quality: ${byteArray.size} bytes")
//
//                } else {
//                    Log.d(TAG, "Bitmap is null")
//                    useToast("Failed to capture image")
//                }
//            }
//    }

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
            photoFile = createImageFile()
            photoUri = FileProvider.getUriForFile(
                this,
                "${applicationContext.packageName}.provider",
                photoFile
            )
            takePictureLauncher.launch(photoUri)
        }
    }

    private fun createImageFile(): File {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile("JPEG_${timestamp}_", ".jpg", storageDir).apply {
            // Save a file: path for use with ACTION_VIEW intents
            currentPhotoPath = absolutePath
        }
    }

    private fun handleFormSubmit() {
        val name = binding.editName.text.toString()
        val cpf = binding.editCpf.text.toString()

        if (name.isNotEmpty() && cpf.isNotEmpty() && IS_PICTURE_TAKED == 1) {
            val user = UserModel()
            user.name = name
            user.cpf = cpf
            user.face = encodedImage!!
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
                binding.imagePreview.visibility = View.INVISIBLE
                binding.imagePreview.setImageBitmap(null)
                binding.imageCapture.visibility = View.VISIBLE
                binding.imageCapture.setImageResource(R.drawable.ic_person)
                binding.imageCapture.setBackgroundResource(R.drawable.rounded_icon)
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