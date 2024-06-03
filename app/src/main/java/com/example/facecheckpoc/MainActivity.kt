package com.example.facecheckpoc

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.MenuItem
import android.view.SurfaceView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import com.example.facecheckpoc.data.UserModel
import com.example.facecheckpoc.databinding.ActivityMainBinding
import org.opencv.android.CameraBridgeViewBase
import org.opencv.android.CameraBridgeViewBase.CvCameraViewListener2
import org.opencv.android.JavaCameraView
import org.opencv.android.OpenCVLoader
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfRect
import org.opencv.core.Point
import org.opencv.core.Rect
import org.opencv.core.Scalar
import org.opencv.imgproc.Imgproc
import org.opencv.objdetect.CascadeClassifier
import java.io.File
import java.io.FileOutputStream


class MainActivity : AppCompatActivity(), CvCameraViewListener2 {

    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: UserViewModel
    private lateinit var cameraView: JavaCameraView
    private lateinit var cameraManager: CameraManager
    private lateinit var cameraId: String
    private lateinit var cameraDevice: CameraDevice
    private lateinit var mRGBA: Mat
    private lateinit var mGray: Mat
    private lateinit var faceCascade: CascadeClassifier

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Carregar a biblioteca OpenCV
        if (!OpenCVLoader.initDebug()) {
            Log.e("MainActivity", "Falha ao carregar OpenCV")
            return
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        viewModel = ViewModelProvider(this)[UserViewModel::class.java]
        enableEdgeToEdge()
        setContentView(binding.root)

        observe()

        val actionBar = supportActionBar
        actionBar?.title = getString(R.string.main_title)
        actionBar?.setDisplayHomeAsUpEnabled(true)

        // Inicializar cameraView
        cameraView = findViewById(R.id.camera_view)
        cameraView.visibility = SurfaceView.VISIBLE
        cameraView.setCvCameraViewListener(this)

        // Carregar o classificador em cascata
        val inputStream = resources.openRawResource(R.raw.haarcascade_frontalface_alt2)
        val cascadeDir = getDir("cascade", Context.MODE_PRIVATE)
        val mCascadeFile = File(cascadeDir, "haarcascade_frontalface_alt2.xml")
        val outputStream = FileOutputStream(mCascadeFile)

        val buffer = ByteArray(4096)
        var bytesRead: Int
        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
            outputStream.write(buffer, 0, bytesRead)
        }
        inputStream.close()
        outputStream.close()

        faceCascade = CascadeClassifier(mCascadeFile.absolutePath)
        if (faceCascade.empty()) {
            Log.e("MainActivity", "Failed to load cascade classifier")
            faceCascade = CascadeClassifier()
        } else {
            Log.i("MainActivity", "Loaded cascade classifier from " + mCascadeFile.absolutePath)
        }

        // Configurar a câmera
        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        cameraId = getFrontCameraId() ?: return

        // Solicitar permissão para a câmera, se necessário
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 1)
        } else {
            openCamera()
        }
    }

    private fun getFrontCameraId(): String? {
        try {
            for (id in cameraManager.cameraIdList) {
                val characteristics = cameraManager.getCameraCharacteristics(id)
                if (characteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT) {
                    return id
                }
            }
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
        return null
    }

    private fun openCamera() {
        val handler = Handler(Looper.getMainLooper())
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
            override fun onOpened(camera: CameraDevice) {
                cameraDevice = camera
                try {
                    camera.createCaptureSession(
                        listOf(cameraView.holder.surface),
                        object : CameraCaptureSession.StateCallback() {
                            override fun onConfigured(session: CameraCaptureSession) {
                                val request =
                                    camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                                request.addTarget(cameraView.holder.surface)
                                session.setRepeatingRequest(request.build(), null, null)
                            }

                            override fun onConfigureFailed(session: CameraCaptureSession) {
                                Log.e("Camera", "Failed to configure capture session")
                            }
                        },
                        null
                    )
                } catch (e: Exception) {
                    Log.e("Camera", "Error setting up capture session: ${e.message}")
                }
            }

            override fun onDisconnected(camera: CameraDevice) {
                camera.close()
                cameraDevice = camera
            }

            override fun onError(camera: CameraDevice, error: Int) {
                Log.e("Camera", "Error opening camera: $error")
                camera.close()
                cameraDevice = camera
            }

        }, handler)
    }

    override fun onResume() {
        super.onResume()
        if (::cameraView.isInitialized) {
            cameraView.enableView()
        }
    }

    override fun onPause() {
        super.onPause()
        if (::cameraView.isInitialized) {
            cameraView.disableView()
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressedDispatcher.onBackPressed()
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onCameraViewStarted(width: Int, height: Int) {
        mRGBA = Mat(height, width, CvType.CV_8UC4)
        mGray = Mat(height, width, CvType.CV_8UC1)
    }

    override fun onCameraViewStopped() {
        mRGBA.release()
        mGray.release()
    }

    override fun onCameraFrame(inputFrame: CameraBridgeViewBase.CvCameraViewFrame): Mat {
        mRGBA = inputFrame.rgba()
        mGray = inputFrame.gray()

        val faces = MatOfRect()
        faceCascade.detectMultiScale(mGray, faces)

        for (rect in faces.toArray()) {
            val cpf = getUserCpfByFace(rect)
            val user = viewModel.getUserByCpf(cpf)

            Imgproc.rectangle(
                mRGBA,
                Point(rect.x.toDouble(), rect.y.toDouble()),
                Point((rect.x + rect.width).toDouble(), (rect.y + rect.height).toDouble()),
                if (user != null) Scalar(0.0, 255.0, 0.0) else Scalar(255.0, 0.0, 0.0),
                3
            )

            if (user != null) {
                drawNameOnFace(mRGBA, user.name, rect)
                runOnUiThread {
                    updateUserInterface(user)
                }
            } else {
                runOnUiThread {
                    binding.textName.text = "Unknown"
                    binding.textCpf.text = ""
                }
            }
        }

        return mRGBA
    }

    private fun drawNameOnFace(frame: Mat, userName: String, rect: Rect) {
        val textSize = 1.0
        val textThickness = 2
        val baseline = IntArray(1)
        val fontface = Imgproc.FONT_HERSHEY_SIMPLEX
        val textSize2 = Imgproc.getTextSize(userName, fontface, textSize, textThickness, baseline)
        Imgproc.putText(
            frame,
            userName,
            Point((rect.x + rect.width / 2) - textSize2.width / 2, (rect.y - 10).toDouble()),
            fontface,
            textSize,
            Scalar(0.0, 255.0, 0.0),
            textThickness
        )
    }

    private fun getUserCpfByFace(rect: Rect): String {
        // Implementar a lógica para obter o CPF do usuário com base no rosto detectado.
        // Este exemplo retorna um CPF fixo. Substitua pela sua lógica real.
        return "12345678901"
    }

    private fun observe() {
        viewModel.userModel.observe(this, Observer { user ->
            user?.let { updateUserInterface(it) }
        })
    }

    private fun updateUserInterface(user: UserModel) {
        runOnUiThread {
            binding.textName.text = user.name
            binding.textCpf.text = user.cpf
        }
    }
}


//private fun getUserCpfByFace(rect: Rect, frame: Mat): String {
//    val grayFrame = Mat()
//    Imgproc.cvtColor(frame, grayFrame, Imgproc.COLOR_RGBA2GRAY)
//
//    val faceCascade = CascadeClassifier()
//    val inputStream = resources.openRawResource(R.raw.haarcascade_frontalface_alt2)
//    val file = File(filesDir, "haarcascade_frontalface_alt2.xml")
//    val fileOutputStream = FileOutputStream(file)
//
//    val buffer = ByteArray(4096)
//    var bytesRead: Int
//    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
//        fileOutputStream.write(buffer, 0, bytesRead)
//    }
//    inputStream.close()
//    fileOutputStream.close()
//
//    faceCascade.load(file.absolutePath)
//
//    val faces = MatOfRect()
//    faceCascade.detectMultiScale(grayFrame, faces)
//
//    for (face in faces.toArray()) {
//        if (face.x <= rect.x && face.x + face.width >= rect.x + rect.width &&
//            face.y <= rect.y && face.y + face.height >= rect.y + rect.height
//        ) {
//            // Buscar o CPF do usuário correspondente ao rosto detectado
//            return "12345678901"
//        }
//    }
//
//    return ""
//}
//}
