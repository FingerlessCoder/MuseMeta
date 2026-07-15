package com.mymusicplayer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.mymusicplayer.data.preferences.SettingsDataStore
import com.mymusicplayer.service.ScanService
import com.mymusicplayer.ui.theme.AccentPalettes
import com.mymusicplayer.ui.theme.MuseMetaTheme
import org.koin.android.ext.android.get

class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    /**
     * Launcher for the audio/storage permission.
     * On grant we trigger an auto-scan so the home screen
     * populates without the user navigating to the Scan tab.
     */
    private val audioPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        Log.d(TAG, "Audio permission granted=$granted")
        if (granted) {
            startAutoScan()
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* granted or not — notification permission is a nice-to-have */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        requestPermissions()

        setContent {
            val settingsDataStore: SettingsDataStore = get()
            val amoledBlack by settingsDataStore.amoledBlackTheme.collectAsState(initial = false)
            val accentIndex by settingsDataStore.accentColorIndex.collectAsState(initial = 0)

            MuseMetaTheme(
                amoledBlack = amoledBlack,
                accentPalette = AccentPalettes.getOrElse(accentIndex) { AccentPalettes[0] }
            ) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MainScreen()
                }
            }
        }
    }

    private fun requestPermissions() {
        // Audio / storage — required for the app to function
        requestAudioPermission()

        // Notification channel permission — nice-to-have on TIRAMISU+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun requestAudioPermission() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        if (ContextCompat.checkSelfPermission(this, permission)
            != PackageManager.PERMISSION_GRANTED
        ) {
            // Request the permission — scan will start automatically once granted
            audioPermissionLauncher.launch(permission)
        } else {
            // Already granted — scan directly
            startAutoScan()
        }
    }

    private fun startAutoScan() {
        Log.d(TAG, "Starting auto-scan after audio permission granted")
        val intent = Intent(this, ScanService::class.java).apply {
            action = ScanService.ACTION_START_SCAN
        }
        startForegroundService(intent)
    }
}
