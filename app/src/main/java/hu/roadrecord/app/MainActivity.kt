package hu.roadrecord.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import hu.roadrecord.app.display.ScreenAwakeController
import hu.roadrecord.app.display.ScreenAwakeOptions
import hu.roadrecord.app.ui.RoadRecordApp
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var screenAwake: ScreenAwakeController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        screenAwake = ScreenAwakeController(window, contentResolver)
        setContent { RoadRecordApp() }
        val app = application as RoadRecordApplication
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                app.repository.settings.map(ScreenAwakeOptions::from).distinctUntilChanged()
                    .collect { screenAwake.updateOptions(it) }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        screenAwake.resume()
    }

    override fun onPause() {
        screenAwake.pause()
        super.onPause()
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        screenAwake.userInteracted()
    }

    override fun onDestroy() {
        screenAwake.pause()
        super.onDestroy()
    }
}
