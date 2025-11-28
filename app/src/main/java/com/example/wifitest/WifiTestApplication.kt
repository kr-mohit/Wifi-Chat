package com.example.wifitest

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate

class WifiTestApplication: Application() {

    override fun onCreate() {
        super.onCreate()

        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
    }
}