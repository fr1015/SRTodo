package com.srtodo.app.data

import java.util.concurrent.TimeUnit

/** 動作確認用のサンプルデータ。期限は現在時刻からの相対値で作る。Phase 4 のアプリ画面ができたら外す */
object SampleTasks {
    fun create(now: Long): List<Task> {
        val hour = TimeUnit.HOURS.toMillis(1)
        val day = TimeUnit.DAYS.toMillis(1)
        // createdAt を少しずつずらし、同じ期限（期限なし）のときの並びを確認できるようにする
        return listOf(
            Task(title = "本を読む（期限なし）", dueAt = null, createdAt = now),
            Task(title = "部屋の掃除", dueAt = now + 3 * day, createdAt = now + 1),
            Task(title = "牛乳を買う", dueAt = now + 3 * hour, createdAt = now + 2),
            Task(title = "請求書を送る（期限切れ）", dueAt = now - 2 * hour, createdAt = now + 3),
            Task(title = "とても長いタイトルのタスクが通知の中でどのように省略されるかを確認するためのテスト", dueAt = null, createdAt = now + 4),
            Task(title = "歯医者の予約を電話で取る", dueAt = now + day, createdAt = now + 5),
        )
    }
}
