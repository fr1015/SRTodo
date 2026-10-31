package com.srtodo.app.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.srtodo.app.appContainer

/**
 * 次のタスクの期限が来たとき（AlarmManager）に呼ばれ、通知を作り直して期限切れの赤字表示にする。
 * アラームの登録は NotificationUpdater.update() が毎回行う。
 */
class OverdueRefreshReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "onReceive: 期限が来たので通知を作り直す")
        val updater = context.appContainer.notificationUpdater
        launchAsync(TAG) { updater.update() }
    }

    private companion object {
        const val TAG = "OverdueRefresh"
    }
}
