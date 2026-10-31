package com.srtodo.app.data

/**
 * タスクが書き換わったときに TaskRepository から呼ばれる。
 * 本番では NotificationUpdater が実装して通知を作り直す。テストでは呼ばれた回数を数える偽物に差し替える。
 */
fun interface TaskChangeListener {
    suspend fun onTasksChanged()
}
