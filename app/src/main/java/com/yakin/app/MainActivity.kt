package com.yakin.app

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts

class MainActivity : ComponentActivity() {
    private lateinit var hub: Hub

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        hub = Hub(Store(this))
        val launcher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { hub.start(this) }
        val perms = mutableListOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= 31) perms += listOf(Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_ADVERTISE, Manifest.permission.BLUETOOTH_CONNECT)
        if (Build.VERSION.SDK_INT >= 33) perms += Manifest.permission.NEARBY_WIFI_DEVICES
        launcher.launch(perms.toTypedArray())
        setContent { YakinTheme { Root(hub) { hub.start(this) } } }
    }
}
