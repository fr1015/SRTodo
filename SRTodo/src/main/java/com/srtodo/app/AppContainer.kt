package com.srtodo.app

import android.content.Context
import com.srtodo.app.data.AppDatabase
import com.srtodo.app.data.TaskRepository
import com.srtodo.app.notification.NotificationUpdater
import com.srtodo.app.notification.RefreshAlarm
import com.srtodo.app.notification.TaskNotificationBuilder
import com.srtodo.app.notification.TaskNotificationController
import com.srtodo.app.settings.AppSettings

/** 手書きの DI コンテナ。アプリ全体で1つだけ作り、SRTodoApplication が持つ。中身は使うときに作る */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val database: AppDatabase by lazy { AppDatabase.create(appContext) }

    val settings: AppSettings by lazy { AppSettings(appContext) }

    val notificationBuilder: TaskNotificationBuilder by lazy { TaskNotificationBuilder(appContext) }

    val refreshAlarm: RefreshAlarm by lazy { RefreshAlarm(appContext) }

    val notificationUpdater: NotificationUpdater by lazy {
        NotificationUpdater(appContext, database.taskDao(), notificationBuilder, refreshAlarm)
    }

    val notificationController: TaskNotificationController by lazy {
        TaskNotificationController(appContext, database.taskDao(), settings, notificationUpdater)
    }

    val taskRepository: TaskRepository by lazy {
        TaskRepository(database, notificationController)
    }
}

val Context.appContainer: AppContainer
    get() = (applicationContext as SRTodoApplication).container
