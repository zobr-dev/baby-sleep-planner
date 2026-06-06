package com.sleepplanner

import com.sleepplanner.user.SlidingWindowRateLimiter
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe
import java.time.Duration

/**
 * Чистый unit-тест ограничителя частоты — без Spring и без БД.
 * Проверяет окно, независимость ключей и восстановление лимита после паузы.
 */
class RateLimiterTest : FeatureSpec({

    feature("SlidingWindowRateLimiter") {
        scenario("пропускает ровно maxRequests запросов, затем отказывает") {
            val limiter = SlidingWindowRateLimiter(3, Duration.ofMinutes(15))
            limiter.tryAcquire("a") shouldBe true
            limiter.tryAcquire("a") shouldBe true
            limiter.tryAcquire("a") shouldBe true
            limiter.tryAcquire("a") shouldBe false
        }

        scenario("разные ключи считаются независимо") {
            val limiter = SlidingWindowRateLimiter(1, Duration.ofMinutes(15))
            limiter.tryAcquire("a") shouldBe true
            limiter.tryAcquire("a") shouldBe false
            // другой ключ не затронут
            limiter.tryAcquire("b") shouldBe true
        }

        scenario("после истечения окна лимит восстанавливается") {
            // окно 50 мс — короткое, чтобы тест не тормозил
            val limiter = SlidingWindowRateLimiter(2, Duration.ofMillis(50))
            limiter.tryAcquire("a") shouldBe true
            limiter.tryAcquire("a") shouldBe true
            limiter.tryAcquire("a") shouldBe false
            Thread.sleep(80)
            limiter.tryAcquire("a") shouldBe true
        }
    }
})
