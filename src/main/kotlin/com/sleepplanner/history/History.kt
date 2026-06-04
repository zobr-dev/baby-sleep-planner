package com.sleepplanner.history

import jakarta.persistence.*
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * Запись истории расчёта за конкретный день.
 * Уникальность по (user_id, child_id, date) обеспечивает upsert «одна запись
 * на день для ребёнка». child_id допускает null для записей из старой схемы —
 * они привязываются к первому добавленному ребёнку при обновлении (см.
 * ChildController) и автоматически вычищаются миграцией ограничения.
 */
@Entity
@Table(
    name = "history",
    uniqueConstraints = [UniqueConstraint(columnNames = ["user_id", "child_id", "date"])]
)
class HistoryEntry(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(name = "user_id", nullable = false)
    var userId: Long = 0,

    @Column(name = "child_id")
    var childId: Long? = null,

    @Column(nullable = false)
    var date: String = "",

    var wh: Double? = null,
    var fw: Double? = null,
    var mw: String? = null,

    @Column(name = "nap_count")
    var napCount: Int? = null,

    @Column(name = "naps_json", columnDefinition = "text")
    var napsJson: String? = null,

    @Column(name = "nap_total_m")
    var napTotalM: Int? = null,

    @Column(name = "rec_first")
    var recFirst: String? = null,

    @Column(name = "rec_night")
    var recNight: String? = null,

    @Column(name = "actual_night")
    var actualNight: String? = null,

    @Column(name = "actual_wake_m")
    var actualWakeM: Int? = null,

    @Column(name = "first_window_m")
    var firstWindowM: Int? = null,

    @Column(name = "eve_window_m")
    var eveWindowM: Int? = null,

    @Column(name = "night_dur_m")
    var nightDurM: Int? = null,

    @Column(name = "saved_at")
    var savedAt: Instant = Instant.now()
)

interface HistoryRepository : JpaRepository<HistoryEntry, Long> {
    fun findByUserIdOrderByDateDesc(userId: Long): List<HistoryEntry>
    fun findByUserIdAndDate(userId: Long, date: String): HistoryEntry?

    // Запросы с учётом ребёнка (новая схема).
    fun findByUserIdAndChildIdOrderByDateDesc(userId: Long, childId: Long): List<HistoryEntry>
    fun findByUserIdAndChildIdAndDate(userId: Long, childId: Long, date: String): HistoryEntry?

    @Transactional
    fun deleteByUserIdAndDate(userId: Long, date: String)

    @Transactional
    fun deleteByUserIdAndChildIdAndDate(userId: Long, childId: Long, date: String)

    @Modifying
    @Transactional
    @Query("DELETE FROM HistoryEntry h WHERE h.userId = :uid AND h.date < :cutoff")
    fun deleteOlderThan(@Param("uid") uid: Long, @Param("cutoff") cutoff: String)

    /** Привязывает старые записи без ребёнка к указанному ребёнку (миграция). */
    @Modifying
    @Transactional
    @Query("UPDATE HistoryEntry h SET h.childId = :cid WHERE h.userId = :uid AND h.childId IS NULL")
    fun assignChildless(@Param("uid") uid: Long, @Param("cid") cid: Long): Int

    /** Удаляет всю историю ребёнка (при удалении ребёнка). */
    @Transactional
    fun deleteByUserIdAndChildId(userId: Long, childId: Long)
}
