package com.sleepplanner.user

import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

/**
 * Простой потокобезопасный ограничитель частоты запросов (sliding window) в памяти.
 *
 * Подходит для одного инстанса приложения (наш случай за nginx). При горизонтальном
 * масштабировании на несколько реплик нужен общий стор — например, Redis.
 */
class SlidingWindowRateLimiter(
    private val maxRequests: Int,
    window: Duration
) {
    private val windowMs = window.toMillis()
    private val hits = ConcurrentHashMap<String, MutableList<Long>>()
    @Volatile private var lastSweep = System.currentTimeMillis()

    /** true — запрос разрешён и учтён; false — лимит для ключа исчерпан. */
    fun tryAcquire(key: String): Boolean {
        val now = System.currentTimeMillis()
        sweepIfNeeded(now)
        val list = hits.computeIfAbsent(key) { mutableListOf() }
        synchronized(list) {
            list.removeIf { now - it > windowMs }
            if (list.size >= maxRequests) return false
            list.add(now)
            return true
        }
    }

    /** Периодически убираем устаревшие записи, чтобы карта не росла бесконечно. */
    private fun sweepIfNeeded(now: Long) {
        if (now - lastSweep < windowMs) return
        lastSweep = now
        hits.forEach { (k, list) ->
            synchronized(list) {
                list.removeIf { now - it > windowMs }
                if (list.isEmpty()) hits.remove(k, list)
            }
        }
    }
}
