package com.sptodo.app.notification

import android.Manifest
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
import com.sptodo.app.MainActivity
import com.sptodo.app.R
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 常駐通知の組み立てと投稿を行う。
 * 折りたたみ時は1件、展開時は最大3件を RemoteViews で表示する。
 */
object TaskNotifier {
    const val CHANNEL_ID = "tasks"
    private const val NOTIFICATION_ID = 1
    private const val MAX_EXPANDED = 3
    private const val MAX_COLLAPSED = 1

    private val dueFormatter = DateTimeFormatter.ofPattern("M/d(E) HH:mm", Locale.JAPAN)

    fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_channel_name),
            // DEFAULT にして「サイレント」欄に追いやられないようにし、音とバイブは切る
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.notification_channel_description)
            setSound(null, null)
            enableVibration(false)
            setShowBadge(false)
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    /** 現在のタスクで通知を作り直す。0件なら通知を消す */
    fun show(context: Context) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return

        val manager = NotificationManagerCompat.from(context)
        val tasks = PrototypeTasks.pending()
        if (tasks.isEmpty()) {
            manager.cancel(NOTIFICATION_ID)
            return
        }

        val openApp = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomContentView(buildList(context, tasks.take(MAX_COLLAPSED)))
            .setCustomBigContentView(buildList(context, tasks.take(MAX_EXPANDED)))
            .setSubText(context.getString(R.string.notification_remaining, tasks.size))
            .setContentIntent(openApp)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(false)
            .build()

        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun buildList(context: Context, tasks: List<PrototypeTask>): RemoteViews {
        val list = RemoteViews(context.packageName, R.layout.notification_task_list)
        list.removeAllViews(R.id.task_container)
        tasks.forEach { list.addView(R.id.task_container, buildRow(context, it)) }
        return list
    }

    private fun buildRow(context: Context, task: PrototypeTask): RemoteViews {
        val row = RemoteViews(context.packageName, R.layout.notification_task_row)
        row.setTextViewText(R.id.task_title, task.title)
        row.setTextViewText(R.id.task_due, formatDue(context, task.dueAt))

        if (task.dueAt != null && task.dueAt < System.currentTimeMillis()) {
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

    private fun formatDue(context: Context, dueAt: Long?): String {
        if (dueAt == null) return context.getString(R.string.due_none)
        val text = Instant.ofEpochMilli(dueAt).atZone(ZoneId.systemDefault()).format(dueFormatter)
        return if (dueAt < System.currentTimeMillis()) {
            context.getString(R.string.due_overdue, text)
        } else {
            text
        }
    }
}
