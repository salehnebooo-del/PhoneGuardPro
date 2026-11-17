package com.phoneguard.pro

import android.app.Application
import com.phoneguard.pro.di.AppContainer

class PhoneGuardApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
