package com.sleepplanner.history

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
class HistoryController(private val repo: HistoryRepository) {

    private fun unauthorized() =
        ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(mapOf("error" to "Требуется вход"))

    private fun cutoff() = LocalDate.now().minusDays(RETENTION_DAYS).toString()

    /** Отдаём строки в snake_case — ровно так их читает фронтенд. */
    private fun toMap(h: HistoryEntry): Map<String, Any?> = mapOf(
        "id" to h.id,
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
    fun list(request: HttpServletRequest): ResponseEntity<*> {
        val uid = SessionUser.uid(request) ?: return unauthorized()
        repo.deleteOlderThan(uid, cutoff())
        val rows = repo.findByUserIdOrderByDateDesc(uid).map { toMap(it) }
        return ResponseEntity.ok(rows)
    }

    @PostMapping
    fun save(@RequestBody body: HistorySaveRequest, request: HttpServletRequest): ResponseEntity<*> {
        val uid = SessionUser.uid(request) ?: return unauthorized()
        val date = body.date ?: return ResponseEntity.badRequest().body(mapOf("error" to "Не указана дата"))

        val existing = repo.findByUserIdAndDate(uid, date)
        val entry = existing ?: HistoryEntry(userId = uid, date = date)

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
    fun delete(@PathVariable date: String, request: HttpServletRequest): ResponseEntity<*> {
        val uid = SessionUser.uid(request) ?: return unauthorized()
        repo.deleteByUserIdAndDate(uid, date)
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
