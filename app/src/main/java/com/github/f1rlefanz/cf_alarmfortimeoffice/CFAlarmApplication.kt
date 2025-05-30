package com.github.f1rlefanz.cf_alarmfortimeoffice

import android.app.Application
import timber.log.Timber

class CFAlarmApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
            Timber.d("Timber initialisiert")
        }
    }
}