package com.srtodo.app.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.srtodo.app.appContainer

/**
 * 端末の再起動後（BOOT_COMPLETED）とアプリの更新後（MY_PACKAGE_REPLACED）に通知を出し直す（固定表示ならサービスを開始する）。
 * 時刻やタイムゾーンが変わったとき（TIME_SET / TIMEZONE_CHANGED）も、期限切れの判定とアラームを作り直す。
 * どれもバックグラウンドからフォアグラウンドサービスを開始してよい例外に当たる。未完了が0件なら通知は出さない。
 */
class NotificationRestoreReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // システムからのブロードキャストを受けるため exported="true"。想定外のアクションは無視する
        if (intent.action !in HANDLED_ACTIONS) return
        Log.d(TAG, "onReceive: ${intent.action}")
        val controller = context.appContainer.notificationController
        launchAsync(TAG) { controller.refresh() }
    }

    private companion object {
        const val TAG = "NotificationRestore"
        val HANDLED_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
        )
    }
}
