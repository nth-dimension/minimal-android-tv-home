package dev.minimal.tvhome

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import dev.minimal.tvhome.data.SleepTimer
import dev.minimal.tvhome.data.WatchNextRepository
import dev.minimal.tvhome.ui.HomeApp
import dev.minimal.tvhome.ui.ImageLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val viewModel: HomeViewModel by viewModels()

    /** Fires when Home is pressed while we are already the home screen. */
    private val homePresses = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    private val requestTvListings =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { viewModel.refreshWatchNext() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SleepTimer.load(this)
        lifecycleScope.launch(Dispatchers.IO) { ImageLoader.installHttpCache(applicationContext) }
        setContent { HomeApp(viewModel, homePresses) }
        lifecycleScope.launch {
            if (viewModel.shouldAskForTvListings()) {
                runCatching { requestTvListings.launch(WatchNextRepository.READ_TV_LISTINGS) }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshOnResume()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.hasCategory(Intent.CATEGORY_HOME)) homePresses.tryEmit(Unit)
    }
}
