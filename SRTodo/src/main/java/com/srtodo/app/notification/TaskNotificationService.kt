package com.srtodo.app.notification

import android.Manifest
import android.app.ForegroundServiceStartNotAllowedException
import android.app.Notification
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.srtodo.app.appContainer
import com.srtodo.app.data.Task
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

/**
 * 常駐通知を「色付きのフォアグラウンドサービスの通知」として持ち、通知欄の一番上の枠に固定する。
 *
 * サービス自体は何もせず待つだけ。通知を作り直すのは次のときだけにして、端末の負担を抑える。
 * - Room の Flow が変化したとき（追加・完了・編集・削除）
 * - TaskNotificationController.refreshTicks が進んだとき（期限・日付のアラーム、時刻の変更、スワイプで消されたとき）
 * ループ・定期的な確認・WakeLock・ACTION_TIME_TICK は使わない。未完了が0件になったら自分で止まる。
 */
class TaskNotificationService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var collectJob: Job? = null

    private val container get() = appContainer

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        container.notificationController.serviceRunning = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // startForegroundService() のあとは数秒以内に startForeground() が必要なので、最初の通知はその場で作る。
        // 読むのは最大3件と件数だけなので、ブロックする時間はごく短い
        val (tasks, count) = runBlocking(Dispatchers.IO) {
            container.database.taskDao().let { it.getPendingTop(TaskNotificationBuilder.MAX_EXPANDED) to it.countPending() }
        }
        if (tasks.isEmpty() || !container.settings.pinNotification) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (!enterForeground(buildPinned(tasks, count))) {
            stopSelf()
            // 通常の通知で代用する（プロセスごと止まらないよう、サービスのスコープとは別に動かす）
            launchFallback()
            return START_NOT_STICKY
        }
        if (collectJob == null) collectJob = scope.launch { observe() }
        // システムに止められても、余裕ができたら再開してもらう
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        container.notificationController.serviceRunning = false
        super.onDestroy()
    }

    private suspend fun observe() {
        val dao = container.database.taskDao()
        combine(
            dao.observePendingTop(TaskNotificationBuilder.MAX_EXPANDED).distinctUntilChanged(),
            dao.observePendingCount().distinctUntilChanged(),
            container.notificationController.refreshTicks,
        ) { tasks, count, _ -> tasks to count }
            .collect { (tasks, count) -> render(tasks, count) }
    }

    private suspend fun render(tasks: List<Task>, count: Int) {
        val alarm = container.refreshAlarm
        when {
            !container.settings.pinNotification -> {
                // 設定がオフになった：通知は残したまま（DETACH）サービスを抜け、通常の通知として作り直す
                ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_DETACH)
                withContext(NonCancellable) { container.notificationUpdater.update() }
                stopSelf()
            }
            tasks.isEmpty() -> {
                alarm.cancel()
                ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            else -> {
                val now = System.currentTimeMillis()
                alarm.schedule(container.database.taskDao().getNextDueAfter(now), now)
                // 同じ ID で notify() すると、フォアグラウンドサービスの通知のまま中身だけ置き換わる
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED
                ) {
                    NotificationManagerCompat.from(this)
                        .notify(TaskNotificationBuilder.NOTIFICATION_ID, buildPinned(tasks, count, now))
                }
            }
        }
    }

    private fun buildPinned(tasks: List<Task>, count: Int, now: Long = System.currentTimeMillis()): Notification =
        container.notificationBuilder.build(tasks, count, now, pinned = true)

    private fun enterForeground(notification: Notification): Boolean = try {
        // specialUse は Android 14 以降の種類。それより前は種類を指定しない
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        ServiceCompat.startForeground(this, TaskNotificationBuilder.NOTIFICATION_ID, notification, type)
        true
    } catch (e: ForegroundServiceStartNotAllowedException) {
        Log.w(TAG, "startForeground が許可されなかったため、通常の通知で表示します", e)
        false
    }

    private fun launchFallback() {
        val updater = container.notificationUpdater
        CoroutineScope(Dispatchers.IO).launch { updater.update() }
    }

    private companion object {
        const val TAG = "TaskNotificationService"
    }
}
