package com.srtodo.app.data

import androidx.room.withTransaction
import com.srtodo.app.notification.NotificationUpdater
import kotlinx.coroutines.flow.Flow

/**
 * タスクの読み書きの窓口。DB への書き込みはここに限定し、書き込みのたびに通知を作り直す。
 */
class TaskRepository(
    private val database: AppDatabase,
    private val notificationUpdater: NotificationUpdater,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val dao = database.taskDao()

    fun observePending(): Flow<List<Task>> = dao.observePending()

    fun observeCompleted(): Flow<List<Task>> = dao.observeCompleted()

    suspend fun getById(id: Long): Task? = dao.getById(id)

    suspend fun add(title: String, dueAt: Long?): Long = writeAndNotify {
        dao.insert(Task(title = title, dueAt = dueAt, createdAt = clock()))
    }

    /** タイトルと期限を変更する（完了状態は [complete] / [uncomplete] で変える） */
    suspend fun edit(id: Long, title: String, dueAt: Long?) = writeAndNotify {
        val current = dao.getById(id) ?: return@writeAndNotify
        dao.update(current.copy(title = title, dueAt = dueAt))
    }

    suspend fun complete(id: Long) = writeAndNotify {
        dao.markDone(id, clock())
    }

    suspend fun uncomplete(id: Long) = writeAndNotify {
        dao.markUndone(id)
    }

    suspend fun delete(id: Long) = writeAndNotify {
        dao.deleteById(id)
    }

    /** 動作確認用：全タスクを消してサンプルデータを入れ直す。Phase 4 のアプリ画面ができたら外す */
    suspend fun replaceWithSamples() = writeAndNotify {
        database.withTransaction {
            dao.deleteAll()
            dao.insertAll(SampleTasks.create(clock()))
        }
    }

    private suspend fun <T> writeAndNotify(block: suspend () -> T): T {
        val result = block()
        notificationUpdater.update()
        return result
    }
}
