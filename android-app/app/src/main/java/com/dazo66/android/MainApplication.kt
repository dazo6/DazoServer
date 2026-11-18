package com.dazo66.android

import android.app.Application
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import com.facebook.react.PackageList
import com.facebook.react.ReactApplication
import com.facebook.react.ReactHost
import com.facebook.react.ReactNativeApplicationEntryPoint.loadReactNative
import com.facebook.react.defaults.DefaultReactHost.getDefaultReactHost

class MainApplication : Application(), ReactApplication {
    override val reactHost: ReactHost by lazy {
        getDefaultReactHost(applicationContext, PackageList(this).packages.apply { add(DazoPackage()) }, useDevSupport = false)
    }
    override fun onCreate() {
        super.onCreate()
        applyTheme(this, getSharedPreferences("preferences", 0).getString("theme", "system") ?: "system")
        loadReactNative(this)
    }
    companion object {
        @JvmStatic fun applyTheme(context: Context, value: String) {
            val mode = when (value) { "dark" -> AppCompatDelegate.MODE_NIGHT_YES; "light" -> AppCompatDelegate.MODE_NIGHT_NO; else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM }
            AppCompatDelegate.setDefaultNightMode(mode)
        }
    }
}
