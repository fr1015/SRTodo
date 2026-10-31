package com.sptodo.app.notification

import java.util.concurrent.TimeUnit

/** 試作用のタスク。Phase 2 で Room の Task エンティティに置き換える */
data class PrototypeTask(
    val id: Long,
    val title: String,
    val dueAt: Long?,
)

/**
 * Phase 1 の試作用の固定データ（メモリ上のみ）。
 * プロセスが終了すると初期状態に戻る。Phase 2 で Repository に置き換える。
 */
object PrototypeTasks {
    private val tasks = mutableListOf<PrototypeTask>()

    init {
        reset()
    }

    /** 固定データを初期状態に戻す。期限は現在時刻からの相対値で作る */
    @Synchronized
    fun reset() {
        val now = System.currentTimeMillis()
        val hour = TimeUnit.HOURS.toMillis(1)
        val day = TimeUnit.DAYS.toMillis(1)
        tasks.clear()
        tasks += listOf(
            PrototypeTask(1, "請求書を送る（期限切れ）", now - 2 * hour),
            PrototypeTask(2, "牛乳を買う", now + 3 * hour),
            PrototypeTask(3, "歯医者の予約を電話で取る", now + day),
            PrototypeTask(4, "部屋の掃除", now + 3 * day),
            PrototypeTask(5, "本を読む（期限なし）", null),
            PrototypeTask(6, "とても長いタイトルのタスクが通知の中でどのように省略されるかを確認するためのテスト", null),
        )
    }

    /** 未完了のタスクを期限の近い順に返す（期限なしは最後）。DAO の ORDER BY dueAt IS NULL, dueAt ASC と同じ並び */
    @Synchronized
    fun pending(): List<PrototypeTask> =
        tasks.sortedWith(compareBy<PrototypeTask> { it.dueAt == null }.thenBy { it.dueAt })

    @Synchronized
    fun complete(id: Long) {
        tasks.removeAll { it.id == id }
    }
}
