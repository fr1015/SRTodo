package com.srtodo.app.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.RemoteViews
import com.srtodo.app.appContainer

/** 通知内のチェックボックスが押されたときに呼ばれる */
class TaskCheckReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getLongExtra(EXTRA_TASK_ID, -1L)
        val checked = intent.getBooleanExtra(RemoteViews.EXTRA_CHECKED, false)
        Log.d(TAG, "onReceive: taskId=$taskId checked=$checked")
        if (taskId < 0 || !checked) return

        val repository = context.appContainer.taskRepository
        launchAsync(TAG) { repository.complete(taskId) }
    }

    companion object {
        const val EXTRA_TASK_ID = "com.srtodo.app.extra.TASK_ID"
        private const val TAG = "TaskCheckReceiver"
    }
}
