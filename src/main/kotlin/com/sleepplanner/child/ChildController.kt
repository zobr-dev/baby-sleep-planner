package com.sleepplanner.child

import com.sleepplanner.history.HistoryRepository
import com.sleepplanner.user.SessionUser
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.Instant

data class ChildRequest(val name: String?, val birthDate: String?)

@RestController
@RequestMapping("/api/children")
class ChildController(
    private val children: ChildRepository,
    private val history: HistoryRepository
) {
    private val birthDateRegex = Regex("^\\d{4}-\\d{2}-\\d{2}$")

    private fun unauthorized() =
        ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(mapOf("error" to "Требуется вход"))

    private fun err(status: HttpStatus, msg: String) =
        ResponseEntity.status(status).body(mapOf("error" to msg))

    private fun toMap(c: Child): Map<String, Any?> = mapOf(
        "id" to c.id,
        "name" to c.name,
        "birthDate" to c.birthDate
    )

    /** Нормализует дату рождения: пустую строку → null, формат yyyy-MM-dd. */
    private fun normBirth(raw: String?): String? {
        val v = raw?.trim().orEmpty()
        if (v.isEmpty()) return null
        if (!birthDateRegex.matches(v)) return "INVALID"
        return v
    }

    @GetMapping
    fun list(request: HttpServletRequest): ResponseEntity<*> {
        val uid = SessionUser.uid(request) ?: return unauthorized()
        return ResponseEntity.ok(children.findByUserIdOrderByCreatedAtAsc(uid).map { toMap(it) })
    }

    @PostMapping
    fun create(@RequestBody body: ChildRequest, request: HttpServletRequest): ResponseEntity<*> {
        val uid = SessionUser.uid(request) ?: return unauthorized()
        val name = body.name?.trim().orEmpty()
        if (name.isEmpty()) return err(HttpStatus.BAD_REQUEST, "Укажите имя ребёнка")
        if (name.length > 40) return err(HttpStatus.BAD_REQUEST, "Имя слишком длинное")
        val birth = normBirth(body.birthDate)
        if (birth == "INVALID") return err(HttpStatus.BAD_REQUEST, "Некорректная дата рождения")

        // Первый ребёнок забирает всю «старую» историю без привязки (миграция).
        val firstChild = children.countByUserId(uid) == 0L

        val saved = children.save(
            Child(userId = uid, name = name, birthDate = birth, createdAt = Instant.now())
        )
        if (firstChild) history.assignChildless(uid, saved.id!!)

        return ResponseEntity.ok(toMap(saved))
    }

    @PutMapping("/{id}")
    fun update(
        @PathVariable id: Long,
        @RequestBody body: ChildRequest,
        request: HttpServletRequest
    ): ResponseEntity<*> {
        val uid = SessionUser.uid(request) ?: return unauthorized()
        val child = children.findByIdAndUserId(id, uid)
            ?: return err(HttpStatus.NOT_FOUND, "Ребёнок не найден")
        val name = body.name?.trim().orEmpty()
        if (name.isEmpty()) return err(HttpStatus.BAD_REQUEST, "Укажите имя ребёнка")
        if (name.length > 40) return err(HttpStatus.BAD_REQUEST, "Имя слишком длинное")
        val birth = normBirth(body.birthDate)
        if (birth == "INVALID") return err(HttpStatus.BAD_REQUEST, "Некорректная дата рождения")

        child.name = name
        child.birthDate = birth
        children.save(child)
        return ResponseEntity.ok(toMap(child))
    }

    /** Удаляет ребёнка вместе со всей его историей. */
    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: Long, request: HttpServletRequest): ResponseEntity<*> {
        val uid = SessionUser.uid(request) ?: return unauthorized()
        val child = children.findByIdAndUserId(id, uid)
            ?: return ResponseEntity.ok(mapOf("ok" to true)) // идемпотентно
        history.deleteByUserIdAndChildId(uid, child.id!!)
        children.delete(child)
        return ResponseEntity.ok(mapOf("ok" to true))
    }
}
