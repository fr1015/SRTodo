package com.srtodo.app.notification

import android.Manifest
import android.app.ForegroundServiceStartNotAllowedException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.srtodo.app.data.TaskChangeListener
import com.srtodo.app.data.TaskDao
import com.srtodo.app.settings.AppSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * 常駐通知の窓口。設定に応じて、固定表示（TaskNotificationService）か通常の常駐通知（NotificationUpdater）を使い分ける。
 * Receiver・画面・Repository はここだけを呼ぶ。
 */
class TaskNotificationController(
    context: Context,
    private val dao: TaskDao,
    private val settings: AppSettings,
    private val updater: NotificationUpdater,
) : TaskChangeListener {
    private val context = context.applicationContext

    /** サービスが動いているか。サービス自身が onCreate / onDestroy で切り替える（同じプロセス内でだけ使う） */
    @Volatile
    internal var serviceRunning = false

    private val _refreshTicks = MutableStateFlow(0L)

    /** 動いているサービスに作り直しを頼むための合図。データが変わらなくても表示を作り直したいとき（期限・日付・時刻の変更、スワイプ）に使う */
    internal val refreshTicks: StateFlow<Long> = _refreshTicks

    override suspend fun onTasksChanged() {
        // サービスが動いていれば、Room の Flow で変化を受け取るので何もしない
        if (serviceRunning) return
        refresh()
    }

    /** 現在の設定とタスクに合わせて、通知を出し直す（サービスの開始・停止も含む） */
    suspend fun refresh() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return

        when {
            // 動いているサービスに任せる（設定がオフになっていれば、サービスが自分で通常の通知に切り替えて止まる）
            serviceRunning -> _refreshTicks.update { it + 1 }
            !settings.pinNotification -> updater.update()
            // 0件なら開始しない。残っている通知とアラームは通常の更新で消す
            dao.countPending() == 0 -> updater.update()
            !startService() -> updater.update()
        }
    }

    suspend fun setPinNotification(enabled: Boolean) {
        settings.pinNotification = enabled
        refresh()
    }

    /** @return 開始を頼めたら true。バックグラウンドからの開始が禁止されている状況なら false */
    private fun startService(): Boolean = try {
        ContextCompat.startForegroundService(context, Intent(context, TaskNotificationService::class.java))
        true
    } catch (e: ForegroundServiceStartNotAllowedException) {
        // 正確でないアラームなど、バックグラウンドからの開始が認められていない場面。通常の通知で代用する
        Log.w(TAG, "フォアグラウンドサービスを開始できないため、通常の通知で表示します", e)
        false
    }

    private companion object {
        const val TAG = "TaskNotification"
    }
}
