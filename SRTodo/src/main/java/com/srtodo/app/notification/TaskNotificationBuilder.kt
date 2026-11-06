package com.srtodo.app.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.srtodo.app.MainActivity
import com.srtodo.app.R
import com.srtodo.app.data.Task
import com.srtodo.app.util.DueFormatter

/**
 * 常駐通知の組み立てだけを行う（投稿はしない）。
 * 固定表示（フォアグラウンドサービス）と通常の常駐通知の両方で使う。
 * 折りたたみ時は1件、展開時は最大3件を RemoteViews で表示する。
 */
class TaskNotificationBuilder(context: Context) {
    private val context = context.applicationContext

    fun createChannel() {
        val manager = context.getSystemService(NotificationManager::class.java)
        // 過去のチャンネルは消す（重要度を変えたため ID を変えてきた）
        LEGACY_CHANNEL_IDS.forEach { manager.deleteNotificationChannel(it) }

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
        manager.createNotificationChannel(channel)
    }

    /**
     * @param tasks 期限の近い順の上位（最大 [MAX_EXPANDED] 件）。空でないこと
     * @param pinned true なら固定表示用（色付きにして、通知欄の一番上の枠に置かれるようにする）
     */
    fun build(tasks: List<Task>, pendingCount: Int, now: Long, pinned: Boolean): Notification {
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

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomContentView(buildList(tasks.take(MAX_COLLAPSED), now, pinned))
            .setCustomBigContentView(buildList(tasks, now, pinned))
            .setSubText(context.getString(R.string.notification_remaining, pendingCount))
            .setContentIntent(openApp)
            .setDeleteIntent(onDismiss)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            // 通常の常駐通知は新しい順に並ぶため、作り直すたびに when を更新して上に戻す（表示はしない）
            .setWhen(now)
            .setShowWhen(false)

        if (pinned) {
            builder
                // 色付きのフォアグラウンドサービスの通知は、通知欄の一番上の枠に置かれる
                .setColorized(true)
                .setColor(ContextCompat.getColor(context, R.color.notification_pinned))
                // 開始直後から通知を出す（既定では最大10秒遅れることがある）
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        }
        return builder.build()
    }

    private fun buildList(tasks: List<Task>, now: Long, pinned: Boolean): RemoteViews {
        val list = RemoteViews(context.packageName, R.layout.notification_task_list)
        list.removeAllViews(R.id.task_container)
        tasks.forEach { list.addView(R.id.task_container, buildRow(it, now, pinned)) }
        return list
    }

    private fun buildRow(task: Task, now: Long, pinned: Boolean): RemoteViews {
        val row = RemoteViews(context.packageName, R.layout.notification_task_row)
        row.setTextViewText(R.id.task_title, task.title)
        row.setTextViewText(R.id.task_due, DueFormatter.format(context, task, now))

        val overdue = task.isOverdue(now)
        if (pinned) {
            // 固定表示は背景色がライト／ダークで変わらないので、背景に合わせた色を直接指定する
            val titleColor = if (overdue) R.color.notification_pinned_overdue else R.color.notification_pinned_text
            val dueColor = if (overdue) R.color.notification_pinned_overdue else R.color.notification_pinned_text_secondary
            row.setTextColor(R.id.task_title, ContextCompat.getColor(context, titleColor))
            row.setTextColor(R.id.task_due, ContextCompat.getColor(context, dueColor))
            row.setColorStateList(R.id.task_check, "setButtonTintList", R.color.notification_pinned_text)
        } else if (overdue) {
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
        const val CHANNEL_ID = "task_list"

        /** 固定表示と通常の常駐通知で同じ ID を使い、切り替えたときに置き換わるようにする */
        const val NOTIFICATION_ID = 1
        const val MAX_EXPANDED = 3
        private const val MAX_COLLAPSED = 1

        /** 過去に使っていたチャンネル。起動時に削除する */
        private val LEGACY_CHANNEL_IDS = listOf("tasks", "tasks_top")
    }
}
