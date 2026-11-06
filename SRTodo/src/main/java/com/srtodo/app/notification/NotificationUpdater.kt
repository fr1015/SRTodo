package com.srtodo.app.notification

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.srtodo.app.data.TaskDao
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 通常の常駐通知（フォアグラウンドサービスを使わない）を DB の内容から作り直す。
 * 「通知に固定表示する」がオフのときと、サービスを開始できなかったときの代わりに使う。0件なら通知を消す。
 * DB は読むだけで、書き込みは TaskRepository が行う。
 */
class NotificationUpdater(
    context: Context,
    private val dao: TaskDao,
    private val builder: TaskNotificationBuilder,
    private val alarm: RefreshAlarm,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val context = context.applicationContext

    /** 連続した書き込みで通知の更新が前後しないよう、1つずつ順番に行う */
    private val mutex = Mutex()

    suspend fun update() = mutex.withLock {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return@withLock

        val now = clock()
        val manager = NotificationManagerCompat.from(context)
        val tasks = dao.getPendingTop(TaskNotificationBuilder.MAX_EXPANDED)
        if (tasks.isEmpty()) {
            alarm.cancel()
            manager.cancel(TaskNotificationBuilder.NOTIFICATION_ID)
            return@withLock
        }
        alarm.schedule(dao.getNextDueAfter(now), now)
        val notification = builder.build(tasks, dao.countPending(), now, pinned = false)
        manager.notify(TaskNotificationBuilder.NOTIFICATION_ID, notification)
    }
}
