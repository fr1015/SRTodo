package com.srtodo.app.notification

import android.content.BroadcastReceiver
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

/**
 * DB はメインスレッドで触れないため、goAsync() で Receiver を延命してバックグラウンドで処理する。
 * goAsync() の猶予は約10秒なので、重い処理は入れないこと。
 */
internal fun BroadcastReceiver.launchAsync(tag: String, block: suspend () -> Unit) {
    val pendingResult = goAsync()
    receiverScope.launch {
        try {
            block()
        } catch (e: Exception) {
            Log.e(tag, "Receiver の処理に失敗しました", e)
        } finally {
            pendingResult.finish()
        }
    }
}
