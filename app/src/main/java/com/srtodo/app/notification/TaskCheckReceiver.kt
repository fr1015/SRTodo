package com.srtodo.app.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.RemoteViews
import com.srtodo.app.appContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** 通知内のチェックボックスが押されたときに呼ばれる */
class TaskCheckReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getLongExtra(EXTRA_TASK_ID, -1L)
        val checked = intent.getBooleanExtra(RemoteViews.EXTRA_CHECKED, false)
        Log.d(TAG, "onReceive: taskId=$taskId checked=$checked")
        if (taskId < 0 || !checked) return

        // DB はメインスレッドで触れないため、goAsync() で Receiver を延命してバックグラウンドで完了させる
        val pendingResult = goAsync()
        val repository = context.appContainer.taskRepository
        scope.launch {
            try {
                repository.complete(taskId)
            } catch (e: Exception) {
                Log.e(TAG, "タスクの完了に失敗しました: taskId=$taskId", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val EXTRA_TASK_ID = "com.srtodo.app.extra.TASK_ID"
        private const val TAG = "TaskCheckReceiver"
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
