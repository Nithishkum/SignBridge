package com.example.cameraxproject

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.*
import android.os.*
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
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
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var viewFinder: PreviewView
    private lateinit var overlayImageView: ImageView
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var handLandmarker: HandLandmarker

    private var cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

    private lateinit var tflite: Interpreter
    private lateinit var labels: List<String>
    private var inputSourceWidth = 0f
    private var inputSourceHeight = 0f
    private var bitmapBuffer: Bitmap? = null
    private var canvasBuffer: Canvas? = null

    private lateinit var tts: TextToSpeech
    private lateinit var sentenceDisplay: TextView
    private var currentLiveLabel = ""
    private var constructedSentence = ""

    private lateinit var btnSuggest1: Button
    private lateinit var btnSuggest2: Button
    private lateinit var btnSuggest3: Button

    private var areControlsVisible = false

    private val dictionary = listOf(
        "HELLO", "HELP", "HOME", "HOW", "HAPPY", "I", "IS", "IT", "IN", "IF",
        "LOVE", "LIKE", "LATER", "ME", "MY", "MORE", "MAN", "NO", "NOT", "NEED",
        "NAME", "NICE", "PLEASE", "PEOPLE", "PROJECT", "SORRY", "SEE", "SOON",
        "THANKS", "THAT", "THIS", "THE", "TIME", "TO", "YOU", "YES", "YOUR",
        "WHAT", "WHERE", "WHEN", "WHY", "WHO", "WATER", "WANT", "GOOD", "BAD", "OK"
    )

    private val textPaint = Paint().apply {
        color = Color.GREEN
        textSize = 120f
        isFakeBoldText = true
        textAlign = Paint.Align.CENTER
        setShadowLayer(10f, 0f, 0f, Color.BLACK)
    }

    private val reqPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) { setupHandLandmarker(); startCamera() } else Toast.makeText(this, "Camera Permission Denied", Toast.LENGTH_LONG).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        viewFinder = findViewById(R.id.viewFinder)
        overlayImageView = findViewById(R.id.overlayImageView)
        tts = TextToSpeech(this, this)

        val startScreen = findViewById<android.view.ViewGroup>(R.id.startScreenLayout)
        val startButton = findViewById<Button>(R.id.startButton)
        val loadingText = findViewById<TextView>(R.id.loadingText)
        val btnExit = findViewById<View>(R.id.btnExit)

        // UI Containers
        val topDisplayLayout = findViewById<View>(R.id.topDisplayLayout)
        val controlPanel = findViewById<View>(R.id.controlPanel)
        val suggestionLayout = findViewById<View>(R.id.suggestionLayout)
        sentenceDisplay = findViewById(R.id.sentenceDisplay)

        val btnAccessibility = findViewById<MaterialButton>(R.id.btnAccessibility)
        val btnAdd = findViewById<Button>(R.id.btnAdd)
        val btnSpace = findViewById<Button>(R.id.btnSpace)
        val btnDelete = findViewById<Button>(R.id.btnDelete)
        val btnClear = findViewById<Button>(R.id.btnClear)
        val btnSpeak = findViewById<Button>(R.id.btnSpeak)

        btnSuggest1 = findViewById(R.id.btnSuggest1)
        btnSuggest2 = findViewById(R.id.btnSuggest2)
        btnSuggest3 = findViewById(R.id.btnSuggest3)

        val suggestionListener = View.OnClickListener { v -> completeCurrentWord((v as Button).text.toString()) }
        btnSuggest1.setOnClickListener(suggestionListener)
        btnSuggest2.setOnClickListener(suggestionListener)
        btnSuggest3.setOnClickListener(suggestionListener)

        cameraExecutor = Executors.newSingleThreadExecutor()

        try {
            labels = loadLabels()
            tflite = Interpreter(loadModelFile())
        } catch (e: Exception) { Log.e("AI", "Error loading model", e) }

        // --- BUTTON ACTIONS ---
        btnAdd?.setOnClickListener {
            if (currentLiveLabel.isNotEmpty()) {
                constructedSentence += currentLiveLabel
                sentenceDisplay.text = constructedSentence
                updateSuggestions()
            }
        }
        btnSpace?.setOnClickListener {
            constructedSentence += " "
            sentenceDisplay.text = constructedSentence
            updateSuggestions()
        }
        btnDelete?.setOnClickListener {
            if (constructedSentence.isNotEmpty()) {
                constructedSentence = constructedSentence.substring(0, constructedSentence.length - 1)
                sentenceDisplay.text = constructedSentence
                updateSuggestions()
            }
        }
        btnClear?.setOnClickListener {
            constructedSentence = ""
            sentenceDisplay.text = ""
            hideSuggestions()
        }
        btnSpeak?.setOnClickListener {
            if (constructedSentence.isNotEmpty()) speakText(constructedSentence)
        }

        // --- STARTUP LOGIC ---
        Handler(Looper.getMainLooper()).postDelayed({
            loadingText?.setTextColor(Color.GREEN)
            startButton?.visibility = View.VISIBLE
        }, 500)

        startButton?.setOnClickListener {
            startScreen.animate().alpha(0f).setDuration(500).withEndAction {
                startScreen.visibility = View.GONE
                btnAccessibility.visibility = View.VISIBLE
                checkPermissionAndStart()
            }
        }

        // --- TOGGLE ANIMATION ---
        btnAccessibility.setOnClickListener {
            toggleControlsWithAnimation(btnAccessibility, controlPanel, suggestionLayout, topDisplayLayout)
        }

        btnExit?.setOnClickListener { finishAffinity() }
    }

    private fun toggleControlsWithAnimation(toggleBtn: MaterialButton, vararg views: View) {
        if (areControlsVisible) {
            views.forEach { view ->
                view.animate()
                    .translationY(300f)
                    .alpha(0f)
                    .setDuration(300)
                    .setInterpolator(AccelerateDecelerateInterpolator())
                    .withEndAction { view.visibility = View.GONE }
                    .start()
            }
            toggleBtn.setIconResource(R.drawable.accessability)
            areControlsVisible = false
        } else {
            views.forEach { view ->
                view.visibility = View.VISIBLE
                view.translationY = 300f
                view.alpha = 0f
                view.animate()
                    .translationY(0f)
                    .alpha(1f)
                    .setDuration(400)
                    .setInterpolator(AccelerateDecelerateInterpolator())
                    .start()
            }
            toggleBtn.setIconResource(android.R.drawable.ic_menu_close_clear_cancel)
            areControlsVisible = true
        }
    }

    private fun updateSuggestions() {
        val words = constructedSentence.split(" ")
        val currentTypingWord = words.lastOrNull() ?: ""
        if (currentTypingWord.isEmpty()) { hideSuggestions(); return }
        val matches = dictionary.filter { it.startsWith(currentTypingWord) && it != currentTypingWord }.take(3)
        if (matches.isNotEmpty()) { btnSuggest1.visibility = View.VISIBLE; btnSuggest1.text = matches[0] } else btnSuggest1.visibility = View.INVISIBLE
        if (matches.size > 1) { btnSuggest2.visibility = View.VISIBLE; btnSuggest2.text = matches[1] } else btnSuggest2.visibility = View.INVISIBLE
        if (matches.size > 2) { btnSuggest3.visibility = View.VISIBLE; btnSuggest3.text = matches[2] } else btnSuggest3.visibility = View.INVISIBLE
    }

    private fun completeCurrentWord(fullWord: String) {
        val lastSpaceIndex = constructedSentence.lastIndexOf(" ")
        constructedSentence = if (lastSpaceIndex == -1) "$fullWord " else "${constructedSentence.substring(0, lastSpaceIndex + 1)}$fullWord "
        sentenceDisplay.text = constructedSentence
        hideSuggestions()
    }

    private fun hideSuggestions() {
        btnSuggest1.visibility = View.INVISIBLE
        btnSuggest2.visibility = View.INVISIBLE
        btnSuggest3.visibility = View.INVISIBLE
    }

    override fun onInit(status: Int) { if (status == TextToSpeech.SUCCESS) tts.setLanguage(Locale.US) }

    private fun speakText(text: String) { tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null) }

    private fun loadLabels(): List<String> = assets.open("labels.txt").bufferedReader().readLines()

    private fun loadModelFile(): MappedByteBuffer {
        val fd = assets.openFd("sign_language.tflite")
        return FileInputStream(fd.fileDescriptor).channel.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
    }

    private fun checkPermissionAndStart() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            setupHandLandmarker(); startCamera()
        } else reqPermissionLauncher.launch(Manifest.permission.CAMERA)
    }

    private fun startCamera() {
        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            val provider = providerFuture.get()
            val preview = Preview.Builder().setTargetAspectRatio(AspectRatio.RATIO_4_3).build().also { it.setSurfaceProvider(viewFinder.surfaceProvider) }
            val analysis = ImageAnalysis.Builder().setTargetAspectRatio(AspectRatio.RATIO_4_3).setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888).build().also { it.setAnalyzer(cameraExecutor) { proxy -> detect(proxy) } }
            try { provider.unbindAll(); provider.bindToLifecycle(this, cameraSelector, preview, analysis) } catch (e: Exception) { Log.e("CameraX", "Binding failed", e) }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun setupHandLandmarker() {
        val baseOptions = BaseOptions.builder().setModelAssetPath("hand_landmarker.task").setDelegate(Delegate.GPU).build()
        val options = HandLandmarker.HandLandmarkerOptions.builder().setBaseOptions(baseOptions).setRunningMode(RunningMode.LIVE_STREAM).setResultListener { result, _ -> runOnUiThread { drawSkeletonAndPredict(result) } }.build()
        handLandmarker = HandLandmarker.createFromOptions(this, options)
    }

    private fun detect(proxy: ImageProxy) {
        if (!::handLandmarker.isInitialized) { proxy.close(); return }
        val bitmap = proxy.toBitmap()
        val matrix = Matrix().apply {
            postRotate(proxy.imageInfo.rotationDegrees.toFloat())
            if (cameraSelector == CameraSelector.DEFAULT_FRONT_CAMERA) postScale(-1f, 1f, bitmap.width / 2f, bitmap.height / 2f)
        }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        inputSourceWidth = rotated.width.toFloat(); inputSourceHeight = rotated.height.toFloat()
        val mpImage = BitmapImageBuilder(rotated).build()
        handLandmarker.detectAsync(mpImage, SystemClock.uptimeMillis())
        proxy.close()
    }

    private fun predictSign(landmarks: List<NormalizedLandmark>): String {
        if (!::tflite.isInitialized || !::labels.isInitialized) return "..."
        val input = Array(1) { FloatArray(63) }
        var i = 0
        for (lm in landmarks) { input[0][i++] = lm.x(); input[0][i++] = lm.y(); input[0][i++] = lm.z() }
        val output = Array(1) { FloatArray(labels.size) }
        tflite.run(input, output)
        val maxIndex = output[0].indices.maxByOrNull { output[0][it] } ?: -1
        return if (maxIndex != -1 && output[0][maxIndex] > 0.5f) labels[maxIndex] else ""
    }

    private fun drawSkeletonAndPredict(result: HandLandmarkerResult) {
        if (bitmapBuffer == null) { bitmapBuffer = Bitmap.createBitmap(viewFinder.width, viewFinder.height, Bitmap.Config.ARGB_8888); canvasBuffer = Canvas(bitmapBuffer!!) }
        canvasBuffer!!.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        if (result.landmarks().isEmpty()) currentLiveLabel = ""
        result.landmarks().forEach { landmarks ->
            val sign = predictSign(landmarks)
            currentLiveLabel = sign
            canvasBuffer!!.drawText(sign, viewFinder.width / 2f, 200f, textPaint)
        }
        overlayImageView.setImageBitmap(bitmapBuffer)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::handLandmarker.isInitialized) handLandmarker.close()
        if (::cameraExecutor.isInitialized) cameraExecutor.shutdown()
        if (::tflite.isInitialized) tflite.close()
        if (::tts.isInitialized) tts.shutdown()
    }
}