package com.srtodo.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * 並び順は「期限ありを期限の近い順 → 期限なし」。同じ期限なら作成が早い順。
 * 書き込み系は Repository からのみ呼ぶこと（通知の作り直しを漏らさないため）。
 */
@Dao
interface TaskDao {

    /** 通知用：未完了のタスクを期限の近い順に上位 [limit] 件 */
    @Query(
        """
        SELECT * FROM tasks
        WHERE isDone = 0
        ORDER BY dueAt IS NULL, dueAt ASC, createdAt ASC, id ASC
        LIMIT :limit
        """
    )
    suspend fun getPendingTop(limit: Int): List<Task>

    /** 常駐サービス用：[getPendingTop] と同じ内容を、変化するたびに流す */
    @Query(
        """
        SELECT * FROM tasks
        WHERE isDone = 0
        ORDER BY dueAt IS NULL, dueAt ASC, createdAt ASC, id ASC
        LIMIT :limit
        """
    )
    fun observePendingTop(limit: Int): Flow<List<Task>>

    @Query("SELECT COUNT(*) FROM tasks WHERE isDone = 0")
    suspend fun countPending(): Int

    /** 常駐サービス用：未完了の件数（「残り N 件」の表示に使う） */
    @Query("SELECT COUNT(*) FROM tasks WHERE isDone = 0")
    fun observePendingCount(): Flow<Int>

    /**
     * 未完了タスクのうち、まだ期限切れになっていない（dueAt >= now）最も近い期限。期限切れ表示を更新するアラームに使う。
     * Task.isOverdue は dueAt < now なので、dueAt == now のタスクもこれから期限切れになる対象に含める。
     */
    @Query("SELECT MIN(dueAt) FROM tasks WHERE isDone = 0 AND dueAt >= :now")
    suspend fun getNextDueAfter(now: Long): Long?

    /** アプリ画面用：未完了のタスク（通知と同じ並び） */
    @Query(
        """
        SELECT * FROM tasks
        WHERE isDone = 0
        ORDER BY dueAt IS NULL, dueAt ASC, createdAt ASC, id ASC
        """
    )
    fun observePending(): Flow<List<Task>>

    /** アプリ画面用：完了済みのタスク（完了が新しい順） */
    @Query("SELECT * FROM tasks WHERE isDone = 1 ORDER BY completedAt DESC, id DESC")
    fun observeCompleted(): Flow<List<Task>>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getById(id: Long): Task?

    @Insert
    suspend fun insert(task: Task): Long

    @Insert
    suspend fun insertAll(tasks: List<Task>)

    @Update
    suspend fun update(task: Task)

    @Query("UPDATE tasks SET isDone = 1, completedAt = :completedAt WHERE id = :id AND isDone = 0")
    suspend fun markDone(id: Long, completedAt: Long): Int

    @Query("UPDATE tasks SET isDone = 0, completedAt = NULL WHERE id = :id AND isDone = 1")
    suspend fun markUndone(id: Long): Int

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun deleteById(id: Long): Int

    @Query("DELETE FROM tasks")
    suspend fun deleteAll()
}
