package com.srtodo.app

import android.app.Application

class SRTodoApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.notificationBuilder.createChannel()
    }
}
