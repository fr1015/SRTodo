package com.srtodo.app.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.srtodo.app.appContainer

/**
 * 端末の再起動後（BOOT_COMPLETED）とアプリの更新後（MY_PACKAGE_REPLACED）に通知を出し直す。
 * 未完了が0件なら NotificationUpdater が通知を出さない。
 */
class NotificationRestoreReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // システムからのブロードキャストを受けるため exported="true"。想定外のアクションは無視する
        if (intent.action !in HANDLED_ACTIONS) return
        Log.d(TAG, "onReceive: ${intent.action}")
        val updater = context.appContainer.notificationUpdater
        launchAsync(TAG) { updater.update() }
    }

    private companion object {
        const val TAG = "NotificationRestore"
        val HANDLED_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
        )
    }
}
