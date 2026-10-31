package com.srtodo.app.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 期限切れの判定（通知とアプリ画面の赤字表示に使う）。Android に依存しないので JVM で動かす */
class TaskTest {

    private fun task(dueAt: Long?, isDone: Boolean = false) =
        Task(title = "タスク", dueAt = dueAt, isDone = isDone, createdAt = 0)

    @Test
    fun 期限を過ぎていれば期限切れ() {
        assertTrue(task(dueAt = NOW - 1).isOverdue(NOW))
    }

    @Test
    fun ちょうど期限の時刻はまだ期限切れではない() {
        assertFalse(task(dueAt = NOW).isOverdue(NOW))
    }

    @Test
    fun 期限前は期限切れではない() {
        assertFalse(task(dueAt = NOW + 1).isOverdue(NOW))
    }

    @Test
    fun 期限なしは期限切れにならない() {
        assertFalse(task(dueAt = null).isOverdue(NOW))
    }

    @Test
    fun 完了済みは期限を過ぎていても期限切れにならない() {
        assertFalse(task(dueAt = NOW - 1, isDone = true).isOverdue(NOW))
    }

    private companion object {
        const val NOW = 1_800_000_000_000L
    }
}
