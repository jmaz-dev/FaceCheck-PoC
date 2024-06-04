package com.example.facecheckpoc.verification

import android.Manifest
import android.animation.Animator
import android.content.Context
import android.content.DialogInterface
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import androidx.camera.view.LifecycleCameraController
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.airbnb.lottie.LottieDrawable
import com.example.facecheckpoc.R
import com.example.facecheckpoc.databinding.FragmentVerificationBinding
import com.example.facecheckpoc.utils.setWidthPercent
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.nnapi.NnApiDelegate
import org.tensorflow.lite.support.common.ops.NormalizeOp
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import java.io.FileInputStream
import java.io.IOException
import java.math.RoundingMode
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.text.DecimalFormat
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

@ExperimentalGetImage class VerificationFragment(
    private val image: Bitmap,
    private val resultListener: OnResultListener? = null,
    private val threshold : Float,
    private val defaultFrontCamera: Boolean = true) : DialogFragment() {

    private lateinit var binding: FragmentVerificationBinding
    private lateinit var cameraController: LifecycleCameraController

    private val executor = Executors.newSingleThreadExecutor()
    private var isProcessing = AtomicBoolean(false)
    private var flipX = defaultFrontCamera
    private var cameraSelector: CameraSelector = if (defaultFrontCamera) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
    private var inputSize = 112 //Input size for model
    private var IMAGE_MEAN = 128.0f
    private var IMAGE_STD = 128.0f
    private var OUTPUT_SIZE = 192 //Output size of model
    private var compareEmbedding: FloatArray? = null
    private var modelFile = "mobile_face_net.tflite" //model name
    private var verification: VerificationResult = VerificationResult(image = image.copy(image.config, false))
    private var topVerifications = sortedMapOf<Float, Bitmap>()
    private var minVerifications = 3
    private var faceDetected = false
    private var failedJob: Job? = null
    private var isPaused = false
    private val tfImageBuffer = TensorImage(DataType.UINT8)
    private val isEmulator = isEmulator()
    private val maxVerificationTimems: Long = 10000
    private var startTimeMs: Long = 0
    private var retryCounter = 0
    private val minRetriesForApproval = 1

    // TensorFLow
    private val tfImageProcessor by lazy {
        ImageProcessor.Builder()
            .add(NormalizeOp(IMAGE_MEAN, IMAGE_STD))
            .build()
    }
    private val nnApiDelegate by lazy {
        NnApiDelegate()
    }
    private val tfLite by lazy {
        Interpreter(loadModelFile(requireContext(), modelFile), Interpreter.Options().addDelegate(nnApiDelegate))
    }

    //ML KIT
    private val detector by lazy {
        val highAccuracyOpts: FaceDetectorOptions = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .build()
        FaceDetection.getClient(highAccuracyOpts)
    }

    private val getPermissionResult =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()) {
            if(it){
                bindCamera()
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

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentVerificationBinding.inflate(
            LayoutInflater.from(
                context
            ))

        isCancelable = false

        setCameraText()

        binding.cancelButton.isEnabled = false

        binding.cameraSwitch.setOnClickListener(View.OnClickListener {
            if (cameraSelector == CameraSelector.DEFAULT_BACK_CAMERA &&
                cameraController.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)) {
                cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
                flipX = true
            } else if (cameraSelector == CameraSelector.DEFAULT_FRONT_CAMERA &&
                cameraController.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)) {
                cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                flipX = false
            }

            setCameraText()
            cameraController.cameraSelector = cameraSelector

            failedJob?.cancel()
            verification.verified = false
            verification.score = 0f
            verification.verificationImage = null
            topVerifications.clear()
            faceDetected = false
        })

        binding.tryagainButton.setOnClickListener {
            failedJob?.cancel()
            verification.verified = false
            verification.score = 0f
            verification.verificationImage = null
            topVerifications.clear()
            faceDetected = false
            binding.animation.visibility = View.VISIBLE
            binding.animation.setAnimation(R.raw.scanning_animation_2)
            binding.animation.repeatCount = LottieDrawable.INFINITE
            binding.animation.playAnimation()
            binding.tryagainButton.visibility = View.GONE
            binding.cameraSwitch.visibility = View.GONE
            binding.infoText.text = getString(R.string.verifying_face)
            binding.viewFinder.visibility = View.VISIBLE
            binding.cancelButton.isEnabled = false
            binding.imageView.visibility = View.GONE
            binding.approveButton.visibility = View.GONE
            isPaused = false
            retryCounter++
        }

        binding.cancelButton.setOnClickListener {
            binding.cancelButton.isEnabled = false

            failedJob?.cancel(null)
            verification.verified = false
            verification.score = 0f
            verification.verificationImage = null
            topVerifications.clear()
            isPaused = true

            dismiss()
        }

        binding.approveButton.setOnClickListener {
            binding.approveButton.visibility = View.GONE
            binding.tryagainButton.visibility = View.GONE
            binding.cancelButton.visibility = View.GONE

            val score = topVerifications.firstKey()
            val verificationImage = topVerifications[score]
            verification.verified = true
            verification.score = score
            verification.verificationImage = verificationImage

            showSuccessAnimation(score)
        }

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (ContextCompat.checkSelfPermission(
                requireActivity(), Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            getPermissionResult.launch(Manifest.permission.CAMERA)
        } else {
            bindCamera()
        }

        lifecycleScope.launch {
            generateCompareEmbedding()
        }
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

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        resultListener?.onResult(verification)
    }

    override fun onDestroy() {
        executor.apply {
            shutdown()
            awaitTermination(1000, TimeUnit.MILLISECONDS)
        }

        detector.close()
        tfLite.close()
        nnApiDelegate.close()

        super.onDestroy()
    }

    private fun setCameraText() {
        if (cameraSelector == CameraSelector.DEFAULT_BACK_CAMERA) {
            binding.cameraSwitch.text = getString(R.string.back_camera)
        } else {
            binding.cameraSwitch.text = getString(R.string.front_camera)
        }
    }

    @Throws(IOException::class)
    private fun loadModelFile(context: Context, MODEL_FILE: String): MappedByteBuffer {
        context.assets.openFd(MODEL_FILE).use {
            FileInputStream(it.fileDescriptor).use { f ->
                val fileChannel = f.channel
                val startOffset = it.startOffset
                val declaredLength = it.declaredLength
                return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
            }
        }
    }

    //Bind camera and preview view
    private fun bindCamera() {
        cameraController = LifecycleCameraController(requireContext())
        cameraController.initializationFuture.addListener({
            try {
                if (cameraSelector == CameraSelector.DEFAULT_BACK_CAMERA &&
                    !cameraController.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)) {
                    cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
                    flipX = true
                } else if (cameraSelector == CameraSelector.DEFAULT_FRONT_CAMERA &&
                    !cameraController.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)) {
                    cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                    flipX = false
                }

                setCameraText()

                cameraController.previewTargetSize = CameraController.OutputSize(AspectRatio.RATIO_4_3)
                cameraController.imageAnalysisTargetSize = CameraController.OutputSize(AspectRatio.RATIO_4_3)
                cameraController.imageAnalysisBackpressureStrategy = ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
                cameraController.cameraSelector = cameraSelector
                cameraController.setEnabledUseCases(CameraController.IMAGE_ANALYSIS)
                cameraController.setImageAnalysisAnalyzer(executor, ImageAnalysis.Analyzer { imageProxy ->
                    if (!faceDetected) {
                        faceDetected = true
                        startTimeMs = System.currentTimeMillis()
                        lifecycleScope.launch(Dispatchers.Main) {
                            binding.infoText.text = getString(R.string.detecting_face)
                        }
                    }

                    val mediaImage = imageProxy.image
                    if (isPaused || mediaImage == null) {
                        imageProxy.close()
                        return@Analyzer
                    }

                    var rotation = imageProxy.imageInfo.rotationDegrees
                    if (isEmulator) {
                        rotation -= 90
                    }

                    val frameBmp = rotateBitmap(
                        mediaImage.toBitmap(), rotation, false, false
                    )

                    val image = InputImage.fromBitmap(frameBmp, 0)

                    detector.process(image)
                        .addOnSuccessListener { faces ->
                            Log.d("FaceDetector", "Face count: ${faces.count()}")

                            if (isProcessing.get() || isPaused) {
                                return@addOnSuccessListener
                            }

                            if ((System.currentTimeMillis() - startTimeMs) >= maxVerificationTimems) {
                                showFailedAnimation()
                                return@addOnSuccessListener
                            }

                            val face = faces.maxByOrNull { f -> f.boundingBox.height() }

                            if (face == null) {
                                binding.infoText.text = getString(R.string.detecting_face)
                            } else if (face.boundingBox.left < 0.1 * frameBmp.width || face.boundingBox.right > 0.9 * frameBmp.width ||
                                face.boundingBox.top < 0.1 * frameBmp.height || face.boundingBox.bottom > 0.9 * frameBmp.height
                            ) {
                                binding.infoText.text = getString(R.string.face_not_centered)
                            } else if (face.boundingBox.height() < (0.25 * frameBmp.height)) {
                                binding.infoText.text = getString(R.string.face_too_far)
                            } else if (face.boundingBox.height() > (0.5 * frameBmp.height)) {
                                binding.infoText.text = getString(R.string.face_too_close)
                            } else if (abs(face.headEulerAngleY) > 15 || abs(face.headEulerAngleX) > 15 || abs(face.headEulerAngleZ) > 15) {
                                binding.infoText.text = getString(R.string.face_invalid_angles)
                            } else if (!verification.verified) {
                                isProcessing.set(true)
                                lifecycleScope.launch(Dispatchers.Default) {
                                    processFrame(face, frameBmp)
                                }
                                binding.infoText.text = getString(R.string.verifying_face)
                            }
                        }
                        .addOnFailureListener {
                            val ex = it
                        }
                        .addOnCompleteListener {
                            imageProxy.close()
                        }
                })

                cameraController.bindToLifecycle(this as LifecycleOwner)
                binding.viewFinder.controller = cameraController
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private suspend fun processFrame(face: Face, bmp: Bitmap) = coroutineScope {
        try {
            var verificationImage = bmp.copy(bmp.config, false)

            if (compareEmbedding == null) {
                topVerifications[1001F] = verificationImage
                return@coroutineScope
            }

            var croppedFace: Bitmap = getAlignedFace(bmp, face, inputSize) ?: return@coroutineScope

            if (flipX) {
                croppedFace =
                    rotateBitmap(croppedFace, 0, flipX, false)
            }

            val embedding = runInference(croppedFace)
            var score = euclidDistance(embedding, compareEmbedding!!)

            topVerifications[score] = verificationImage
            if (topVerifications.size > minVerifications) {
                topVerifications.remove(topVerifications.lastKey())
            }

            if (topVerifications.size == minVerifications) {
                score = topVerifications.firstKey()
                verificationImage = topVerifications[topVerifications.firstKey()]

                if (score < threshold && !verification.verified) {
                    verification.verified = true
                    verification.score = score
                    verification.verificationImage = verificationImage

                    withContext(Dispatchers.Main) {
                        showSuccessAnimation(score)
                    }
                } else if (score >= threshold && !verification.verified) {
                    verification.verified = false
                    verification.score = score
                    verification.verificationImage = verificationImage

                    if ((System.currentTimeMillis() - startTimeMs) >= maxVerificationTimems) {
                        withContext(Dispatchers.Main) {
                            showFailedAnimation()
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("ProcessFrame", "Error processing camera frame!", e)
        } finally {
            isProcessing.set(false)
        }
    }

    private fun runInference(bitmap: Bitmap): FloatArray {
        // Process the image in Tensorflow
        val tfImage =  tfImageProcessor.process(tfImageBuffer.apply { load(bitmap) })

        //imgData is input to our model
        val inputArray = arrayOf<Any>(tfImage.buffer)
        val outputMap: MutableMap<Int, Any> = HashMap()
        val embeedings = Array(1) { FloatArray(OUTPUT_SIZE) }
        outputMap[0] = embeedings
        tfLite.runForMultipleInputsOutputs(inputArray, outputMap) //Run model

        return embeedings[0]
    }

    private fun generateCompareEmbedding() {
        val inputImage = InputImage.fromBitmap(image, 0)
        detector.process(inputImage).addOnSuccessListener { faces ->
            if (faces.isNotEmpty()) {
                val face: Face = faces[0]
                getAlignedFace(image, face)?.let {
                    compareEmbedding = runInference(it)
                }
            }
        }
    }

    private fun showSuccessAnimation(score: Float) {
        try {
            isPaused = true

            val df = DecimalFormat("#.####")
            df.roundingMode = RoundingMode.DOWN

            binding.viewFinder.visibility = View.INVISIBLE
            binding.infoText.text = getString(R.string.face_verification_success)
            binding.cameraSwitch.visibility = View.INVISIBLE
            binding.cancelButton.visibility = View.INVISIBLE

            binding.animation.setAnimation(R.raw.verified_animation_2)
            binding.animation.repeatCount = 0
            binding.animation.visibility = View.VISIBLE
            binding.animation.playAnimation()
            binding.animation.removeAllAnimatorListeners()
            binding.animation.addAnimatorListener(object : Animator.AnimatorListener{
                override fun onAnimationStart(p0: Animator) {}
                override fun onAnimationEnd(p0: Animator) {
                    dismiss()
                }
                override fun onAnimationCancel(p0: Animator) {}
                override fun onAnimationRepeat(p0: Animator) {}
            })
        } catch (e: Exception) {}
    }

    private fun showFailedAnimation() {
        isPaused = true

        binding.infoText.text = getString(R.string.face_verification_failed)
        binding.viewFinder.visibility = View.INVISIBLE
        binding.cameraSwitch.visibility = View.GONE
        binding.tryagainButton.visibility = View.VISIBLE
        binding.cancelButton.isEnabled = true

        binding.animation.setAnimation(R.raw.error_animation)
        binding.animation.repeatCount = 0
        binding.animation.visibility = View.VISIBLE
        binding.animation.playAnimation()
        binding.animation.removeAllAnimatorListeners()
        binding.animation.addAnimatorListener(object : Animator.AnimatorListener{
            override fun onAnimationStart(p0: Animator) {}
            override fun onAnimationEnd(p0: Animator) {
                showVerificationApproval()
            }
            override fun onAnimationCancel(p0: Animator) {}
            override fun onAnimationRepeat(p0: Animator) {}
        })
    }

    private fun showVerificationApproval() {
        if (retryCounter < minRetriesForApproval || !topVerifications.any()) {
            return
        }

        var verificationImage = topVerifications[topVerifications.firstKey()] ?: return
        if (flipX) {
            verificationImage = rotateBitmap(verificationImage, 0, flipX, false)
        }

        binding.animation.visibility = View.INVISIBLE
        binding.imageView.setImageBitmap(verificationImage)
        binding.imageView.visibility = View.VISIBLE
        binding.infoText.text = getString(R.string.face_verification_failed_approve)
        binding.approveButton.visibility = View.VISIBLE
    }

    data class VerificationResult(
        var verified: Boolean = false,
        var score: Float = 0f,
        var image: Bitmap? = null,
        var verificationImage: Bitmap? = null
    )

    interface OnResultListener {
        fun onResult(verification: VerificationResult)
    }
}