package com.example.facecheckpoc.verification

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.ImageFormat
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.YuvImage
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.airbnb.lottie.LottieAnimationView
import com.example.facecheckpoc.R
import com.example.facecheckpoc.databinding.FragmentVerification2Binding
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.*
import org.tensorflow.lite.Interpreter
import java.io.*
import java.nio.*
import java.nio.channels.FileChannel

class Verification2Fragment : DialogFragment() {
    private lateinit var binding: FragmentVerification2Binding
    private lateinit var userFace: ByteArray
    private lateinit var cameraProvider: ProcessCameraProvider
    private lateinit var camera: Camera
    private lateinit var preview: Preview
    private lateinit var imageCapture: ImageCapture
    private lateinit var faceDetector: FaceDetector
    private lateinit var animationView: LottieAnimationView
    private lateinit var interpreter: Interpreter
    private var detectFaceJob: Job? = null

    private val getPermissionResult = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            userFace = it.getByteArray("face") ?: ByteArray(0)
        }


    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentVerification2Binding.inflate(inflater, container, false)
        binding.cancelButton.setOnClickListener { dismiss() }
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
            startFaceDetectionTimeout()
        } else {
            getPermissionResult.launch(Manifest.permission.CAMERA)
        }

        // Configurar o detector de rostos
        configureFaceDetector()

        // Interpreter
        interpreter = Interpreter(loadModelFile("mobile_face_net.tflite"))

    }

    override fun onDestroyView() {
        super.onDestroyView()
        detectFaceJob?.cancel()
    }

    private fun loadModelFile(modelPath: String): MappedByteBuffer {
        val assetFileDescriptor = requireContext().assets.openFd(modelPath)
        val fileInputStream = FileInputStream(assetFileDescriptor.fileDescriptor)
        val fileChannel = fileInputStream.channel
        val startOffset = assetFileDescriptor.startOffset
        val declaredLength = assetFileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    @OptIn(ExperimentalGetImage::class)
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())

        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()

                preview = Preview.Builder().build()
                imageCapture = ImageCapture.Builder().build()
                val imageAnalyzer = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also {
                        it.setAnalyzer(
                            ContextCompat.getMainExecutor(requireContext()),
                            { imageProxy ->
                                processImageProxy(imageProxy)
                            })
                    }

                val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

                cameraProvider.unbindAll()

                camera = cameraProvider.bindToLifecycle(
                    this,
                    cameraSelector,
                    preview,
                    imageCapture,
                    imageAnalyzer
                )

                preview.setSurfaceProvider(binding.cameraPreview.surfaceProvider)

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

    private fun startFaceDetectionTimeout() {
        detectFaceJob = lifecycleScope.launch {
            delay(10000) // Espera por 10 segundos
            binding.textInfo.text = "Não foi possível detectar o rosto"
            stopCameraPreview()
        }
    }

    private fun stopCameraPreview() {
        cameraProvider.unbindAll()
        binding.cameraPreview.visibility = View.GONE
    }

    @ExperimentalGetImage
    private fun processImageProxy(imageProxy: ImageProxy) {
        val rotationDegrees = imageProxy.imageInfo.rotationDegrees
        val image = imageProxy.image ?: run { imageProxy.close(); return }
        val inputImage = InputImage.fromMediaImage(image, rotationDegrees)
        faceDetector.process(inputImage)
            .addOnSuccessListener { faces ->
                if (faces.isNotEmpty()) {
                    binding.textInfo.text = getString(R.string.detecting_face)
                    detectFaceJob?.cancel()

                    // Obtenha a face do ViewModel
                    val storedEmbeding = userFace
                    val cameraEmbeding = imageProxyToBitmap(imageProxy)

                    // Passe a face para a função de processamento
                    if (cameraEmbeding != null) {
                        processImageWithTFLite(storedEmbeding, cameraEmbeding)
                    } else {
                        binding.textInfo.text = getString(R.string.face_invalid_angles)
                    }
                } else {
                    binding.textInfo.text = getString(R.string.face_not_centered)
                }
                imageProxy.close()
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Falha na detecção de rosto", e)
                binding.textInfo.text = "Falha na detecção de rosto"
                imageProxy.close()
            }
    }

    private fun processImageWithTFLite(storedImage: ByteArray?, cameraEmbeding: Bitmap) {
        if (storedImage != null) {
            // Converta o ByteArray da imagem armazenada para um bitmap
            val storedBitmap = BitmapFactory.decodeByteArray(storedImage, 0, storedImage.size)

            // Verifique se o bitmap não é nulo
            if (storedBitmap != null) {
                // Redimensione o bitmap armazenado para o tamanho desejado
                val resizedStoredBitmap =
                    Bitmap.createScaledBitmap(storedBitmap, INPUT_SIZE, INPUT_SIZE, false)
                val storedInputBuffer = convertBitmapToByteBuffer(resizedStoredBitmap)
                val storedOutputBuffer = Array(1) { FloatArray(OUTPUT_SIZE) }

                // Execute a inferência do modelo TFLite na imagem armazenada
                interpreter.run(storedInputBuffer, storedOutputBuffer)

                // Converta o bitmap da imagem da câmera para um vetor de embeddings
                val resizedCameraBitmap =
                    Bitmap.createScaledBitmap(cameraEmbeding, INPUT_SIZE, INPUT_SIZE, false)
                val cameraInputBuffer = convertBitmapToByteBuffer(resizedCameraBitmap)
                val cameraOutputBuffer = Array(1) { FloatArray(OUTPUT_SIZE) }

                // Execute a inferência do modelo TFLite para a imagem da câmera
                interpreter.run(cameraInputBuffer, cameraOutputBuffer)

                // Compare os rostos
                if (storedOutputBuffer[0].size == OUTPUT_SIZE && cameraOutputBuffer[0].size == OUTPUT_SIZE) {
                    // Faça o que for necessário com os embeddings
                    compareFaces(storedOutputBuffer[0], cameraOutputBuffer[0])
                } else {
                    Log.e(
                        TAG,
                        "Erro de tamanho de embedding: output size = ${storedOutputBuffer[0].size}, cameraEmbedding size = ${cameraOutputBuffer[0].size}, expected $OUTPUT_SIZE"
                    )
                    binding.textInfo.text = "Erro de tamanho de embedding"
                }
            } else {
                Log.d(TAG, "Falha na conversão do ByteArray para Bitmap")
                binding.textInfo.text = "Falha na conversão da imagem"
            }
        } else {
            Log.d(TAG, "ByteArray do rosto é nulo")
            binding.textInfo.text = "ByteArray do rosto é nulo"
        }
    }

    private fun preprocessImage(bitmap: Bitmap): Bitmap {
        // Redimensionamento da imagem para o tamanho desejado
        val resizedBitmap = Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, false)

        // Normalização dos valores de pixel
        val normalizedBitmap = Bitmap.createBitmap(INPUT_SIZE, INPUT_SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(normalizedBitmap)
        val paint = Paint()
        paint.colorFilter = ColorMatrixColorFilter(ColorMatrix().apply {
            setScale(
                1f / IMAGE_STD,
                1f / IMAGE_STD,
                1f / IMAGE_STD,
                1f
            )
        })
        canvas.drawBitmap(resizedBitmap, 0f, 0f, paint)

        return normalizedBitmap
    }

    @OptIn(ExperimentalGetImage::class)
    private fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap? {
        val image = imageProxy.image ?: return null
        val yBuffer = image.planes[0].buffer
        val uBuffer = image.planes[1].buffer
        val vBuffer = image.planes[2].buffer

        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        val nv21 = ByteArray(ySize + uSize + vSize)
        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)

        val yuvImage = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, image.width, image.height), 100, out)
        val yuvByteArray = out.toByteArray()
        return BitmapFactory.decodeByteArray(yuvByteArray, 0, yuvByteArray.size)
    }

    private fun convertBitmapToByteBuffer(bitmap: Bitmap): ByteBuffer {
        val bitmapProcessed = preprocessImage(bitmap)

        val byteBuffer = ByteBuffer.allocateDirect(4 * INPUT_SIZE * INPUT_SIZE * 3)
        byteBuffer.order(ByteOrder.nativeOrder())
        val intValues = IntArray(INPUT_SIZE * INPUT_SIZE)
        bitmapProcessed.getPixels(
            intValues,
            0,
            bitmapProcessed.width,
            0,
            0,
            bitmapProcessed.width,
            bitmapProcessed.height
        )
        var pixel = 0
        for (i in 0 until INPUT_SIZE) {
            for (j in 0 until INPUT_SIZE) {
                val value = intValues[pixel++]
                byteBuffer.putFloat(((value shr 16 and 0xFF) - IMAGE_MEAN) / IMAGE_STD)
                byteBuffer.putFloat(((value shr 8 and 0xFF) - IMAGE_MEAN) / IMAGE_STD)
                byteBuffer.putFloat(((value and 0xFF) - IMAGE_MEAN) / IMAGE_STD)
            }
        }
        return byteBuffer
    }

    private fun compareFaces(storedEmbedding: FloatArray, cameraEmbedding: FloatArray) {
        // Calcule a distância entre os embeddings
        val distance = calculateEuclideanDistance(storedEmbedding, cameraEmbedding)

        // Defina um limiar para considerar se os rostos são semelhantes
        val threshold = 1.0f

        Log.d(TAG, "Valor da Distancia de Euclide: $distance")
        if (distance < threshold) {
            binding.textInfo.text = "Rostos correspondem"
        } else {
            binding.textInfo.text = "Rostos não correspondem"
        }
    }

    private fun calculateEuclideanDistance(embedding1: FloatArray, embedding2: FloatArray): Float {
        var sum = 0f
        for (i in embedding1.indices) {
            val diff = embedding1[i] - embedding2[i]
            sum += diff * diff
        }
        return Math.sqrt(sum.toDouble()).toFloat()
    }

    companion object {
        private const val TAG = "Verification2Fragment"
        private const val INPUT_SIZE = 112
        private const val IMAGE_MEAN = 128.0f
        private const val IMAGE_STD = 128.0f
        private const val OUTPUT_SIZE = 192
        private const val THRESHOLD = 1.0f
    }
}
