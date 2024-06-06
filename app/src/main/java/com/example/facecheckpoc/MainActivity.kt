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
import com.example.facecheckpoc.verification.Verification2Fragment
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


class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: UserViewModel


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)


        binding = ActivityMainBinding.inflate(layoutInflater)
        viewModel = ViewModelProvider(this)[UserViewModel::class.java]
        enableEdgeToEdge()
        setContentView(binding.root)

        binding.verificationButton.setOnClickListener {
            navigateToVerificationFragment()
        }


    }

    private fun navigateToVerificationFragment() {
        // Crie uma instância da Verification2Fragment
        val verificationFragment = Verification2Fragment()

        // Adicione a fragment ao gerenciador de fragments
        supportFragmentManager.beginTransaction()
            .add(R.id.fragment_container, verificationFragment)
            .commit()
    }

}


