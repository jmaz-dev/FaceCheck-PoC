package com.example.facecheckpoc.utils

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.media.Image
import android.util.Log
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceLandmark
import org.opencv.android.Utils
import org.opencv.calib3d.Calib3d
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Rect
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

/**
 * Call this method (in onActivityCreated or later) to set
 * the width of the dialog to a percentage of the current
 * screen width.
 */
fun DialogFragment.setWidthPercent(percentage: Int) {
    val percent = percentage.toFloat() / 100
    val dm = Resources.getSystem().displayMetrics
    val rect = dm.run { Rect(0, 0, widthPixels, heightPixels) }
    val percentWidth = rect.width * percent
    dialog?.window?.setLayout(percentWidth.toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
}

fun Image.yuvToRgba(): Mat {
    val rgbaMat = Mat()

    if (format == ImageFormat.YUV_420_888
        && planes.size == 3
    ) {

        val chromaPixelStride = planes[1].pixelStride

        if (chromaPixelStride == 2) { // Chroma channels are interleaved
            assert(planes[0].pixelStride == 1)
            assert(planes[2].pixelStride == 2)
            val yPlane = planes[0].buffer
            val uvPlane1 = planes[1].buffer
            val uvPlane2 = planes[2].buffer
            val yMat = Mat(height, width, CvType.CV_8UC1, yPlane)
            val uvMat1 = Mat(height / 2, width / 2, CvType.CV_8UC2, uvPlane1)
            val uvMat2 = Mat(height / 2, width / 2, CvType.CV_8UC2, uvPlane2)
            val addrDiff = uvMat2.dataAddr() - uvMat1.dataAddr()
            if (addrDiff > 0) {
                assert(addrDiff == 1L)
                Imgproc.cvtColorTwoPlane(yMat, uvMat1, rgbaMat, Imgproc.COLOR_YUV2RGBA_NV12)
            } else {
                assert(addrDiff == -1L)
                Imgproc.cvtColorTwoPlane(yMat, uvMat2, rgbaMat, Imgproc.COLOR_YUV2RGBA_NV21)
            }
        } else { // Chroma channels are not interleaved
            val yuvBytes = ByteArray(width * (height + height / 2))
            val yPlane = planes[0].buffer
            val uPlane = planes[1].buffer
            val vPlane = planes[2].buffer

            yPlane.get(yuvBytes, 0, width * height)

            val chromaRowStride = planes[1].rowStride
            val chromaRowPadding = chromaRowStride - width / 2

            var offset = width * height
            if (chromaRowPadding == 0) {
                // When the row stride of the chroma channels equals their width, we can copy
                // the entire channels in one go
                uPlane.get(yuvBytes, offset, width * height / 4)
                offset += width * height / 4
                vPlane.get(yuvBytes, offset, width * height / 4)
            } else {
                // When not equal, we need to copy the channels row by row
                for (i in 0 until height / 2) {
                    uPlane.get(yuvBytes, offset, width / 2)
                    offset += width / 2
                    if (i < height / 2 - 1) {
                        uPlane.position(uPlane.position() + chromaRowPadding)
                    }
                }
                for (i in 0 until height / 2) {
                    vPlane.get(yuvBytes, offset, width / 2)
                    offset += width / 2
                    if (i < height / 2 - 1) {
                        vPlane.position(vPlane.position() + chromaRowPadding)
                    }
                }
            }

            val yuvMat = Mat(height + height / 2, width, CvType.CV_8UC1)
            yuvMat.put(0, 0, yuvBytes)
            Imgproc.cvtColor(yuvMat, rgbaMat, Imgproc.COLOR_YUV2RGBA_I420, 4)
        }
    }

    return rgbaMat
}

fun Image.toBitmap(): Bitmap {
    val mat = this.yuvToRgba()
    val bmp = Bitmap.createBitmap(mat.cols(), mat.rows(), Bitmap.Config.ARGB_8888)
    Utils.matToBitmap(mat, bmp)

    return bmp
}

fun rotateBitmap(
    bitmap: Bitmap, rotationDegrees: Int, flipX: Boolean, flipY: Boolean
): Bitmap {
    val mat = Mat()
    Utils.bitmapToMat(bitmap, mat)

    if (flipX && flipY) {
        Core.flip(mat, mat, -1)
    } else if (flipX) {
        Core.flip(mat, mat, 1)
    } else if (flipY) {
        Core.flip(mat, mat, 0)
    }

    val rotCode = when (rotationDegrees) {
        0 -> -1
        90 -> Core.ROTATE_90_CLOCKWISE
        180 -> Core.ROTATE_180
        270 -> Core.ROTATE_90_COUNTERCLOCKWISE
        else -> throw IllegalArgumentException("Invalid rotation degrees")
    }
    if (rotCode != -1) {
        Core.rotate(mat, mat, rotCode)
    }

    val dstBitmap = Bitmap.createBitmap(mat.cols(), mat.rows(), Bitmap.Config.ARGB_8888)
    Utils.matToBitmap(mat, dstBitmap)

    return dstBitmap
}

fun Bitmap.scale(maxSize: Int): Bitmap {
    val outWidth: Int
    val outHeight: Int
    val inWidth: Int = width
    val inHeight: Int = height

    if (inWidth > inHeight) {
        outWidth = maxSize
        outHeight = (inHeight * maxSize) / inWidth
    } else {
        outHeight = maxSize
        outWidth = (inWidth * maxSize) / inHeight
    }

    return Bitmap.createScaledBitmap(this, outWidth, outHeight, false)
}

fun getAlignedFace(bitmap: Bitmap, face: Face, inputSize: Int): Bitmap? {
    try {
        val padding = 0.0f

        val defaultShape = floatArrayOf(
            0.3419f, 0.4615f,
            0.6565f, 0.4598f,
            0.5002f, 0.6305f,
            0.3709f, 0.8247f,
            0.6315f, 0.8232f
        )

        val leftEye = face.getLandmark(FaceLandmark.LEFT_EYE)
        val rightEye = face.getLandmark(FaceLandmark.RIGHT_EYE)
        val nose = face.getLandmark(FaceLandmark.NOSE_BASE)
        val leftMouth = face.getLandmark(FaceLandmark.MOUTH_LEFT)
        val rightMouth = face.getLandmark(FaceLandmark.MOUTH_RIGHT)

        if (leftEye == null || rightEye == null || nose == null || leftMouth == null || rightMouth == null) {
            Log.e("AlignedFace", "Failed to detect face landmarks.")
            return null
        }

        val landmarks = floatArrayOf(
            leftEye.position.x, leftEye.position.y,
            rightEye.position.x, rightEye.position.y,
            nose.position.x, nose.position.y,
            leftMouth.position.x, leftMouth.position.y,
            rightMouth.position.x, rightMouth.position.y
        )

        for (i in defaultShape.indices) {
            defaultShape[i] = defaultShape[i] * inputSize + inputSize * (padding / 2)
        }

        val src = MatOfPoint2f(
            Point(landmarks[0].toDouble(), landmarks[1].toDouble()),
            Point(landmarks[2].toDouble(), landmarks[3].toDouble()),
            Point(landmarks[4].toDouble(), landmarks[5].toDouble()),
            Point(landmarks[6].toDouble(), landmarks[7].toDouble()),
            Point(landmarks[8].toDouble(), landmarks[9].toDouble())
        )

        val dst = MatOfPoint2f(
            Point(defaultShape[0].toDouble(), defaultShape[1].toDouble()),
            Point(defaultShape[2].toDouble(), defaultShape[3].toDouble()),
            Point(defaultShape[4].toDouble(), defaultShape[5].toDouble()),
            Point(defaultShape[6].toDouble(), defaultShape[7].toDouble()),
            Point(defaultShape[8].toDouble(), defaultShape[9].toDouble())
        )

        val affineMatrix = Calib3d.estimateAffine2D(src, dst)

        val rgbMat = Mat()
        Utils.bitmapToMat(bitmap, rgbMat)

        val alignFace = Mat()
        Imgproc.warpAffine(
            rgbMat,
            alignFace,
            affineMatrix,
            Size(inputSize.toDouble(), inputSize.toDouble()),
            Imgproc.INTER_CUBIC
        )

        val alignedBitmap =
            Bitmap.createBitmap(alignFace.cols(), alignFace.rows(), Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(alignFace, alignedBitmap)

        return alignedBitmap
    } catch (e: Exception) {
        Log.e("AlignedFace", "Exception in getAlignedFace: ${e.message}")
        return null
    }
}
