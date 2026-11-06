package com.srtodo.app.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Repository の書き込みと「書き込みのたびに通知を作り直す」ことを確かめる。
 * 本物の TaskNotificationController の代わりに呼ばれた回数を数える偽物を渡すので、エミュレータに通知は出ない。
 */
@RunWith(AndroidJUnit4::class)
class TaskRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: TaskDao
    private lateinit var repository: TaskRepository

    private var changedCount = 0
    private var now = NOW

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            AppDatabase::class.java,
        ).build()
        dao = db.taskDao()
        repository = TaskRepository(
            database = db,
            changeListener = { changedCount++ },
            clock = { now },
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun 追加すると作成日時が入り_通知の作り直しが1回呼ばれる() = runTest {
        val id = repository.add("牛乳を買う", dueAt = NOW + HOUR)

        val task = dao.getById(id)!!
        assertEquals("牛乳を買う", task.title)
        assertEquals(NOW + HOUR, task.dueAt)
        assertEquals(NOW, task.createdAt)
        assertFalse(task.isDone)
        assertEquals(1, changedCount)
    }

    @Test
    fun 完了すると完了日時が入り_次のタスクが繰り上がる() = runTest {
        val first = repository.add("1番目", dueAt = NOW + 1 * HOUR)
        repository.add("2番目", dueAt = NOW + 2 * HOUR)
        repository.add("3番目", dueAt = NOW + 3 * HOUR)
        repository.add("4番目", dueAt = NOW + 4 * HOUR)
        changedCount = 0
        now = NOW + MINUTE

        repository.complete(first)

        assertEquals(NOW + MINUTE, dao.getById(first)!!.completedAt)
        assertEquals(listOf("2番目", "3番目", "4番目"), dao.getPendingTop(3).map { it.title })
        assertEquals(1, changedCount)
    }

    @Test
    fun 未完了に戻せる() = runTest {
        val id = repository.add("タスク", dueAt = null)
        repository.complete(id)
        changedCount = 0

        repository.uncomplete(id)

        val task = dao.getById(id)!!
        assertFalse(task.isDone)
        assertNull(task.completedAt)
        assertEquals(1, changedCount)
    }

    @Test
    fun 編集ではタイトルと期限だけ変わり_完了状態と作成日時は変わらない() = runTest {
        val id = repository.add("古いタイトル", dueAt = NOW + HOUR)
        repository.complete(id)
        val before = dao.getById(id)!!
        changedCount = 0
        now = NOW + DAY

        repository.edit(id, "新しいタイトル", dueAt = null)

        val after = dao.getById(id)!!
        assertEquals("新しいタイトル", after.title)
        assertNull(after.dueAt)
        assertTrue(after.isDone)
        assertEquals(before.createdAt, after.createdAt)
        assertEquals(before.completedAt, after.completedAt)
        assertEquals(1, changedCount)
    }

    @Test
    fun 期限を変えると並び順も変わる() = runTest {
        repository.add("A", dueAt = NOW + 1 * HOUR)
        val b = repository.add("B", dueAt = NOW + 2 * HOUR)

        repository.edit(b, "B", dueAt = NOW + 30 * MINUTE)

        assertEquals(listOf("B", "A"), dao.getPendingTop(3).map { it.title })
    }

    @Test
    fun 削除すると消えて_通知の作り直しが呼ばれる() = runTest {
        val id = repository.add("タスク", dueAt = null)
        changedCount = 0

        repository.delete(id)

        assertNull(dao.getById(id))
        assertEquals(1, changedCount)
    }

    @Test
    fun 最後の1件を完了しても通知の作り直しは呼ばれる() = runTest {
        // 0件になったときに通知を消すのは TaskNotificationController の役目なので、Repository は必ず知らせる
        val id = repository.add("最後の1件", dueAt = null)
        changedCount = 0

        repository.complete(id)

        assertEquals(0, dao.countPending())
        assertEquals(1, changedCount)
    }

    private companion object {
        const val NOW = 1_800_000_000_000L
        const val MINUTE = 60_000L
        const val HOUR = 60 * MINUTE
        const val DAY = 24 * HOUR
    }
}
