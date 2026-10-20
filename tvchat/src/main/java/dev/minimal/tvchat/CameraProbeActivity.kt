package dev.minimal.tvchat

import android.app.Activity
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.util.Range
import android.media.ImageReader
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.Log
import android.view.Surface
import android.widget.TextView
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Diagnostic only: opens the USB camera every way Android offers and counts the frames that arrive,
 * to tell whether the TV can stream from it at all. Started over ADB:
 *   adb shell am start -n dev.minimal.tvchat/.CameraProbeActivity
 * Results go to logcat under TvChat and onto the screen.
 */
class CameraProbeActivity : Activity() {

    private lateinit var text: TextView
    // The probe blocks on one thread while the camera answers on another.
    private val thread = HandlerThread("probe").apply { start() }
    private val callbacks = HandlerThread("probe-callbacks").apply { start() }
    private val handler = Handler(callbacks.looper)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        text = TextView(this).apply { textSize = 14f; setPadding(48, 48, 48, 48) }
        setContentView(text)
        val mic = intent?.getBooleanExtra("mic", false) == true
        Handler(thread.looper).post { if (mic) micAll() else runAll() }
    }

    private fun say(line: String) {
        Log.i(TAG, line)
        runOnUiThread { text.append(line + "\n") }
    }

    private fun runAll() {
        // Frames each size delivers in 4 s (~100 = 25 fps). Order matters: a step that wedges
        // the driver makes every later step fail too.
        for ((w, h) in listOf(640 to 360, 640 to 480, 848 to 480, 800 to 600, 1280 to 720, 1920 to 1080, 640 to 480)) {
            say("yuv ${w}x$h: " + camera2(w, h, ImageFormat.YUV_420_888))
        }
        say("done")
    }

    /**
     * Records 3 s from each audio source and reports the level, to find a microphone that hears
     * the room. Started with: am start -n dev.minimal.tvchat/.CameraProbeActivity --ez mic true
     */
    private fun micAll() {
        val am = getSystemService(android.media.AudioManager::class.java)
        am.getDevices(android.media.AudioManager.GET_DEVICES_INPUTS).forEach {
            say("input device: type=${it.type} name=${it.productName} address=${it.address}")
        }
        val sources = listOf(
            "DEFAULT" to android.media.MediaRecorder.AudioSource.DEFAULT,
            "MIC" to android.media.MediaRecorder.AudioSource.MIC,
            "VOICE_COMMUNICATION" to android.media.MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            "VOICE_RECOGNITION" to android.media.MediaRecorder.AudioSource.VOICE_RECOGNITION,
            "CAMCORDER" to android.media.MediaRecorder.AudioSource.CAMCORDER,
            "UNPROCESSED" to android.media.MediaRecorder.AudioSource.UNPROCESSED,
        )
        for ((name, source) in sources) say("$name: " + record(source))
        say("done")
    }

    @android.annotation.SuppressLint("MissingPermission")
    private fun record(source: Int): String {
        val rate = 16000
        val min = android.media.AudioRecord.getMinBufferSize(rate, android.media.AudioFormat.CHANNEL_IN_MONO, android.media.AudioFormat.ENCODING_PCM_16BIT)
        if (min <= 0) return "no buffer size"
        val rec = runCatching {
            android.media.AudioRecord(source, rate, android.media.AudioFormat.CHANNEL_IN_MONO, android.media.AudioFormat.ENCODING_PCM_16BIT, min * 4)
        }.getOrElse { return "open failed: ${it.message}" }
        if (rec.state != android.media.AudioRecord.STATE_INITIALIZED) { rec.release(); return "not initialized" }
        val buf = ShortArray(min)
        var peak = 0
        var sumSq = 0.0
        var n = 0L
        return try {
            rec.startRecording()
            val end = SystemClock.elapsedRealtime() + 3000
            while (SystemClock.elapsedRealtime() < end) {
                val read = rec.read(buf, 0, buf.size)
                for (i in 0 until read) { val v = kotlin.math.abs(buf[i].toInt()); if (v > peak) peak = v; sumSq += v.toDouble() * v; n++ }
            }
            val routed = rec.routedDevice
            val rms = if (n > 0) kotlin.math.sqrt(sumSq / n).toInt() else 0
            "peak=$peak rms=$rms samples=$n via ${routed?.type}/${routed?.productName}/${routed?.address}"
        } catch (e: Exception) {
            "error ${e.message}"
        } finally {
            runCatching { rec.stop() }
            rec.release()
        }
    }

    /** Camera2 into a SurfaceTexture (format null) or an ImageReader of the given format. */
    private fun camera2(w: Int, h: Int, format: Int?, fps: Int? = null): String {
        val manager = getSystemService(CameraManager::class.java)
        val id = manager.cameraIdList.firstOrNull() ?: return "no camera"
        var frames = 0
        var error: String? = null
        val texture = SurfaceTexture(0).apply { setDefaultBufferSize(w, h) }
        texture.setOnFrameAvailableListener({ frames++ }, handler)
        val reader = format?.let { ImageReader.newInstance(w, h, it, 3) }
        reader?.setOnImageAvailableListener({ r -> r.acquireLatestImage()?.close(); frames++ }, handler)
        val surface = reader?.surface ?: Surface(texture)
        val opened = CountDownLatch(1)
        var device: CameraDevice? = null
        try {
            manager.openCamera(id, object : CameraDevice.StateCallback() {
                override fun onOpened(d: CameraDevice) { device = d; opened.countDown() }
                override fun onDisconnected(d: CameraDevice) { error = "disconnected"; opened.countDown() }
                override fun onError(d: CameraDevice, code: Int) { error = "open error $code"; opened.countDown() }
            }, handler)
            opened.await(5, TimeUnit.SECONDS)
            val dev = device ?: return error ?: "open timeout"
            val ready = CountDownLatch(1)
            var session: CameraCaptureSession? = null
            @Suppress("DEPRECATION")
            dev.createCaptureSession(listOf(surface), object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(s: CameraCaptureSession) { session = s; ready.countDown() }
                override fun onConfigureFailed(s: CameraCaptureSession) { error = "configure failed"; ready.countDown() }
            }, handler)
            ready.await(5, TimeUnit.SECONDS)
            val s = session ?: return error ?: "session timeout"
            val request = dev.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                addTarget(surface)
                if (fps != null) set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, Range(fps, fps))
            }.build()
            var errors = 0
            s.setRepeatingRequest(request, object : CameraCaptureSession.CaptureCallback() {
                override fun onCaptureFailed(session: CameraCaptureSession, request: android.hardware.camera2.CaptureRequest, failure: android.hardware.camera2.CaptureFailure) { errors++ }
            }, handler)
            val start = SystemClock.elapsedRealtime()
            while (SystemClock.elapsedRealtime() - start < 4000) Thread.sleep(100)
            runCatching { s.stopRepeating(); s.abortCaptures() }
            return "$frames frames in 4s, $errors failed captures" + (error?.let { ", $it" } ?: "")
        } catch (e: Exception) {
            return "exception ${e.javaClass.simpleName}: ${e.message}"
        } finally {
            runCatching { device?.close() }
            reader?.close()
            surface.release()
            texture.release()
            Thread.sleep(800)
        }
    }

    /** The old Camera API, which some external-camera drivers handle better. */
    @Suppress("DEPRECATION")
    private fun camera1(w: Int, h: Int): String {
        var frames = 0
        val texture = SurfaceTexture(0)
        var cam: android.hardware.Camera? = null
        return try {
            // Camera1 delivers callbacks on the thread that opened it, so open from the callback thread.
            val openLatch = CountDownLatch(1)
            handler.post { cam = runCatching { android.hardware.Camera.open(0) }.getOrNull(); openLatch.countDown() }
            openLatch.await(5, TimeUnit.SECONDS)
            val c = cam ?: return "open failed"
            val p = c.parameters
            val supported = p.supportedPreviewSizes.joinToString { "${it.width}x${it.height}" }
            val formats = p.supportedPreviewFormats.joinToString()
            p.setPreviewSize(w, h)
            c.parameters = p
            c.setPreviewTexture(texture)
            c.setPreviewCallback { _, _ -> frames++ }
            c.startPreview()
            Thread.sleep(4000)
            c.stopPreview()
            "$frames frames in 4s (sizes: $supported; formats: $formats)"
        } catch (e: Exception) {
            "exception ${e.javaClass.simpleName}: ${e.message}"
        } finally {
            runCatching { cam?.release() }
            texture.release()
            Thread.sleep(800)
        }
    }

    override fun onDestroy() {
        thread.quitSafely()
        callbacks.quitSafely()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "TvChat"
    }
}
