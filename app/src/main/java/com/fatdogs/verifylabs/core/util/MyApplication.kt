package com.fatdogs.verifylabs.core.util

import android.app.Application
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import com.fatdogs.verifylabs.data.base.PreferencesHelperImpl
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesConfiguration
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class MyApplication : Application() {
    private val TAG="MyApplication"

    override fun onCreate() {
        super.onCreate()
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)

        Purchases.configure(
            PurchasesConfiguration.Builder(this, "sk_LgBpMBEefiXYuMtvvyqNRoPkAtaHP")
                .appUserID(null) // or provide custom user id if you handle accounts
                .build()

        )

    }
}
