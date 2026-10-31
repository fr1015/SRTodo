package com.srtodo.app.notification

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.srtodo.app.MainActivity
import com.srtodo.app.R
import com.srtodo.app.data.Task
import com.srtodo.app.data.TaskDao
import com.srtodo.app.util.DueFormatter
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * DB の内容から常駐通知を作り直す。
 * 折りたたみ時は1件、展開時は最大3件を RemoteViews で表示する。0件なら通知を消す。
 * 次の期限が来たら作り直せるよう、アラームの登録もここで行う。
 * DB は読むだけで、書き込みは TaskRepository が行う。
 */
class NotificationUpdater(
    context: Context,
    private val dao: TaskDao,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val context = context.applicationContext

    /** 連続した書き込みで通知の更新が前後しないよう、1つずつ順番に行う */
    private val mutex = Mutex()

    fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_channel_name),
            // DEFAULT にして「サイレント」欄に入らないようにし、音とバイブは切る
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.notification_channel_description)
            setSound(null, null)
            enableVibration(false)
            setShowBadge(false)
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    suspend fun update() = mutex.withLock {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return@withLock

        val now = clock()
        scheduleOverdueRefresh(dao.getNextDueAfter(now))

        val manager = NotificationManagerCompat.from(context)
        val tasks = dao.getPendingTop(MAX_EXPANDED)
        if (tasks.isEmpty()) {
            manager.cancel(NOTIFICATION_ID)
            return@withLock
        }
        val pendingCount = dao.countPending()

        val openApp = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        // スワイプなどで消されたら NotificationDismissedReceiver で出し直す
        val onDismiss = PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, NotificationDismissedReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomContentView(buildList(tasks.take(MAX_COLLAPSED), now))
            .setCustomBigContentView(buildList(tasks, now))
            .setSubText(context.getString(R.string.notification_remaining, pendingCount))
            .setContentIntent(openApp)
            .setDeleteIntent(onDismiss)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(false)
            .build()

        manager.notify(NOTIFICATION_ID, notification)
    }

    /**
     * 次の期限の直後に OverdueRefreshReceiver が呼ばれるようにする。期限が無ければ解除する。
     * 端末を起こさない RTC アラームなので権限は不要。スリープ中なら次に画面が点いたときに届く。
     */
    private fun scheduleOverdueRefresh(nextDueAt: Long?) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, OverdueRefreshReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        if (nextDueAt == null) {
            alarmManager.cancel(pendingIntent)
        } else {
            // isOverdue は dueAt < now で判定するため、少し後にする
            alarmManager.set(AlarmManager.RTC, nextDueAt + OVERDUE_MARGIN_MILLIS, pendingIntent)
        }
    }

    private fun buildList(tasks: List<Task>, now: Long): RemoteViews {
        val list = RemoteViews(context.packageName, R.layout.notification_task_list)
        list.removeAllViews(R.id.task_container)
        tasks.forEach { list.addView(R.id.task_container, buildRow(it, now)) }
        return list
    }

    private fun buildRow(task: Task, now: Long): RemoteViews {
        val row = RemoteViews(context.packageName, R.layout.notification_task_row)
        row.setTextViewText(R.id.task_title, task.title)
        row.setTextViewText(R.id.task_due, DueFormatter.format(context, task, now))

        if (task.isOverdue(now)) {
            // 通知側のコンテキストで色リソースを解決させ、ライト／ダークを自動で切り替える（API 31以降）
            row.setColorStateList(R.id.task_title, "setTextColor", R.color.task_overdue)
            row.setColorStateList(R.id.task_due, "setTextColor", R.color.task_overdue)
        }

        row.setCompoundButtonChecked(R.id.task_check, false)
        // チェック状態は RemoteViews.EXTRA_CHECKED として fill-in されるため、PendingIntent は MUTABLE が必須
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            task.id.toInt(),
            Intent(context, TaskCheckReceiver::class.java)
                .putExtra(TaskCheckReceiver.EXTRA_TASK_ID, task.id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
        row.setOnCheckedChangeResponse(
            R.id.task_check,
            RemoteViews.RemoteResponse.fromPendingIntent(pendingIntent),
        )
        return row
    }

    companion object {
        const val CHANNEL_ID = "tasks"
        private const val NOTIFICATION_ID = 1
        private const val MAX_EXPANDED = 3
        private const val MAX_COLLAPSED = 1
        private const val OVERDUE_MARGIN_MILLIS = 1_000L
    }
}
