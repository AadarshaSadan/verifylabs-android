package com.fatdogs.verifylabs.core.util

import android.app.Application
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import com.fatdogs.verifylabs.data.base.PreferencesHelperImpl
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class MyApplication : Application() {
    private val TAG="MyApplication"

    override fun onCreate() {
        super.onCreate()
        //val preferencesHelperImpl = PreferencesHelperImpl(this)
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)

//        val isDarkMode = resources.configuration.uiMode and
//                android.content.res.Configuration.UI_MODE_NIGHT_MASK
//        when (isDarkMode) {
//            android.content.res.Configuration.UI_MODE_NIGHT_YES -> {
//                Log.d(TAG, "Dark theme applied")
//                preferencesHelperImpl.setTheme(true)
//            }
//            android.content.res.Configuration.UI_MODE_NIGHT_NO -> {
//                Log.d(TAG,"Light theme applied")
//                preferencesHelperImpl.setTheme(false)
//            }
//            android.content.res.Configuration.UI_MODE_NIGHT_UNDEFINED -> {
//                Log.d(TAG, "System default theme applied")
//            }
//        }

    }
}
