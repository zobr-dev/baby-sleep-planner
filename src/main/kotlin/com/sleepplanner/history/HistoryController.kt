package com.sleepplanner.history

import com.sleepplanner.child.ChildRepository
import com.sleepplanner.user.SessionUser
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.LocalDate
import java.time.Instant

/** Тело запроса на сохранение: фронтенд присылает поля camelCase. */
data class HistorySaveRequest(
    val date: String?,
    val childId: Long?,
    val wh: Double?,
    val fw: Double?,
    val mw: String?,
    val napCount: Int?,
    val naps: List<Map<String, String>>?,
    val napTotal: Int?,
    val recFirst: String?,
    val recNight: String?,
    val an: String?,
    val actWakeM: Int?,
    val firstWindowM: Int?,
    val eveWindowM: Int?,
    val nightDurM: Int?
)

const val RETENTION_DAYS = 90L

@RestController
@RequestMapping("/api/history")
class HistoryController(
    private val repo: HistoryRepository,
    private val children: ChildRepository
) {

    private fun unauthorized() =
        ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(mapOf("error" to "Требуется вход"))

    private fun forbidden() =
        ResponseEntity.status(HttpStatus.FORBIDDEN).body(mapOf("error" to "Нет доступа к этому ребёнку"))

    private fun cutoff() = LocalDate.now().minusDays(RETENTION_DAYS).toString()

    /** Проверяет, что ребёнок принадлежит пользователю. */
    private fun ownsChild(uid: Long, childId: Long) =
        children.findByIdAndUserId(childId, uid) != null

    /** Отдаём строки в snake_case — ровно так их читает фронтенд. */
    private fun toMap(h: HistoryEntry): Map<String, Any?> = mapOf(
        "id" to h.id,
        "child_id" to h.childId,
        "date" to h.date,
        "wh" to h.wh,
        "fw" to h.fw,
        "mw" to h.mw,
        "nap_count" to h.napCount,
        "naps_json" to h.napsJson,
        "nap_total_m" to h.napTotalM,
        "rec_first" to h.recFirst,
        "rec_night" to h.recNight,
        "actual_night" to h.actualNight,
        "actual_wake_m" to h.actualWakeM,
        "first_window_m" to h.firstWindowM,
        "eve_window_m" to h.eveWindowM,
        "night_dur_m" to h.nightDurM
    )

    @GetMapping
    fun list(
        @RequestParam(required = false) childId: Long?,
        request: HttpServletRequest
    ): ResponseEntity<*> {
        val uid = SessionUser.uid(request) ?: return unauthorized()
        repo.deleteOlderThan(uid, cutoff())
        val entries = if (childId != null) {
            if (!ownsChild(uid, childId)) return forbidden()
            repo.findByUserIdAndChildIdOrderByDateDesc(uid, childId)
        } else {
            repo.findByUserIdOrderByDateDesc(uid)
        }
        return ResponseEntity.ok(entries.map { toMap(it) })
    }

    @PostMapping
    fun save(@RequestBody body: HistorySaveRequest, request: HttpServletRequest): ResponseEntity<*> {
        val uid = SessionUser.uid(request) ?: return unauthorized()
        val date = body.date ?: return ResponseEntity.badRequest().body(mapOf("error" to "Не указана дата"))
        val childId = body.childId
        if (childId != null && !ownsChild(uid, childId)) return forbidden()

        val existing = if (childId != null)
            repo.findByUserIdAndChildIdAndDate(uid, childId, date)
        else
            repo.findByUserIdAndDate(uid, date)
        val entry = existing ?: HistoryEntry(userId = uid, childId = childId, date = date)
        entry.childId = childId

        entry.wh = body.wh
        entry.fw = body.fw
        entry.mw = body.mw
        entry.napCount = body.napCount
        entry.napsJson = serializeNaps(body.naps)
        entry.napTotalM = body.napTotal
        entry.recFirst = body.recFirst
        entry.recNight = body.recNight
        entry.actualNight = body.an?.ifBlank { null }
        entry.actualWakeM = body.actWakeM
        entry.firstWindowM = body.firstWindowM
        entry.eveWindowM = body.eveWindowM
        entry.nightDurM = body.nightDurM
        entry.savedAt = Instant.now()

        repo.save(entry)
        repo.deleteOlderThan(uid, cutoff())
        return ResponseEntity.ok(mapOf("updated" to (existing != null)))
    }

    @DeleteMapping("/{date}")
    fun delete(
        @PathVariable date: String,
        @RequestParam(required = false) childId: Long?,
        request: HttpServletRequest
    ): ResponseEntity<*> {
        val uid = SessionUser.uid(request) ?: return unauthorized()
        if (childId != null) {
            if (!ownsChild(uid, childId)) return forbidden()
            repo.deleteByUserIdAndChildIdAndDate(uid, childId, date)
        } else {
            repo.deleteByUserIdAndDate(uid, date)
        }
        return ResponseEntity.ok(mapOf("ok" to true))
    }

    /** Сериализуем массив снов в JSON-строку (как хранила прежняя версия). */
    private fun serializeNaps(naps: List<Map<String, String>>?): String {
        if (naps.isNullOrEmpty()) return "[]"
        val items = naps.joinToString(",") { n ->
            val s = (n["s"] ?: "").replace("\"", "")
            val e = (n["e"] ?: "").replace("\"", "")
            "{\"s\":\"$s\",\"e\":\"$e\"}"
        }
        return "[$items]"
    }
}
