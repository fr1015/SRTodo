package com.srtodo.app

import android.content.Context
import com.srtodo.app.data.AppDatabase
import com.srtodo.app.data.TaskRepository
import com.srtodo.app.notification.NotificationUpdater

/** 手書きの DI コンテナ。アプリ全体で1つだけ作り、SRTodoApplication が持つ */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val database: AppDatabase by lazy { AppDatabase.create(appContext) }

    val notificationUpdater: NotificationUpdater by lazy {
        NotificationUpdater(appContext, database.taskDao())
    }

    val taskRepository: TaskRepository by lazy {
        TaskRepository(database, notificationUpdater)
    }
}

val Context.appContainer: AppContainer
    get() = (applicationContext as SRTodoApplication).container
