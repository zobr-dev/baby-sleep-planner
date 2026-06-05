package com.sleepplanner.child

import jakarta.persistence.*
import org.springframework.data.jpa.repository.JpaRepository
import java.time.Instant

/**
 * Ребёнок, привязанный к аккаунту. История расчётов ведётся по каждому ребёнку
 * отдельно (history.child_id). Возраст не храним статически — храним дату
 * рождения и вычисляем актуальный возраст на клиенте.
 */
@Entity
@Table(name = "child")
data class Child(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(name = "user_id", nullable = false)
    var userId: Long = 0,

    @Column(nullable = false)
    var name: String = "",

    /** ISO-дата рождения (yyyy-MM-dd) либо null, если не указана. */
    @Column(name = "birth_date")
    var birthDate: String? = null,

    @Column(name = "created_at")
    var createdAt: Instant = Instant.now()
)

interface ChildRepository : JpaRepository<Child, Long> {
    fun findByUserIdOrderByCreatedAtAsc(userId: Long): List<Child>
    fun findByIdAndUserId(id: Long, userId: Long): Child?
    fun countByUserId(userId: Long): Long
}
