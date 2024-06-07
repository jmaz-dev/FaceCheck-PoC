package com.example.facecheckpoc.verification

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import com.airbnb.lottie.LottieAnimationView
import com.example.facecheckpoc.R
import com.example.facecheckpoc.databinding.FragmentVerification2Binding
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions

class Verification2Fragment : DialogFragment() {

    private lateinit var binding: FragmentVerification2Binding
    private lateinit var cameraProvider: ProcessCameraProvider
    private lateinit var camera: Camera
    private lateinit var preview: Preview
    private lateinit var imageCapture: ImageCapture
    private lateinit var faceDetector: FaceDetector
    private lateinit var animationView: LottieAnimationView

    private val getPermissionResult =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) {
            if (it) {
                startCamera()
            } else {
                Toast.makeText(
                    context,
                    "Permissão de câmera é obrigatória para continuar!",
                    Toast.LENGTH_LONG
                ).show()
                dismiss()
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentVerification2Binding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Inicializar a animação
        animationView = binding.animation
        animationView.setAnimation(R.raw.scanning_animation)
        animationView.playAnimation()

        // Ocultar a barra de ferramentas
        val supportActionBar = (requireActivity() as AppCompatActivity).supportActionBar
        supportActionBar?.hide()

        // Solicitar permissão da câmera
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        } else {
            getPermissionResult.launch(Manifest.permission.CAMERA)
        }

        // Configurar o detector de rostos
        configureFaceDetector()

//        // Implementar as animações
//        setupAnimations()

        // Implementar o dialog
        setupDialog()
    }

    private fun startCamera() {
        Log.d(TAG, "Starting camera")
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())

        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()

                // Criar os casos de uso da câmera
                preview = Preview.Builder().build()
                imageCapture = ImageCapture.Builder().build()

                // Selecionar a câmera padrão (frontal)
                val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

                // Desvincular todos os casos de uso antes de vincular novamente
                cameraProvider.unbindAll()

                // Vincular os casos de uso à câmera
                camera = cameraProvider.bindToLifecycle(
                    this, // LifecycleOwner
                    cameraSelector,
                    preview,
                    imageCapture
                )

                // Configurar a preview
                preview.setSurfaceProvider(binding.cameraPreview.surfaceProvider)

                Log.d(TAG, "Camera preview initialized")
            } catch (exc: Exception) {
                Log.e(TAG, "Falha ao inicializar a câmera", exc)
            }
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun configureFaceDetector() {
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
            .build()

        faceDetector = FaceDetection.getClient(options)
    }

//    private fun setupAnimations() {
//        // Carregar as animações Lottie dinamicamente
//        val animation = binding.animation
//        Log.d(TAG, "Animation file: ${animation.animation}")
//        animation.setAnimation(R.raw.scanning_animation)
//    }

    private fun setupDialog() {
        binding.cancelButton.setOnClickListener {
            dismiss()
        }
    }

    companion object {
        private const val TAG = "VerificationFragment"
    }
}