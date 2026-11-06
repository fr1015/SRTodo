package com.srtodo.app.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.srtodo.app.appContainer

/**
 * 次のタスクの期限が来たとき、または日付が切り替わったとき（AlarmManager）に呼ばれ、通知を作り直す。
 * 期限切れの赤字表示に切り替えるのと、通知を新しくして通知欄の上に戻すのが目的。
 * アラームの登録は、通知を作り直すたびに RefreshAlarm で行う。
 */
class OverdueRefreshReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "onReceive: 期限が来たので通知を作り直す")
        val controller = context.appContainer.notificationController
        launchAsync(TAG) { controller.refresh() }
    }

    private companion object {
        const val TAG = "OverdueRefresh"
    }
}
