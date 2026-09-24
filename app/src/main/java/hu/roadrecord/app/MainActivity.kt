package hu.roadrecord.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.content.Intent
import android.content.pm.ActivityInfo
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import hu.roadrecord.app.display.ScreenAwakeController
import hu.roadrecord.app.display.ScreenAwakeOptions
import hu.roadrecord.app.ui.RoadRecordApp
import hu.roadrecord.app.service.TrackingService
import hu.roadrecord.app.theme.ThemeStore
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var screenAwake: ScreenAwakeController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        screenAwake = ScreenAwakeController(window, contentResolver)
        val initialAppearance=ThemeStore.mode(this)
        setContent { RoadRecordApp(initialAppearance) }
        if(ActivityCompat.checkSelfPermission(this,Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED){LocationServices.getFusedLocationProviderClient(this).lastLocation.addOnSuccessListener{location->location?.let{ThemeStore.saveLocation(this,it.latitude,it.longitude,it.time)}}}
        val app = application as RoadRecordApplication
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                app.repository.settings.map(ScreenAwakeOptions::from).distinctUntilChanged()
                    .collect { screenAwake.updateOptions(it) }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                app.repository.settings.map { it.landscapeEnabled }.distinctUntilChanged().collect { enabled ->
                    val target = if (enabled) ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                    if (requestedOrientation != target) requestedOrientation = target
                }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                app.repository.days.map { days ->
                    // The display policy belongs to the whole open work session. At a stop the
                    // last event is TRIP_END, but that must not silently cancel dimming/keep-awake.
                    days.any { day -> day.events.none { it.type == hu.roadrecord.app.data.EventType.WORK_END } }
                }.distinctUntilChanged().collect { screenAwake.updateTripActive(it) }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                app.repository.days.map { days ->
                    days.firstOrNull { day -> day.events.none { it.type == hu.roadrecord.app.data.EventType.WORK_END } }?.day?.id
                }.distinctUntilChanged().collect { openDayId ->
                    openDayId?.let { startForegroundService(Intent(this@MainActivity, TrackingService::class.java)
                        .setAction(TrackingService.ACTION_START).putExtra(TrackingService.EXTRA_DAY, it)) }
                }
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
