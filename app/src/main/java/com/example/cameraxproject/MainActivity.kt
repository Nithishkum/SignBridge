package com.example.cameraxproject

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.*
import android.os.*
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.google.android.material.button.MaterialButton
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var viewFinder: PreviewView
    private lateinit var overlayImageView: ImageView
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var handLandmarker: HandLandmarker

    // 🔥 Camera Control
    private var cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

    // 🔥 AI Variables
    private lateinit var tflite: Interpreter
    private lateinit var labels: List<String>

    private var inputSourceWidth = 0f
    private var inputSourceHeight = 0f
    private var bitmapBuffer: Bitmap? = null
    private var canvasBuffer: Canvas? = null

    // Paints
    private val textPaint = Paint().apply {
        color = Color.GREEN
        textSize = 120f
        isFakeBoldText = true
        textAlign = Paint.Align.CENTER
        setShadowLayer(10f, 0f, 0f, Color.BLACK)
    }

    // ================== PERMISSION ==================
    private val reqPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                setupHandLandmarker()
                startCamera()
            } else {
                Toast.makeText(this, "Camera Permission Denied", Toast.LENGTH_LONG).show()
            }
        }

    // ================== ON CREATE ==================
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        // 1. Initialize Views
        viewFinder = findViewById(R.id.viewFinder)
        overlayImageView = findViewById(R.id.overlayImageView)

        val startScreen = findViewById<android.view.ViewGroup>(R.id.startScreenLayout)
        val startButton = findViewById<Button>(R.id.startButton)
        val loadingText = findViewById<TextView>(R.id.loadingText)
        val btnExit = findViewById<TextView>(R.id.btnExit)
        val btnSwitchCamera = findViewById<MaterialButton>(R.id.btnSwitchCamera)

        cameraExecutor = Executors.newSingleThreadExecutor()

        // 2. Load AI Models
        try {
            labels = loadLabels()
            tflite = Interpreter(loadModelFile())
        } catch (e: Exception) {
            Log.e("AI", "Error loading model", e)
            Toast.makeText(this, "Error loading AI Model", Toast.LENGTH_SHORT).show()
        }

        // 3. Setup Buttons & UI Logic

        // Ensure Flip Button is HIDDEN initially
        btnSwitchCamera.visibility = View.GONE

        // Handle Switch Camera Button Click
        btnSwitchCamera.setOnClickListener {
            toggleCamera()
        }

        // Simulate "loading" then show Start button
        Handler(Looper.getMainLooper()).postDelayed({
            loadingText?.setTextColor(Color.GREEN)
            startButton?.visibility = View.VISIBLE
        }, 500)

        // Start Button Logic
        startButton?.setOnClickListener {
            // Fade out start screen
            startScreen.animate().alpha(0f).setDuration(500).withEndAction {
                startScreen.visibility = View.GONE

                // SHOW the Flip Camera button now
                btnSwitchCamera.visibility = View.VISIBLE

                // Start Camera
                checkPermissionAndStart()
            }
        }

        // Exit Button Logic
        btnExit?.setOnClickListener {
            finishAffinity()
        }
    }

    // ================== AI FILES ==================
    private fun loadLabels(): List<String> {
        return assets.open("labels.txt").bufferedReader().readLines()
    }

    private fun loadModelFile(): MappedByteBuffer {
        val fileDescriptor = assets.openFd("sign_language.tflite")
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val channel = inputStream.channel
        return channel.map(
            FileChannel.MapMode.READ_ONLY,
            fileDescriptor.startOffset,
            fileDescriptor.declaredLength
        )
    }

    // ================== CAMERA LOGIC ==================
    private fun checkPermissionAndStart() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            setupHandLandmarker()
            startCamera()
        } else {
            reqPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun toggleCamera() {
        // Switch variable between Front and Back
        cameraSelector = if (cameraSelector == CameraSelector.DEFAULT_BACK_CAMERA) {
            CameraSelector.DEFAULT_FRONT_CAMERA
        } else {
            CameraSelector.DEFAULT_BACK_CAMERA
        }
        // Restart camera with new selector
        startCamera()
    }

    private fun startCamera() {
        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            val provider = providerFuture.get()

            val preview = Preview.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                .build()
                .also { it.setSurfaceProvider(viewFinder.surfaceProvider) }

            val analysis = ImageAnalysis.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor) { proxy -> detect(proxy) }
                }

            try {
                // Unbind previous use cases before rebinding
                provider.unbindAll()

                // Bind with the CURRENT cameraSelector (Front or Back)
                provider.bindToLifecycle(
                    this,
                    cameraSelector,
                    preview,
                    analysis
                )
            } catch (e: Exception) {
                Log.e("CameraX", "Binding failed", e)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun setupHandLandmarker() {
        val baseOptions = BaseOptions.builder()
            .setModelAssetPath("hand_landmarker.task")
            .setDelegate(Delegate.GPU)
            .build()

        val options = HandLandmarker.HandLandmarkerOptions.builder()
            .setBaseOptions(baseOptions)
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setResultListener { result, _ ->
                runOnUiThread { drawSkeletonAndPredict(result) }
            }
            .build()

        handLandmarker = HandLandmarker.createFromOptions(this, options)
    }

    private fun detect(proxy: ImageProxy) {
        if (!::handLandmarker.isInitialized) {
            proxy.close()
            return
        }

        val bitmap = proxy.toBitmap()
        val matrix = Matrix().apply {
            postRotate(proxy.imageInfo.rotationDegrees.toFloat())

            // If using Front Camera, mirror the image horizontally so it feels natural
            if (cameraSelector == CameraSelector.DEFAULT_FRONT_CAMERA) {
                postScale(-1f, 1f, bitmap.width / 2f, bitmap.height / 2f)
            }
        }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)

        inputSourceWidth = rotated.width.toFloat()
        inputSourceHeight = rotated.height.toFloat()

        val mpImage = BitmapImageBuilder(rotated).build()
        handLandmarker.detectAsync(mpImage, SystemClock.uptimeMillis())

        proxy.close()
    }

    private fun predictSign(landmarks: List<NormalizedLandmark>): String {
        // 🛑 SAFETY CHECK: If model isn't loaded, stop here.
        if (!::tflite.isInitialized || !::labels.isInitialized) {
            return "Loading AI..."
        }

        // 1. Prepare Input
        val input = Array(1) { FloatArray(63) }
        var i = 0
        for (lm in landmarks) {
            input[0][i++] = lm.x()
            input[0][i++] = lm.y()
            input[0][i++] = lm.z()
        }

        // 2. Prepare Output
        val output = Array(1) { FloatArray(labels.size) }

        // 3. Run Inference
        try {
            tflite.run(input, output)
        } catch (e: Exception) {
            Log.e("AI", "Inference Failed", e)
            return "AI Error"
        }

        // 4. Find Best Match
        val maxIndex = output[0].indices.maxByOrNull { output[0][it] } ?: -1

        if (maxIndex != -1 && output[0][maxIndex] > 0.5f) {
            return labels[maxIndex]
        } else {
            return ""
        }
    }

    // ================== DRAWING ==================
    private fun drawSkeletonAndPredict(result: HandLandmarkerResult) {
        if (bitmapBuffer == null) {
            bitmapBuffer = Bitmap.createBitmap(
                viewFinder.width,
                viewFinder.height,
                Bitmap.Config.ARGB_8888
            )
            canvasBuffer = Canvas(bitmapBuffer!!)
        }

        canvasBuffer!!.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

        // Only draw if hands are detected
        result.landmarks().forEach { landmarks ->
            val sign = predictSign(landmarks)

            canvasBuffer!!.drawText(
                sign,
                viewFinder.width / 2f,
                200f,
                textPaint
            )
        }

        overlayImageView.setImageBitmap(bitmapBuffer)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::handLandmarker.isInitialized) handLandmarker.close()
        if (::cameraExecutor.isInitialized) cameraExecutor.shutdown()
        if (::tflite.isInitialized) tflite.close()
    }
}