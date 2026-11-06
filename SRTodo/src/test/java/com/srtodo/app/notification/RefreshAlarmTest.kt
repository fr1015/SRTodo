package com.srtodo.app.notification

import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

/** 次に通知を作り直す時刻（「次の期限の直後」と「次の0時」の早い方）。Android に依存しないので JVM で動かす */
class RefreshAlarmTest {

    private val zone = ZoneId.of("Asia/Tokyo")

    private fun at(text: String): Long = LocalDateTime.parse(text).atZone(zone).toInstant().toEpochMilli()

    private fun next(nextDueAt: Long?, now: Long) = RefreshAlarm.nextRefreshAt(nextDueAt, now, zone)

    @Test
    fun 期限がなければ次の0時() {
        assertEquals(at("2026-11-07T00:00"), next(nextDueAt = null, now = at("2026-11-06T15:00")))
    }

    @Test
    fun 今日の期限が先なら期限の1秒後() {
        assertEquals(
            at("2026-11-06T18:00:01"),
            next(nextDueAt = at("2026-11-06T18:00"), now = at("2026-11-06T15:00")),
        )
    }

    @Test
    fun 期限が明日以降なら先に0時() {
        assertEquals(
            at("2026-11-07T00:00"),
            next(nextDueAt = at("2026-11-08T09:00"), now = at("2026-11-06T15:00")),
        )
    }

    @Test
    fun 期限がちょうど0時なら0時を優先する() {
        // 0時の作り直しで期限切れにはならない（期限ちょうどは期限切れでない）が、0時0分1秒のアラームが次に予約される
        assertEquals(
            at("2026-11-07T00:00"),
            next(nextDueAt = at("2026-11-07T00:00"), now = at("2026-11-06T23:00")),
        )
    }

    @Test
    fun 夜中の0時ちょうどに作り直したときは翌日の0時() {
        assertEquals(at("2026-11-08T00:00"), next(nextDueAt = null, now = at("2026-11-07T00:00")))
    }
}
