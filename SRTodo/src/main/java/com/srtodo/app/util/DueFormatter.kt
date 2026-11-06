package com.srtodo.app.util

import android.content.Context
import com.srtodo.app.R
import com.srtodo.app.data.Task
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** 期限の表示文字列。通知とアプリ画面で共通 */
object DueFormatter {
    private val FORMATTER = DateTimeFormatter.ofPattern("M/d(E) HH:mm", Locale.JAPAN)

    fun formatDateTime(dueAt: Long): String =
        Instant.ofEpochMilli(dueAt).atZone(ZoneId.systemDefault()).format(FORMATTER)

    /** 「期限なし」「10/31(土) 15:00」「期限切れ 10/31(土) 10:00」のいずれか */
    fun format(context: Context, task: Task, now: Long): String {
        val dueAt = task.dueAt ?: return context.getString(R.string.due_none)
        val text = formatDateTime(dueAt)
        return if (task.isOverdue(now)) context.getString(R.string.due_overdue, text) else text
    }
}
