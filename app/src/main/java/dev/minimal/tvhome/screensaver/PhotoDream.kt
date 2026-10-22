package dev.minimal.tvhome.screensaver

import android.app.Activity
import android.os.Bundle
import android.service.dreams.DreamService
import android.view.KeyEvent
import android.view.WindowManager
import dev.minimal.tvhome.ui.ImageLoader

/** The TV's screensaver: Unsplash photos, a new one every five minutes. Any button wakes the TV. */
class PhotoDream : DreamService() {
    private var slideshow: PhotoSlideshow? = null

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        Thread { ImageLoader.installHttpCache(applicationContext) }.start()
        isInteractive = false
        isFullscreen = true
        isScreenBright = true
        slideshow = PhotoSlideshow(this).also { setContentView(it.view) }
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        slideshow?.start()
    }

    override fun onDreamingStopped() {
        slideshow?.stop()
        super.onDreamingStopped()
    }

    override fun onDetachedFromWindow() {
        slideshow?.release()
        slideshow = null
        super.onDetachedFromWindow()
    }
}

/** Preview from Options: the same slideshow in an activity. Any button closes it. */
class ScreensaverPreviewActivity : Activity() {
    private lateinit var slideshow: PhotoSlideshow

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        slideshow = PhotoSlideshow(this)
        setContentView(slideshow.view)
    }

    override fun onStart() {
        super.onStart()
        slideshow.start()
    }

    override fun onStop() {
        slideshow.stop()
        super.onStop()
    }

    override fun onDestroy() {
        slideshow.release()
        super.onDestroy()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_UP) finish()
        return true
    }
}
