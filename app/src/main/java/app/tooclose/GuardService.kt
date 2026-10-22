package app.tooclose

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.hardware.camera2.CameraCharacteristics
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.util.Size
import android.view.KeyEvent
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import java.util.concurrent.Executors

/**
 * Foreground service (type camera): looks at the front camera about 2.5 times a second while the screen is on,
 * and covers the screen when the face stays closer than the threshold. Camera is released while the screen is off.
 * Frames are processed in memory only; nothing is stored or sent anywhere.
 */
class GuardService : LifecycleService() {
    private val main = Handler(Looper.getMainLooper())
    private val exec = Executors.newSingleThreadExecutor()
    private val est = Estimator()
    private lateinit var overlay: Overlay
    private var provider: ProcessCameraProvider? = null
    private var focalRatio = 0.8f
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    private var lastFrameAt = 0L
    private val recent = ArrayDeque<Pair<Long, Float>>()   // (time, cm) for a 3-reading median
    private var nearSince = 0L
    private var farSince = 0L
    private var lastFaceAt = 0L
    private var lastSpokeAt = 0L
    private var pausedMedia = false

    private val screenRx = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            when (i.action) {
                Intent.ACTION_SCREEN_OFF -> stopCamera()
                Intent.ACTION_SCREEN_ON -> startCamera()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        overlay = Overlay(this)
        createChannel()
        val n = notif(getString(R.string.notif_watching))
        if (Build.VERSION.SDK_INT >= 30) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA) else startForeground(1, n)
        val f = IntentFilter().apply { addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_SCREEN_ON) }
        ContextCompat.registerReceiver(this, screenRx, f, ContextCompat.RECEIVER_NOT_EXPORTED)   // system broadcasts still arrive
        tts = TextToSpeech(this) { st -> ttsReady = st == TextToSpeech.SUCCESS }
        if ((getSystemService(POWER_SERVICE) as PowerManager).isInteractive) startCamera()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        return START_NOT_STICKY   // can't reopen the camera from the background anyway; the boot reminder covers restarts
    }

    override fun onDestroy() {
        try { unregisterReceiver(screenRx) } catch (e: Exception) {}
        stopCamera()
        unlock()
        tts?.shutdown()
        exec.shutdown()
        if (instance === this) instance = null
        state = State.OFF
        super.onDestroy()
    }

    private fun startCamera() {
        if (provider != null) return
        val pf = ProcessCameraProvider.getInstance(this)
        pf.addListener({
            try {
                val p = pf.get()
                val rs = ResolutionSelector.Builder()
                    .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                    .setResolutionStrategy(ResolutionStrategy(Size(640, 480), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER))
                    .build()
                val a = ImageAnalysis.Builder()
                    .setResolutionSelector(rs)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                a.setAnalyzer(exec) { img -> try { onFrame(img) } finally { img.close() } }
                p.unbindAll()
                val cam = p.bindToLifecycle(this, CameraSelector.DEFAULT_FRONT_CAMERA, a)
                focalRatio = focalRatioOf(cam.cameraInfo)
                provider = p
                state = State.WATCHING
            } catch (e: Exception) {
                state = State.NO_CAMERA
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun stopCamera() {
        try { provider?.unbindAll() } catch (e: Exception) {}
        provider = null
        recent.clear(); nearSince = 0; farSince = 0
        if (state == State.WATCHING) state = State.SCREEN_OFF
    }

    @OptIn(ExperimentalCamera2Interop::class)
    private fun focalRatioOf(info: CameraInfo): Float = try {
        val ci = Camera2CameraInfo.from(info)
        val f = ci.getCameraCharacteristic(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.firstOrNull()
        val s = ci.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
        if (f != null && s != null && s.width > 0f) f / maxOf(s.width, s.height) else 0.8f
    } catch (e: Exception) { 0.8f }

    private fun onFrame(img: ImageProxy) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastFrameAt < 400) return
        lastFrameAt = now
        val r = est.read(img, focalRatio, Prefs.ipdCm(this))
        val cal = calibration
        if (cal != null && r.rawCm != null) {
            cal.add(r.rawCm)
            if (cal.size >= 6) {
                val med = cal.sorted()[cal.size / 2]
                Prefs.setCalib(this, calTarget / med)
                calibration = null
            }
        }
        val cm = r.rawCm?.let { it * Prefs.calib(this) }
        main.post { step(now, cm, r.dark) }
    }

    /** State machine, on the main thread. */
    private fun step(now: Long, cmRaw: Float?, dark: Boolean) {
        val th = Prefs.thresholdCm(this)
        var cm: Float? = null
        if (cmRaw != null) {
            lastFaceAt = now
            recent.addLast(Pair(now, cmRaw))
            while (recent.size > 3 || (recent.isNotEmpty() && now - recent.first().first > 2000)) recent.removeFirst()
            cm = recent.map { it.second }.sorted()[recent.size / 2]
        }
        lastCm = cm
        lastDark = dark
        if (!overlay.showing) {
            when {
                cm != null && cm < th -> { if (nearSince == 0L) nearSince = now; if (now - nearSince >= 3000) lock() }
                cm != null -> nearSince = 0
                now - lastFaceAt > 1500 -> nearSince = 0
            }
        } else {
            when {
                dark -> { farSince = 0; overlay.setDetail(getString(R.string.cover_dark)) }
                cm != null && cm >= th + 3 -> { if (farSince == 0L) farSince = now; if (now - farSince >= 2000) unlock() }
                cm != null -> { farSince = 0; overlay.setDetail(getString(R.string.cover_distance, cm.toInt(), th)) }
                now - lastFaceAt > 10000 -> unlock()   // walked away
            }
            if (overlay.showing && Prefs.voice(this) && now - lastSpokeAt > 15000) speak()
        }
    }

    private fun lock() {
        nearSince = 0; farSince = 0
        overlay.show()
        state = State.COVERED
        if (Prefs.pauseMedia(this)) {
            val am = getSystemService(AUDIO_SERVICE) as AudioManager
            if (am.isMusicActive) { mediaKey(am, KeyEvent.KEYCODE_MEDIA_PAUSE); pausedMedia = true }
        }
        if (Prefs.voice(this)) speak()
    }

    private fun unlock() {
        nearSince = 0; farSince = 0
        val was = overlay.showing
        overlay.hide()
        if (state == State.COVERED) state = if (provider != null) State.WATCHING else State.SCREEN_OFF
        if (was && pausedMedia) mediaKey(getSystemService(AUDIO_SERVICE) as AudioManager, KeyEvent.KEYCODE_MEDIA_PLAY)
        pausedMedia = false
    }

    private fun mediaKey(am: AudioManager, code: Int) {
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
    }

    private fun speak() {
        lastSpokeAt = SystemClock.elapsedRealtime()
        if (ttsReady) tts?.speak(getString(R.string.voice_line), TextToSpeech.QUEUE_FLUSH, null, "tooclose")
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val ch = NotificationChannel(CH, getString(R.string.notif_channel), NotificationManager.IMPORTANCE_LOW)
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(ch)
        }
    }

    private fun notif(text: String): Notification {
        val pi = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, CH)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
    }

    enum class State { OFF, WATCHING, COVERED, SCREEN_OFF, NO_CAMERA }

    companion object {
        const val CH = "guard"
        @Volatile var instance: GuardService? = null
        @Volatile var state = State.OFF
        @Volatile var lastCm: Float? = null
        @Volatile var lastDark = false
        /** Set by the calibration button: collects 6 raw readings, then saves actual ÷ median. */
        @Volatile var calibration: MutableList<Float>? = null
        @Volatile var calTarget = 30f

        fun start(c: Context) { ContextCompat.startForegroundService(c, Intent(c, GuardService::class.java)) }
        fun stop(c: Context) { c.stopService(Intent(c, GuardService::class.java)) }
        fun calibrate(actualCm: Float) { calTarget = actualCm; calibration = java.util.Collections.synchronizedList(ArrayList()) }
    }
}
