package com.srtodo.app.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** 並び順・繰り上がりなど、DAO の SQL をエミュレータ上の SQLite（インメモリ）で確かめる */
@RunWith(AndroidJUnit4::class)
class TaskDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: TaskDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            AppDatabase::class.java,
        ).build()
        dao = db.taskDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    /** タイトルの順に並べて返す。並び順の確認に使う */
    private suspend fun pendingTitles(limit: Int = 100) = dao.getPendingTop(limit).map { it.title }

    private suspend fun insert(title: String, dueAt: Long?, createdAt: Long = NOW, isDone: Boolean = false): Long =
        dao.insert(
            Task(
                title = title,
                dueAt = dueAt,
                createdAt = createdAt,
                isDone = isDone,
                completedAt = if (isDone) NOW else null,
            )
        )

    // --- 並び順 ---

    @Test
    fun 期限の近い順に並び_期限なしは最後() = runTest {
        // わざとバラバラの順で入れる
        insert("期限なし", dueAt = null)
        insert("3日後", dueAt = NOW + 3 * DAY)
        insert("期限切れ", dueAt = NOW - HOUR)
        insert("1時間後", dueAt = NOW + HOUR)

        assertEquals(listOf("期限切れ", "1時間後", "3日後", "期限なし"), pendingTitles())
    }

    @Test
    fun 期限なし同士は作成が早い順() = runTest {
        insert("後に作成", dueAt = null, createdAt = NOW + 10)
        insert("先に作成", dueAt = null, createdAt = NOW)

        assertEquals(listOf("先に作成", "後に作成"), pendingTitles())
    }

    @Test
    fun 同じ期限なら作成が早い順_作成時刻も同じならID順() = runTest {
        insert("同期限・後に作成", dueAt = NOW + HOUR, createdAt = NOW + 10)
        insert("同期限・同時作成A", dueAt = NOW + HOUR, createdAt = NOW)
        insert("同期限・同時作成B", dueAt = NOW + HOUR, createdAt = NOW)

        assertEquals(
            listOf("同期限・同時作成A", "同期限・同時作成B", "同期限・後に作成"),
            pendingTitles(),
        )
    }

    @Test
    fun 上位3件だけ取れる() = runTest {
        (1..5).forEach { insert("タスク$it", dueAt = NOW + it * HOUR) }

        assertEquals(listOf("タスク1", "タスク2", "タスク3"), pendingTitles(limit = 3))
    }

    @Test
    fun 完了済みは未完了の一覧と件数に含まれない() = runTest {
        insert("完了済み", dueAt = NOW - HOUR, isDone = true)
        insert("未完了", dueAt = NOW + HOUR)

        assertEquals(listOf("未完了"), pendingTitles())
        assertEquals(1, dao.countPending())
        assertEquals(listOf("未完了"), dao.observePending().first().map { it.title })
    }

    @Test
    fun 画面用の未完了一覧も通知と同じ並び() = runTest {
        insert("期限なし", dueAt = null)
        insert("2時間後", dueAt = NOW + 2 * HOUR)
        insert("1時間後", dueAt = NOW + HOUR)

        assertEquals(pendingTitles(), dao.observePending().first().map { it.title })
    }

    // --- 完了と繰り上がり ---

    @Test
    fun 完了すると4件目が上位3件に繰り上がる() = runTest {
        val first = insert("1番目", dueAt = NOW + 1 * HOUR)
        insert("2番目", dueAt = NOW + 2 * HOUR)
        insert("3番目", dueAt = NOW + 3 * HOUR)
        insert("4番目", dueAt = NOW + 4 * HOUR)
        assertEquals(listOf("1番目", "2番目", "3番目"), pendingTitles(limit = 3))

        dao.markDone(first, completedAt = NOW)

        assertEquals(listOf("2番目", "3番目", "4番目"), pendingTitles(limit = 3))
        assertEquals(3, dao.countPending())
    }

    @Test
    fun 真ん中を完了しても順番が崩れない() = runTest {
        insert("1番目", dueAt = NOW + 1 * HOUR)
        val second = insert("2番目", dueAt = NOW + 2 * HOUR)
        insert("3番目", dueAt = NOW + 3 * HOUR)
        insert("期限なし", dueAt = null)

        dao.markDone(second, completedAt = NOW)

        assertEquals(listOf("1番目", "3番目", "期限なし"), pendingTitles(limit = 3))
    }

    @Test
    fun 完了すると完了日時が入り_もう一度完了しても変わらない() = runTest {
        val id = insert("タスク", dueAt = null)

        assertEquals(1, dao.markDone(id, completedAt = NOW))
        val done = dao.getById(id)!!
        assertTrue(done.isDone)
        assertEquals(NOW, done.completedAt)

        // 通知の連打などで2回届いても、最初の完了日時を上書きしない
        assertEquals(0, dao.markDone(id, completedAt = NOW + HOUR))
        assertEquals(NOW, dao.getById(id)!!.completedAt)
    }

    @Test
    fun 未完了に戻すと完了日時が消えて元の位置に並ぶ() = runTest {
        insert("1番目", dueAt = NOW + 1 * HOUR)
        val second = insert("2番目", dueAt = NOW + 2 * HOUR)
        insert("3番目", dueAt = NOW + 3 * HOUR)
        dao.markDone(second, completedAt = NOW)

        assertEquals(1, dao.markUndone(second))

        val restored = dao.getById(second)!!
        assertFalse(restored.isDone)
        assertNull(restored.completedAt)
        assertEquals(listOf("1番目", "2番目", "3番目"), pendingTitles())
    }

    @Test
    fun 完了済み一覧は完了が新しい順() = runTest {
        val a = insert("先に完了", dueAt = null)
        val b = insert("後に完了", dueAt = null)
        dao.markDone(a, completedAt = NOW)
        dao.markDone(b, completedAt = NOW + HOUR)

        assertEquals(listOf("後に完了", "先に完了"), dao.observeCompleted().first().map { it.title })
    }

    // --- 削除 ---

    @Test
    fun 削除すると次のタスクが繰り上がる() = runTest {
        val first = insert("1番目", dueAt = NOW + 1 * HOUR)
        insert("2番目", dueAt = NOW + 2 * HOUR)

        assertEquals(1, dao.deleteById(first))

        assertNull(dao.getById(first))
        assertEquals(listOf("2番目"), pendingTitles())
    }

    @Test
    fun 全部なくなると上位も件数も空() = runTest {
        val id = insert("最後の1件", dueAt = null)
        dao.markDone(id, completedAt = NOW)

        assertTrue(pendingTitles().isEmpty())
        assertEquals(0, dao.countPending())
    }

    // --- 期限切れ表示の更新アラーム用 ---

    @Test
    fun 次の期限は未来で一番近いもの_過去と完了済みと期限なしは除く() = runTest {
        insert("期限切れ", dueAt = NOW - HOUR)
        insert("完了済み・30分後", dueAt = NOW + 30 * MINUTE, isDone = true)
        insert("期限なし", dueAt = null)
        insert("2時間後", dueAt = NOW + 2 * HOUR)
        insert("1時間後", dueAt = NOW + HOUR)

        assertEquals(NOW + HOUR, dao.getNextDueAfter(NOW))
    }

    @Test
    fun 未来の期限がなければ次の期限はnull() = runTest {
        insert("期限切れ", dueAt = NOW - HOUR)
        insert("期限なし", dueAt = null)

        assertNull(dao.getNextDueAfter(NOW))
    }

    @Test
    fun ちょうど今が期限のものも次の期限に含む() = runTest {
        // isOverdue は dueAt < now のため、dueAt == now の瞬間はまだ赤字ではない。
        // ここで対象から外すと、このタスクを赤字にするアラームが登録されないままになる
        insert("ちょうど今", dueAt = NOW)
        insert("1時間後", dueAt = NOW + HOUR)

        assertEquals(NOW, dao.getNextDueAfter(NOW))
    }

    // --- 常駐サービス用の Flow ---

    @Test
    fun 上位の_Flow_は_getPendingTop_と同じ並びで3件() = runTest {
        insert("期限なし", dueAt = null)
        insert("3日後", dueAt = NOW + 3 * DAY)
        insert("期限切れ", dueAt = NOW - HOUR)
        insert("1時間後", dueAt = NOW + HOUR)

        val observed = dao.observePendingTop(3).first().map { it.title }
        assertEquals(listOf("期限切れ", "1時間後", "3日後"), observed)
        assertEquals(pendingTitles(3), observed)
    }

    @Test
    fun 完了すると上位の_Flow_に4件目が繰り上がって流れる() = runTest {
        val first = insert("1時間後", dueAt = NOW + HOUR)
        insert("2時間後", dueAt = NOW + 2 * HOUR)
        insert("3時間後", dueAt = NOW + 3 * HOUR)
        insert("期限なし", dueAt = null)

        dao.markDone(first, NOW)

        assertEquals(
            listOf("2時間後", "3時間後", "期限なし"),
            dao.observePendingTop(3).first().map { it.title },
        )
        assertEquals(3, dao.observePendingCount().first())
    }

    @Test
    fun 件数の_Flow_は未完了だけを数える() = runTest {
        insert("未完了1", dueAt = null)
        insert("未完了2", dueAt = NOW + HOUR)
        insert("完了済み", dueAt = NOW, isDone = true)

        assertEquals(2, dao.observePendingCount().first())
    }

    @Test
    fun 全部完了すると上位の_Flow_は空になる() = runTest {
        val id = insert("1件だけ", dueAt = null)
        dao.markDone(id, NOW)

        assertTrue(dao.observePendingTop(3).first().isEmpty())
        assertEquals(0, dao.observePendingCount().first())
    }

    private companion object {
        const val NOW = 1_800_000_000_000L
        const val MINUTE = 60_000L
        const val HOUR = 60 * MINUTE
        const val DAY = 24 * HOUR
    }
}
