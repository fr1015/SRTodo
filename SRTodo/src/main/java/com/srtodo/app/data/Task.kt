package com.srtodo.app.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "tasks",
    indices = [Index(value = ["isDone", "dueAt"])],
)
data class Task(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    /** 期限（epoch millis）。null は期限なし */
    val dueAt: Long?,
    val isDone: Boolean = false,
    val createdAt: Long,
    val completedAt: Long? = null,
) {
    fun isOverdue(now: Long): Boolean = !isDone && dueAt != null && dueAt < now
}
