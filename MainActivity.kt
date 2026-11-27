package com.smoothcam.pro

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.MediaStore
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    private lateinit var previewView: PreviewView
    private lateinit var status: TextView
    private lateinit var shutter: Button
    private lateinit var modeButton: Button
    private lateinit var qualityButton: Button
    private lateinit var cameraExecutor: ExecutorService

    private var cameraProvider: ProcessCameraProvider? = null
    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var activeRecording: Recording? = null
    private var camera: Camera? = null
    private var videoMode = false
    private var useUhd = false
    private var isRecording = false

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            val cameraGranted = result[Manifest.permission.CAMERA] == true
            if (cameraGranted) startCamera()
            else showStatus("Camera permission is required.")
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cameraExecutor = Executors.newSingleThreadExecutor()
        buildInterface()
        if (hasCameraPermission()) startCamera() else requestPermissions()
    }

    private fun buildInterface() {
        val base = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF080B10.toInt())
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(20, 16, 20, 12)
        }
        val brand = TextView(this).apply {
            text = "SMOOTHCAM  /  PRO"
            textSize = 16f
            setTextColor(0xFFC6F36A.toInt())
            letterSpacing = 0.08f
        }
        status = TextView(this).apply {
            text = "Starting camera…"
            textSize = 11f
            setTextColor(0xFFB4BCC8.toInt())
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
        }
        header.addView(brand, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(status, LinearLayout.LayoutParams(0, -2, 1f))
        base.addView(header)

        previewView = PreviewView(this).apply {
            implementationMode = PreviewView.ImplementationMode.PERFORMANCE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
        base.addView(previewView, LinearLayout.LayoutParams(-1, 0, 1f))

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 14, 16, 24)
        }
        val topControls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        modeButton = makeButton("PHOTO")
        qualityButton = makeButton("1080p")
        topControls.addView(modeButton, LinearLayout.LayoutParams(0, 48, 1f).apply { marginEnd = 8 })
        topControls.addView(qualityButton, LinearLayout.LayoutParams(0, 48, 1f).apply { marginStart = 8 })
        controls.addView(topControls)

        shutter = makeButton("TAKE PHOTO").apply {
            textSize = 16f
            setBackgroundColor(0xFFC6F36A.toInt())
            setTextColor(0xFF10140A.toInt())
        }
        val shutterParams = LinearLayout.LayoutParams(-1, 58).apply {
            topMargin = 14
        }
        controls.addView(shutter, shutterParams)

        val hint = TextView(this).apply {
            text = "Stability-first build • Rear camera • Saves to Gallery"
            textSize = 11f
            gravity = Gravity.CENTER
            setTextColor(0xFF7E8998.toInt())
            setPadding(4, 12, 4, 0)
        }
        controls.addView(hint)
        base.addView(controls)
        setContentView(base)

        modeButton.setOnClickListener {
            if (isRecording) return@setOnClickListener
            videoMode = !videoMode
            modeButton.text = if (videoMode) "VIDEO" else "PHOTO"
            shutter.text = if (videoMode) "START RECORDING" else "TAKE PHOTO"
            bindUseCases()
        }
        qualityButton.setOnClickListener {
            if (isRecording) return@setOnClickListener
            useUhd = !useUhd
            qualityButton.text = if (useUhd) "4K*" else "1080p"
            bindUseCases()
        }
        shutter.setOnClickListener {
            if (videoMode) {
                if (isRecording) stopRecording() else startRecording()
            } else takePhoto()
        }
    }

    private fun makeButton(label: String) = Button(this).apply {
        text = label
        isAllCaps = false
        textSize = 13f
        setTextColor(0xFFF2F5F8.toInt())
        setBackgroundColor(0xFF202833.toInt())
    }

    private fun hasCameraPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED

    private fun requestPermissions() {
        permissionLauncher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
    }

    private fun startCamera() {
        showStatus("Preparing camera…")
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            try {
                cameraProvider = future.get()
                bindUseCases()
            } catch (e: Exception) {
                showStatus("Camera unavailable")
                toast("Could not initialise camera: ${e.localizedMessage}")
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun bindUseCases() {
        val provider = cameraProvider ?: return
        try {
            provider.unbindAll()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            val capture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .setJpegQuality(95)
                .build()
            imageCapture = capture

            val quality = if (useUhd) Quality.UHD else Quality.FHD
            val recorder = Recorder.Builder()
                .setQualitySelector(
                    QualitySelector.fromOrderedList(
                        listOf(quality, Quality.FHD, Quality.HD, Quality.SD),
                        androidx.camera.video.FallbackStrategy.lowerQualityOrHigherThan(Quality.SD)
                    )
                )
                .build()
            val video = VideoCapture.withOutput(recorder)
            videoCapture = video

            // Keep the first release conservative: preview + photo + video are
            // attempted together. Some devices reject this stream combination.
            camera = provider.bindToLifecycle(
                this, CameraSelector.DEFAULT_BACK_CAMERA, preview, capture, video
            )
            showStatus(if (useUhd) "Camera ready • 4K requested" else "Camera ready • 1080p")
        } catch (first: Exception) {
            // Fallback to the lighter preview + selected capture path.
            try {
                provider.unbindAll()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                if (videoMode) {
                    val recorder = Recorder.Builder()
                        .setQualitySelector(QualitySelector.from(Quality.FHD))
                        .build()
                    videoCapture = VideoCapture.withOutput(recorder)
                    camera = provider.bindToLifecycle(
                        this, CameraSelector.DEFAULT_BACK_CAMERA, preview, videoCapture!!
                    )
                } else {
                    imageCapture = ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .setJpegQuality(95)
                        .build()
                    camera = provider.bindToLifecycle(
                        this, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture!!
                    )
                }
                showStatus("Camera ready • compatibility mode")
            } catch (second: Exception) {
                showStatus("Unsupported camera configuration")
                toast("This camera mode is not supported on this device.")
            }
        }
    }

    private fun takePhoto() {
        val capture = imageCapture ?: run {
            toast("Photo capture is not ready.")
            return
        }
        val name = "SmoothCam_${timestamp()}.jpg"
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/SmoothCamPro")
        }
        val output = ImageCapture.OutputFileOptions.Builder(
            contentResolver, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values
        ).build()

        shutter.isEnabled = false
        capture.takePicture(output, cameraExecutor, object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(result: ImageCapture.OutputFileResults) {
                runOnUiThread {
                    shutter.isEnabled = true
                    showStatus("Photo saved")
                    toast("Photo saved to Gallery")
                }
            }
            override fun onError(exception: ImageCaptureException) {
                runOnUiThread {
                    shutter.isEnabled = true
                    showStatus("Photo failed")
                    toast("Capture failed: ${exception.message}")
                }
            }
        })
    }

    private fun startRecording() {
        val capture = videoCapture ?: run {
            toast("Video capture is not ready.")
            return
        }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "SmoothCam_${timestamp()}.mp4")
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/SmoothCamPro")
        }
        val output = MediaStoreOutputOptions.Builder(
            contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        ).setContentValues(values).build()

        try {
            activeRecording = capture.output
                .prepareRecording(this, output)
                .withAudioEnabled()
                .start(ContextCompat.getMainExecutor(this)) { event ->
                    when (event) {
                        is VideoRecordEvent.Start -> {
                            isRecording = true
                            shutter.text = "STOP RECORDING"
                            shutter.setBackgroundColor(0xFFFF6B6B.toInt())
                            showStatus("Recording")
                        }
                        is VideoRecordEvent.Finalize -> {
                            isRecording = false
                            activeRecording = null
                            shutter.text = "START RECORDING"
                            shutter.setBackgroundColor(0xFFC6F36A.toInt())
                            showStatus(if (event.hasError()) "Recording error" else "Video saved")
                            if (!event.hasError()) toast("Video saved to Gallery")
                        }
                    }
                }
        } catch (e: Exception) {
            toast("Could not start recording: ${e.localizedMessage}")
        }
    }

    private fun stopRecording() {
        activeRecording?.stop()
        showStatus("Saving video…")
    }

    private fun timestamp() =
        SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(System.currentTimeMillis())

    private fun showStatus(message: String) {
        runOnUiThread { status.text = message }
    }

    private fun toast(message: String) =
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    override fun onDestroy() {
        activeRecording?.stop()
        cameraExecutor.shutdown()
        super.onDestroy()
    }
}
