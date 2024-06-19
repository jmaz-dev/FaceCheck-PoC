package com.example.facecheckpoc.verification

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.*
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
import androidx.camera.core.Camera
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.airbnb.lottie.LottieAnimationView
import com.example.facecheckpoc.R
import com.example.facecheckpoc.UserFormActivity
import com.example.facecheckpoc.databinding.FragmentVerification2Binding
import com.example.facecheckpoc.utils.getAlignedFace
import com.example.facecheckpoc.utils.rotateBitmap
import com.example.facecheckpoc.utils.setWidthPercent
import com.example.facecheckpoc.utils.toBitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.*
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.nnapi.NnApiDelegate
import org.tensorflow.lite.support.common.ops.NormalizeOp
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import java.io.*
import java.nio.*
import java.nio.channels.FileChannel
import java.util.concurrent.CountDownLatch
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

@ExperimentalGetImage
class Verification2Fragment : DialogFragment() {
    private lateinit var binding: FragmentVerification2Binding
    private lateinit var userFace: ByteArray
    private lateinit var storedEmbedding: FloatArray
    private lateinit var userName: String
    private lateinit var cameraProvider: ProcessCameraProvider
    private lateinit var camera: Camera
    private lateinit var faceDetector: FaceDetector
    private lateinit var interpreter: Interpreter
    private lateinit var animationView: LottieAnimationView
    private val maxVerificationTimems: Long = 10000
    private var startTimeMs: Long = 0
    private val nnApiDelegate by lazy {
        NnApiDelegate()
    }

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

    override fun getTheme() = R.style.Verification_Dialog

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
        binding = FragmentVerification2Binding.inflate(LayoutInflater.from(context))
        binding.cancelButton.setOnClickListener { dismiss() }
        binding.cancelButtonView.setOnClickListener { dismiss() }

        configureFaceDetector()

        interpreter = Interpreter(
            loadModelFile("mobile_face_net.tflite"),
            Interpreter.Options().addDelegate(nnApiDelegate)
        )

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
        // Transform storedEmbeding to bitmap and preprocess the bitmap
        generateCompareEmbedding { embedding ->
            if (embedding.isNotEmpty()) {
                storedEmbedding = embedding
            } else {
                Log.e(TAG, "Erro ao gerar embedding")
            }
        }

    }

    override fun onDestroyView() {
        super.onDestroyView()
        faceDetector.close()
        faceDetector.close()
        cameraProvider.unbindAll()
    }

    override fun onResume() {
        super.onResume()

        val dm = Resources.getSystem().displayMetrics
        val widthDp = dm.widthPixels / dm.density
        if (dm.heightPixels > dm.widthPixels && widthDp < 500) {
            setWidthPercent(95)
        } else if (dm.heightPixels > dm.widthPixels) {
            setWidthPercent(85)
        } else {
            setWidthPercent(40)
        }
    }

    private fun generateCompareEmbedding(callback: (FloatArray) -> Unit) {
        if (userFace.isNotEmpty()) {
            val storedBitmap = BitmapFactory.decodeByteArray(userFace, 0, userFace.size)
            val inputImage = InputImage.fromBitmap(storedBitmap, 0)
            var alignedFace: Bitmap? = null

            Log.d("ImageSize", inputImage.width.toString() + "x" + inputImage.height)

            faceDetector.process(inputImage)
                .addOnSuccessListener { faces ->
                    val face = faces.maxByOrNull { it.boundingBox.height() }
                    if (face != null) {
                        alignedFace = getAlignedFace(storedBitmap, face, INPUT_SIZE)
                    }
                    Log.d(TAG, "AlignedFace: $alignedFace")
                    if (alignedFace != null) {
                        Log.d(TAG, "AlignedFace: $alignedFace")

                        val preProcessStored = preprocessImage(alignedFace!!)
                        val embedding = runModel(preProcessStored)
                        callback(embedding)
                    } else {
                        Log.e(TAG, "Erro ao detectar e alinhar o rosto na imagem armazenada")
                        callback(floatArrayOf())
                    }
                }
                .addOnFailureListener {
                    Log.e(TAG, "Erro ao detectar o rosto")
                    callback(floatArrayOf())
                }
        } else {
            Log.e(TAG, "Erro de tamanho de embedding")
            callback(floatArrayOf())
        }
    }

    private fun configureFaceDetector() {
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
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
        startTimeMs = System.currentTimeMillis()
        binding.textInfo.text = getString(R.string.detecting_face)

        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
            bindCameraUseCases()
        }, ContextCompat.getMainExecutor(requireContext()))
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


    private fun processImageProxy(imageProxy: ImageProxy) {
        lifecycleScope.launch(Dispatchers.IO) {
            val rotationDegrees = imageProxy.imageInfo.rotationDegrees
            val image = imageProxy.image ?: run { imageProxy.close(); return@launch }
            val inputImage = InputImage.fromMediaImage(image, rotationDegrees)
            faceDetector.process(inputImage)
                .addOnSuccessListener { faces ->
                    lifecycleScope.launch(Dispatchers.Main) {

                        if ((System.currentTimeMillis() - startTimeMs) >= maxVerificationTimems) {
                            Log.d(TAG, "Timeout reached")
                            stopCameraPreview()
                            return@launch
                        }

                        val rotation = imageProxy.imageInfo.rotationDegrees

                        val frameBmp = rotateBitmap(
                            image.toBitmap(), rotation, false, false
                        )

                        val face = faces.maxByOrNull { f -> f.boundingBox.height() }

                        if (face == null) {
                            binding.textInfo.text = getString(R.string.detecting_face)
                        } else if (face.boundingBox.left < 0.1 * frameBmp.width || face.boundingBox.right > 0.9 * frameBmp.width ||
                            face.boundingBox.top < 0.1 * frameBmp.height || face.boundingBox.bottom > 0.9 * frameBmp.height
                        ) {
                            binding.textInfo.text = getString(R.string.face_not_centered)
                        } else if (face.boundingBox.height() < (0.25 * frameBmp.height)) {
                            binding.textInfo.text = getString(R.string.face_too_far)
                        } else if (face.boundingBox.height() > (0.5 * frameBmp.height)) {
                            binding.textInfo.text = getString(R.string.face_too_close)
                        } else if (abs(face.headEulerAngleY) > 15 || abs(face.headEulerAngleX) > 15 || abs(
                                face.headEulerAngleZ
                            ) > 15
                        ) {
                            binding.textInfo.text = getString(R.string.face_invalid_angles)
                        } else {
                            binding.textInfo.text = getString(R.string.verifying_face)

                            val alignedFace = getAlignedFace(frameBmp, face, INPUT_SIZE)
                            Log.d(TAG, "AlignedFace: $alignedFace")
                            if (alignedFace != null) {
                                Log.d(TAG, "AlignedFace: $alignedFace")
                                val preprocessedFaceBitmap = preprocessImage(alignedFace)
                                val cameraEmbedding = runModel(preprocessedFaceBitmap)
                                processImageWithTFLite(preprocessedFaceBitmap, cameraEmbedding)
                            } else {
                                Log.e(TAG, "Erro ao alinhar o rosto")
                            }
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

    private fun processImageWithTFLite(cameraEmbeddingBitmap: Bitmap, cameraEmbedding: FloatArray) {
        if (cameraEmbedding.isNotEmpty()) {
            val distance =
                calculateEuclideanDistance(storedEmbedding, cameraEmbedding)
            Log.d(TAG, "Distance: $distance")
            if (distance < THRESHOLD) {
                detectAndDrawBoundingBox(cameraEmbeddingBitmap, userName)
                binding.textInfo.text = "Rosto verificado com sucesso"
            }
        } else {
            Log.e(TAG, "Erro de tamanho de embedding")
            binding.textInfo.text = "Erro de tamanho de embedding"
        }
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

    private fun detectAndDrawBoundingBox(bitmap: Bitmap, label: String) {
        val inputImage = InputImage.fromBitmap(bitmap, 0)
        faceDetector.process(inputImage)
            .addOnSuccessListener { faces ->
                if (faces.isEmpty()) {
                    binding.textInfo.text = "Não foi possível detectar o rosto"
                    return@addOnSuccessListener
                }
                val face = faces.first()
                val outputBitmap = drawBoundingBoxAndLabel(bitmap, face, label)
                cameraProvider.unbindAll()
                binding.textInfo.visibility = View.INVISIBLE
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


    private fun calculateEuclideanDistance(embedding1: FloatArray, embedding2: FloatArray): Float {
        return sqrt(embedding1.indices.sumOf {
            (embedding1[it] - embedding2[it]).toDouble().pow(2)
        }).toFloat()
    }

    private fun drawBoundingBoxAndLabel(bitmap: Bitmap, face: Face, label: String): Bitmap {
        val outputBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(outputBitmap)
        val paint = Paint().apply {
            color = Color.GREEN
            style = Paint.Style.STROKE
            strokeWidth = 1f
        }
        val textPaint = Paint().apply {
            color = Color.GREEN
            textSize = 10f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        val boundingBox = face.boundingBox
        canvas.drawRect(boundingBox, paint)
        canvas.drawText(
            label,
            boundingBox.left.toFloat(),
            boundingBox.top.toFloat() - 10,
            textPaint
        )

        return outputBitmap
    }

    companion object {
        private const val TAG = "Verification2Fragment"
        private const val INPUT_SIZE = 112
        private const val IMAGE_MEAN = 128.0f
        private const val IMAGE_STD = 128.0f
        private const val OUTPUT_SIZE = 192
        private const val THRESHOLD = 0.8f
    }
}
