package com.example.facecheckpoc

import org.opencv.android.JavaCameraView
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import org.opencv.android.CameraBridgeViewBase
import android.hardware.camera2.CameraManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.MenuItem
import android.widget.FrameLayout
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import com.example.facecheckpoc.data.UserModel
import com.example.facecheckpoc.databinding.ActivityMainBinding
import org.opencv.core.Mat
import org.opencv.core.MatOfRect
import org.opencv.core.Point
import org.opencv.core.Rect
import org.opencv.core.Scalar
import org.opencv.imgproc.Imgproc
import org.opencv.objdetect.CascadeClassifier


class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private lateinit var viewModel: UserViewModel
    private lateinit var cameraView: JavaCameraView
    private lateinit var cameraManager: CameraManager
    private lateinit var cameraId: String
    private lateinit var cameraDevice: CameraDevice
    private lateinit var cameraCharacteristics: CameraCharacteristics

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        viewModel = ViewModelProvider(this).get(UserViewModel::class.java)
        enableEdgeToEdge()
        setContentView(binding.root)

        observe()

        val actionBar = supportActionBar
        actionBar?.setTitle(getString(R.string.main_title))
        actionBar?.setDisplayHomeAsUpEnabled(true)

        // Inflar o layout
        val cameraFrame = findViewById<FrameLayout>(R.id.camera_frame)
        cameraView = JavaCameraView(this, null)
        cameraFrame.addView(cameraView)

        // Configurar a câmera
        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        cameraId = cameraManager.cameraIdList[0]
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
                camera.createCaptureSession(
                    listOf(cameraView.holder.surface),
                    object : CameraCaptureSession.StateCallback() {
                        override fun onConfigured(session: CameraCaptureSession) {
                            val request = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                            request.addTarget(cameraView.holder.surface)
                            session.setRepeatingRequest(request.build(), null, null)
                        }

                        override fun onConfigureFailed(session: CameraCaptureSession) {
                            Log.e("Camera", "Failed to configure capture session")
                        }
                    },
                    null
                )
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

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressedDispatcher.onBackPressed()
        }
        return super.onOptionsItemSelected(item)
    }

    private fun detectFaces(frame: Mat) {
        val grayFrame = Mat()
        Imgproc.cvtColor(frame, grayFrame, Imgproc.COLOR_RGBA2GRAY)
        val faces = MatOfRect()
        val faceCascade = CascadeClassifier()
        faceCascade.detectMultiScale(grayFrame, faces)
        for (rect in faces.toArray()) {
            val userName = getUserNameByFace(rect)
            if (userName != "UNKNOWN") {
                Imgproc.rectangle(
                    frame,
                    Point(rect.x.toDouble(), rect.y.toDouble()),
                    Point((rect.x + rect.width).toDouble(), (rect.y + rect.height).toDouble()),
                    Scalar(0.0, 255.0, 0.0),
                    3
                )
            } else {
                Imgproc.rectangle(
                    frame,
                    Point(rect.x.toDouble(), rect.y.toDouble()),
                    Point((rect.x + rect.width).toDouble(), (rect.y + rect.height).toDouble()),
                    Scalar(255.0, 0.0, 0.0),
                    3
                )
            }
            drawNameOnFace(frame, userName, rect)
        }
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

    private fun getUserNameByFace(rect: Rect): String {
        val cpf = getUserCpfByFace(rect)
        val user = viewModel.getUserByCpf(cpf)
        return user?.name ?: "UNKNOWN"
    }

    private fun observe() {
        viewModel.userModel.observe(this, Observer { user ->
            user?.let { updateUserInterface(it) }
        })
    }

    private fun updateUserInterface(user: UserModel) {
        // Atualizar a interface do usuário com as informações do usuário
    }

    private fun getUserCpfByFace(rect: Rect): String {
        // Implementar lógica para obter o CPF com base no rosto detectado
        // Você pode usar o rosto detectado para buscar o CPF no banco de dados
        // ou gerar um CPF fictício para fins de demonstração
        return "12345678901"
    }
}
