package com.example.facecheckpoc.verification

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.facecheckpoc.R
import com.example.facecheckpoc.databinding.FragmentVerification2Binding
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class Verification2Fragment : Fragment() {

    private lateinit var binding: FragmentVerification2Binding
    private lateinit var cameraProvider: ProcessCameraProvider
    private lateinit var camera: Camera
    private lateinit var preview: Preview
    private lateinit var imageCapture: ImageCapture
    private lateinit var faceDetector: FaceDetector

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

        // Ocultar a barra de ferramentas
        val supportActionBar = (requireActivity() as AppCompatActivity).supportActionBar
        supportActionBar?.hide()

        // Configurar a câmera
        lifecycleScope.launch {
            startCamera()
        }

        // Configurar o detector de rostos
        configureFaceDetector()

        // Implementar as animações
        setupAnimations()

        // Implementar o dialog
        setupDialog()
    }

    private suspend fun startCamera() {
        cameraProvider = withContext(Dispatchers.IO) {
            ProcessCameraProvider.getInstance(requireContext()).get()
        }

        // Criar o uso de casos (Preview, ImageCapture, etc.)
        preview = Preview.Builder().build()
        imageCapture = ImageCapture.Builder().build()

        // Selecionar a câmera padrão (traseira)
        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

        try {
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
        } catch (exc: Exception) {
            Log.e(TAG, "Falha ao vincular os casos de uso à câmera", exc)
        }
    }

    private fun configureFaceDetector() {
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
            .build()

        faceDetector = FaceDetection.getClient(options)
    }

    private fun setupAnimations() {
        // Carregar as animações Lottie dinamicamente
        binding.animation.setAnimation(R.raw.scanning_annimation)
    }

    private fun setupDialog() {
        binding.cancelButton.setOnClickListener {
            // Mostrar o dialog
            val dialog = DialogFragment()
            dialog.show(childFragmentManager, "dialog")
        }
    }

    companion object {
        private const val TAG = "VerificationFragment"
    }
}