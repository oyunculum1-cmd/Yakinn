package com.yakin.app

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts

class MainActivity : ComponentActivity() {
    private lateinit var hub: Hub

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        hub = Hub(Store(this))
        // Tek izin: mikrofon (sesli arama). Bluetooth, konum ya da Google hizmeti gerekmez.
        val launcher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
        launcher.launch(Manifest.permission.RECORD_AUDIO)
        setContent { YakinTheme { Root(hub) { hub.start(this) } } }
        hub.start(this)
    }

    override fun onResume() {
        super.onResume()
        if (::hub.isInitialized) { if (hub.error != null) hub.retry(this) else hub.start(this) }
    }

    override fun onDestroy() {
        if (::hub.isInitialized && isFinishing) hub.stop()
        super.onDestroy()
    }
}
