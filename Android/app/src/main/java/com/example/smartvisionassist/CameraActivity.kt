package com.example.smartvisionassist

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.graphics.YuvImage
import android.graphics.ImageFormat
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import android.widget.Toast

import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat


import java.io.ByteArrayOutputStream

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

import android.speech.tts.TextToSpeech
import java.util.Locale
import android.os.Vibrator
import android.os.VibrationEffect
import android.os.Build

class CameraActivity : AppCompatActivity(),
    TextToSpeech.OnInitListener {

    private lateinit var previewView: PreviewView
    private lateinit var txtStatus: TextView

    private lateinit var cameraExecutor: ExecutorService

    private lateinit var overlayView: OverlayView

    private val REQUEST_CODE = 100

    private lateinit var tts: TextToSpeech

    private var isReady = false

    private var lastSpokenMessage = ""

    private var lastVibrationTime = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera)

        previewView = findViewById(R.id.previewView)
        overlayView = findViewById(R.id.overlayView)
        txtStatus = findViewById(R.id.txtStatus)

        cameraExecutor = Executors.newSingleThreadExecutor()

        tts = TextToSpeech(this, this)



        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                REQUEST_CODE
            )
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build()
            preview.setSurfaceProvider(previewView.surfaceProvider)

            val imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            imageAnalyzer.setAnalyzer(cameraExecutor) { imageProxy ->
                try {
                    val bitmap = imageProxyToBitmap(imageProxy)
                    val resized = Bitmap.createScaledBitmap(bitmap, 640, 640, true)

                    sendImageToServer(resized)

                } catch (e: Exception) {
                    Log.e("AI", "Error: ${e.message}")
                } finally {
                    imageProxy.close()
                }
            }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                this,
                cameraSelector,
                preview,
                imageAnalyzer
            )

        }, ContextCompat.getMainExecutor(this))
    }

    private var isProcessing = false

    private fun sendImageToServer(bitmap: Bitmap) {

        if (isProcessing) return

        isProcessing = true

        try {

            val file = File(cacheDir, "frame.jpg")

            val fos = FileOutputStream(file)

            bitmap.compress(
                Bitmap.CompressFormat.JPEG,
                90,
                fos
            )

            fos.flush()
            fos.close()

            val requestBody =
                MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart(
                        "file",
                        file.name,
                        file.asRequestBody(
                            "image/jpeg".toMediaType()
                        )
                    )
                    .build()

            val request =
                Request.Builder()
                    //.url("http://192.168.1.23:8001/navigate")
                    .url("http://172.20.10.3:8001/navigate")
                    .post(requestBody)
                    .build()

            OkHttpClient().newCall(request)
                .enqueue(object : Callback {

                    override fun onFailure(
                        call: Call,
                        e: java.io.IOException
                    ) {

                        Log.e(
                            "SERVER",
                            "REQUEST FAILED",
                            e
                        )

                        isProcessing = false

                        runOnUiThread {
                            txtStatus.text =
                                e.message
                        }
                    }

                    override fun onResponse(

                        call: Call,
                        response: Response
                    ) {
                        Log.e(
                            "SERVER",
                            "CODE = ${response.code}"
                        )


                        isProcessing = false

                        val json =
                            response.body?.string()
                                ?: return
                        Log.e("SERVER_JSON", json)

                        val detections =
                            mutableListOf<Detection>()

                        val root =
                            org.json.JSONObject(json)

                        val guidance =
                            root.getString("guidance")

                        val risk =
                            root.getString("risk")

                        val array =
                            root.getJSONArray("detections")

                        val scaleX =
                            previewView.width / 640f

                        val scaleY =
                            previewView.height / 640f

                        for (i in 0 until array.length()) {

                            val obj =
                                array.getJSONObject(i)

                            detections.add(
                                Detection(
                                    obj.getDouble("x1").toFloat() * scaleX,
                                    obj.getDouble("y1").toFloat() * scaleY,
                                    obj.getDouble("x2").toFloat() * scaleX,
                                    obj.getDouble("y2").toFloat() * scaleY,
                                    obj.getDouble("confidence").toFloat(),
                                    obj.getInt("class_id"),
                                    obj.getString("class_name")
                                )
                            )
                        }

                        runOnUiThread {

                            txtStatus.text =
                                guidance

                            handleRiskAndGuidance(
                                guidance,
                                risk
                            )

                            overlayView.setDetections(
                                detections
                            )
                        }


                    }
                })

        } catch (e: Exception) {

            isProcessing = false

            runOnUiThread {
                txtStatus.text = e.message
            }
        }
    }

    private fun imageProxyToBitmap(image: ImageProxy): Bitmap {
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

        val imageBytes = out.toByteArray()
        return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
    }



    private fun allPermissionsGranted(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(
            requestCode,
            permissions,
            grantResults
        )

        if (requestCode == REQUEST_CODE) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(this, "Camera permission required", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }


    private fun handleRiskAndGuidance(
        guidance: String,
        risk: String
    ) {

        var message: String? = null

        when (risk) {

            "medium" -> {

                message =
                    "Caution. Obstacle nearby."
            }

            "high" -> {

                message = guidance

                // Short vibration
                if (
                    System.currentTimeMillis()
                    - lastVibrationTime
                    > 2000
                ) {

                    vibrateWarning(250)

                    lastVibrationTime =
                        System.currentTimeMillis()
                }
            }

            "critical" -> {

                message = guidance

                // Long vibration
                if (
                    System.currentTimeMillis()
                    - lastVibrationTime
                    > 2000
                ) {

                    vibrateWarning(800)

                    lastVibrationTime =
                        System.currentTimeMillis()
                }
            }
        }

        if (
            message != null &&
            message != lastSpokenMessage
        ) {

            speak(message)

            lastSpokenMessage = message
        }
    }

    override fun onInit(status: Int) {

        if (status == TextToSpeech.SUCCESS) {

            tts.language = Locale.US
            isReady = true
        }
    }

    private fun speak(text: String) {

        if (isReady) {

            tts.speak(
                text,
                TextToSpeech.QUEUE_FLUSH,
                null,
                null
            )
        }
    }

    private fun vibrateWarning(
        duration: Long
    ) {

        val vibrator =
            getSystemService(VIBRATOR_SERVICE)
                    as Vibrator

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            vibrator.vibrate(
                VibrationEffect.createOneShot(
                    duration,
                    VibrationEffect.DEFAULT_AMPLITUDE
                )
            )

        } else {

            @Suppress("DEPRECATION")
            vibrator.vibrate(duration)
        }
    }


    override fun onDestroy() {

        if (::tts.isInitialized) {

            tts.stop()
            tts.shutdown()
        }

        cameraExecutor.shutdown()

        super.onDestroy()
    }

}