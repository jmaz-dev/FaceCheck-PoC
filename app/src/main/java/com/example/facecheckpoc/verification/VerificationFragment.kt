package com.example.facecheckpoc.verification

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.*
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.core.Camera
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.airbnb.lottie.LottieAnimationView
import com.example.facecheckpoc.R
import com.example.facecheckpoc.databinding.FragmentVerificationBinding
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.*
import org.tensorflow.lite.Interpreter
import java.io.*
import java.nio.*
import java.nio.channels.FileChannel
import kotlin.math.pow
import kotlin.math.sqrt

class VerificationFragment : DialogFragment() {
    private lateinit var binding: FragmentVerificationBinding
    private lateinit var userFace: ByteArray
    private lateinit var userName: String
    private lateinit var cameraProvider: ProcessCameraProvider
    private lateinit var camera: Camera
    private lateinit var faceDetector: FaceDetector
    private lateinit var interpreter: Interpreter
    private var detectFaceJob: Job? = null
    private var successDetectJob: Job? = null
    private lateinit var animationView: LottieAnimationView


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
            userName = it.getString("name") ?: ""
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentVerificationBinding.inflate(inflater, container, false)
        binding.cancelButton.setOnClickListener { dismiss() }
        binding.cancelButtonView.setOnClickListener { dismiss() }
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        animationView = binding.animation
        animationView.setAnimation(R.raw.scanning_animation)
        animationView.playAnimation()
        binding.cancelButton.isEnabled = false

        (requireActivity() as AppCompatActivity).supportActionBar?.hide()

        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        } else {
            getPermissionResult.launch(Manifest.permission.CAMERA)
        }

        configureFaceDetector()
        interpreter = Interpreter(loadModelFile("mobile_face_net.tflite"))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        detectFaceJob?.cancel()
        cameraProvider.unbindAll()
    }

    private fun configureFaceDetector() {
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
            .build()
        faceDetector = FaceDetection.getClient(options)
    }

    private fun loadModelFile(modelPath: String): MappedByteBuffer {
        val assetFileDescriptor = requireContext().assets.openFd(modelPath)
        val fileInputStream = FileInputStream(assetFileDescriptor.fileDescriptor)
        val fileChannel = fileInputStream.channel
        return fileChannel.map(
            FileChannel.MapMode.READ_ONLY,
            assetFileDescriptor.startOffset,
            assetFileDescriptor.declaredLength
        )
    }


    private fun startCamera() {
        startFaceDetectionTimeout()
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
            bindCameraUseCases()
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun startFaceDetectionTimeout() {
        detectFaceJob?.cancel()
        detectFaceJob = lifecycleScope.launch {
            delay(10000)
            if (isAdded) {
                binding.textInfo.text = "Não foi possível detectar o rosto"
                Log.d("FaceDetectionTimeout", "Timeout reached")
                stopCameraPreview()
            }
        }
    }

    private fun stopCameraPreview() {
        cameraProvider.unbindAll()
        binding.cameraPreview.visibility = View.INVISIBLE
        binding.cancelButton.isEnabled = true
        animationView = binding.animation
        animationView.setAnimation(R.raw.fail_animation)
        animationView.repeatCount = 0
        animationView.playAnimation()
    }

    @OptIn(ExperimentalGetImage::class)
    private fun bindCameraUseCases() {
        val preview = Preview.Builder().build()
        val imageCapture = ImageCapture.Builder().build()
        val imageAnalyzer = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also {
                it.setAnalyzer(ContextCompat.getMainExecutor(requireContext()), ::processImageProxy)
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
    }


    @OptIn(ExperimentalGetImage::class)
    private fun processImageProxy(imageProxy: ImageProxy) {
        lifecycleScope.launch(Dispatchers.IO) {
            val rotationDegrees = imageProxy.imageInfo.rotationDegrees
            val image = imageProxy.image ?: run { imageProxy.close(); return@launch }
            val inputImage = InputImage.fromMediaImage(image, rotationDegrees)
            faceDetector.process(inputImage)
                .addOnSuccessListener { faces ->
                    lifecycleScope.launch(Dispatchers.Main) {
                        if (faces.isNotEmpty()) {
                            binding.textInfo.text = getString(R.string.detecting_face)

                            // Transform cameraEmbedding to bitmap and preprocess the bitmap
                            val cameraEmbedding = imageProxyToBitmap(imageProxy)

                            // Transform storedEmbedding to bitmap and preprocess the bitmap
                            val storedBitmap =
                                BitmapFactory.decodeByteArray(userFace, 0, userFace.size)
                            val storedEmbedding = preprocessImage(storedBitmap)

                            if (cameraEmbedding != null) {
                                processImageWithTFLite(storedEmbedding, cameraEmbedding, faces)
                            } else {
                                binding.textInfo.text = getString(R.string.face_not_centered)
                            }
                        } else {
                            binding.textInfo.text = getString(R.string.face_invalid_angles)
                        }
                        imageProxy.close()
                    }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Falha na detecção de rosto", e)
                    binding.textInfo.text = "Falha na detecção de rosto"
                    imageProxy.close()
                }
        }
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
        val bitmap = BitmapFactory.decodeByteArray(yuvByteArray, 0, yuvByteArray.size)

        val matrix = Matrix().apply { postRotate(imageProxy.imageInfo.rotationDegrees.toFloat()) }
        return preprocessImage(
            Bitmap.createBitmap(
                bitmap,
                0,
                0,
                bitmap.width,
                bitmap.height,
                matrix,
                true
            )
        )
    }

    // Resize and convert the image to grayscale
    private fun preprocessImage(bitmap: Bitmap): Bitmap {
        val resizedBitmap = Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, false)
        val grayBitmap = Bitmap.createBitmap(INPUT_SIZE, INPUT_SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(grayBitmap)
        val paint = Paint().apply {
            colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })
        }
        canvas.drawBitmap(resizedBitmap, 0f, 0f, paint)
        return grayBitmap
    }

    private fun processImageWithTFLite(
        storedEmbedding: Bitmap,
        cameraEmbedding: Bitmap,
        faces: List<Face>
    ) {
        detectFaceJob?.cancel()
        val preprocessedStoredBitmap = preprocessImage(storedEmbedding)
        val storedOutputBuffer = runModel(preprocessedStoredBitmap)

        val faceDistances = faces.map { face ->
            val faceBitmap = cropFaceBitmap(cameraEmbedding, face.boundingBox)
            val preprocessedFaceBitmap = preprocessImage(faceBitmap)
            val faceEmbedding = runModel(preprocessedFaceBitmap)
            val distance = calculateEuclideanDistance(storedOutputBuffer, faceEmbedding)
            Log.d(TAG, "Calculated distance: $distance for face: $face")
            Pair(face, distance)
        }

        Log.d(TAG, "Calling drawBoundingBoxAndLabel with faceDistances: $faceDistances")
        val outputBitmap = drawBoundingBoxAndLabel(cameraEmbedding, faceDistances)
        Log.d(
            TAG,
            "Output bitmap created, width: ${outputBitmap.width}, height: ${outputBitmap.height}"
        )

        // Atualizar a ImageView com o bitmap gerado
        binding.textInfo.text = "Rostos processados"
        binding.cameraPreview.visibility = View.INVISIBLE
        binding.animation.visibility = View.GONE
        binding.imageView.visibility = View.VISIBLE

        // Certifique-se de que a escala do bitmap está adequada para a exibição na ImageView
        binding.imageView.scaleType = ImageView.ScaleType.CENTER_CROP
        binding.imageView.setImageBitmap(outputBitmap)

        Log.d(TAG, "ImageView updated with output bitmap")
    }


    private fun unsetImageView() {
        binding.cameraPreview.visibility = View.VISIBLE
        binding.animation.visibility = View.VISIBLE
        binding.imageView.visibility = View.GONE
    }

    private fun runModel(bitmap: Bitmap): FloatArray {
        val inputBuffer = convertBitmapToByteBuffer(bitmap)
        val outputBuffer = Array(1) { FloatArray(OUTPUT_SIZE) }
        interpreter.run(inputBuffer, outputBuffer)
        return outputBuffer[0]
    }

    private fun convertBitmapToByteBuffer(bitmap: Bitmap): ByteBuffer {
        val byteBuffer = ByteBuffer.allocateDirect(4 * INPUT_SIZE * INPUT_SIZE * 3)
        byteBuffer.order(ByteOrder.nativeOrder())
        val intValues = IntArray(INPUT_SIZE * INPUT_SIZE)
        bitmap.getPixels(intValues, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        intValues.forEach { value ->
            byteBuffer.putFloat(((value shr 16 and 0xFF) - IMAGE_MEAN) / IMAGE_STD)
            byteBuffer.putFloat(((value shr 8 and 0xFF) - IMAGE_MEAN) / IMAGE_STD)
            byteBuffer.putFloat(((value and 0xFF) - IMAGE_MEAN) / IMAGE_STD)
        }
        return byteBuffer
    }

    private fun detectAndDrawBoundingBox(bitmap: Bitmap, storedEmbedding: FloatArray) {
        val inputImage = InputImage.fromBitmap(bitmap, 0)
        faceDetector.process(inputImage)
            .addOnSuccessListener { faces ->
                if (faces.isEmpty()) {
                    binding.textInfo.text = "Não foi possível detectar o rosto"
                    return@addOnSuccessListener
                }
                val faceDistances = faces.map { face ->
                    val faceBitmap = cropFaceBitmap(bitmap, face.boundingBox)
                    val faceEmbedding = preprocessImage(faceBitmap)?.let { runModel(it) }
                    val distance =
                        faceEmbedding?.let { calculateEuclideanDistance(storedEmbedding, it) }
                            ?: Float.MAX_VALUE
                    Pair(face, distance)
                }

                val outputBitmap = drawBoundingBoxAndLabel(bitmap, faceDistances)
                cameraProvider.unbindAll()
                binding.textInfo.text = "Rostos processados"
                binding.cameraPreview.visibility = View.INVISIBLE
                binding.animation.visibility = View.GONE
                binding.imageView.visibility = View.VISIBLE
                binding.cancelButton.isEnabled = true
                binding.imageView.setImageBitmap(outputBitmap)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Falha na detecção de rosto", e)
                binding.textInfo.text = "Falha na detecção de rosto"
            }
    }

    private fun cropFaceBitmap(bitmap: Bitmap, boundingBox: Rect): Bitmap {
        val left = boundingBox.left.coerceAtLeast(0)
        val top = boundingBox.top.coerceAtLeast(0)
        val right = boundingBox.right.coerceAtMost(bitmap.width)
        val bottom = boundingBox.bottom.coerceAtMost(bitmap.height)
        val width = right - left
        val height = bottom - top

        return if (width > 0 && height > 0) {
            Bitmap.createBitmap(bitmap, left, top, width, height)
        } else {
            Bitmap.createBitmap(
                1,
                1,
                Bitmap.Config.ARGB_8888
            ) // Retorna um bitmap vazio como fallback
        }
    }

    private fun calculateEuclideanDistance(embedding1: FloatArray, embedding2: FloatArray): Float {
        return sqrt(embedding1.indices.sumOf {
            (embedding1[it] - embedding2[it]).toDouble().pow(2)
        }).toFloat()
    }


    private fun drawBoundingBoxAndLabel(
        bitmap: Bitmap,
        faceDistances: List<Pair<Face, Float>>
    ): Bitmap {
        Log.d(TAG, "Entered drawBoundingBoxAndLabel")
        val outputBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(outputBitmap)
        val greenPaint = Paint().apply {
            color = Color.GREEN
            style = Paint.Style.STROKE
            strokeWidth = 4f // Ajuste para melhor visualização
        }
        val redPaint = Paint().apply {
            color = Color.RED
            style = Paint.Style.STROKE
            strokeWidth = 4f // Ajuste para melhor visualização
        }
        val textPaint = Paint().apply {
            color = Color.GREEN
            textSize = 40f // Ajuste para melhor visualização
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        faceDistances.forEach { (face, distance) ->
            val boundingBox = face.boundingBox
            val label = if (distance < THRESHOLD) {
                canvas.drawRect(boundingBox, greenPaint)
                userName
            } else {
                canvas.drawRect(boundingBox, redPaint)
                "Desconhecido"
            }
            Log.d(TAG, "Drawing bounding box for $label with distance $distance")
            canvas.drawText(
                label,
                boundingBox.left.toFloat(),
                boundingBox.top.toFloat() - 10,
                textPaint
            )
        }

        Log.d(TAG, "Completed drawing bounding boxes")
        return outputBitmap
    }

    companion object {
        private const val TAG = "Verification2Fragment"
        private const val INPUT_SIZE = 112
        private const val IMAGE_MEAN = 128.0f
        private const val IMAGE_STD = 128.0f
        private const val OUTPUT_SIZE = 192
        private const val THRESHOLD = 0.78f
    }
}
