package com.vela.app.data.time

import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId

interface TimeProvider {
    fun zone(): ZoneId

    fun now(): OffsetDateTime = OffsetDateTime.now(zone())

    fun today(): LocalDate = now().toLocalDate()

    fun nowText(): String = now().toString()
}

object SystemTimeProvider : TimeProvider {
    override fun zone(): ZoneId = ZoneId.systemDefault()
}

/**
 * 全局时间入口。UI 层没有注入通道时统一走这里，测试可替换 [provider]。
 */
object VelaClock : TimeProvider {
    @Volatile
    var provider: TimeProvider = SystemTimeProvider

    override fun zone(): ZoneId = provider.zone()

    override fun now(): OffsetDateTime = provider.now()

    override fun today(): LocalDate = provider.today()
}
