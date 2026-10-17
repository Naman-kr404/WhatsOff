package com.example.whatsoff

import android.app.Application
import com.example.whatsoff.di.AppContainer
import com.example.whatsoff.di.DefaultAppContainer

/**
 * Custom Application class initializing the DI container.
 */
class WhatsOffApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = DefaultAppContainer(this)
    }
}
