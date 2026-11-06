package com.srtodo.app.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.srtodo.app.appContainer

/**
 * 通知がユーザーに消されたとき（setDeleteIntent）に呼ばれ、通知を出し直す。
 * Android 14 以降は ongoing 通知もスワイプで消せるため必要。
 * 未完了が0件なら通知を出さないので、再投稿もされない。
 * アプリ自身が cancel() したときは呼ばれない。
 */
class NotificationDismissedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "onReceive: 通知が消されたので出し直す")
        val controller = context.appContainer.notificationController
        launchAsync(TAG) { controller.refresh() }
    }

    private companion object {
        const val TAG = "NotificationDismissed"
    }
}
