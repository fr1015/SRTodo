package com.srtodo.app.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.time.Instant
import java.time.ZoneId

/**
 * 通知を作り直すためのアラーム（OverdueRefreshReceiver を呼ぶ）を1本だけ管理する。
 * 端末を起こさない RTC で、正確なアラームの権限も使わない。スリープ中なら次に画面が点いたときに届く。
 */
class RefreshAlarm(context: Context) {
    private val context = context.applicationContext

    /** 「次の期限の直後」と「次の0時」の早い方に予約する（前の予約は置き換わる） */
    fun schedule(nextDueAt: Long?, now: Long) {
        alarmManager().set(AlarmManager.RTC, nextRefreshAt(nextDueAt, now), pendingIntent())
    }

    fun cancel() {
        alarmManager().cancel(pendingIntent())
    }

    private fun alarmManager() = context.getSystemService(AlarmManager::class.java)

    private fun pendingIntent() = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, OverdueRefreshReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        /** isOverdue は dueAt < now で判定するため、期限より少し後にする */
        private const val OVERDUE_MARGIN_MILLIS = 1_000L

        /** 次に通知を作り直す時刻。「次の期限の直後」と「次の0時（日付の切り替わり）」の早い方 */
        fun nextRefreshAt(nextDueAt: Long?, now: Long, zone: ZoneId = ZoneId.systemDefault()): Long {
            val nextMidnight = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
                .plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val afterDue = nextDueAt?.plus(OVERDUE_MARGIN_MILLIS) ?: return nextMidnight
            return minOf(afterDue, nextMidnight)
        }
    }
}
