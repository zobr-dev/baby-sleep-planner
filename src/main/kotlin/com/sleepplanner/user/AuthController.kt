package com.sleepplanner.user

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpSession
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.web.bind.annotation.*
import java.security.SecureRandom
import java.time.Duration
import java.time.Instant

data class AuthRequest(val username: String?, val password: String?, val email: String?)
data class ForgotRequest(val email: String?)
data class ResetRequest(val email: String?, val code: String?, val password: String?)
data class EmailRequest(val email: String?)
data class ChangePasswordRequest(val currentPassword: String?, val newPassword: String?)

@RestController
@RequestMapping("/api")
class AuthController(
    private val users: UserRepository,
    private val encoder: PasswordEncoder,
    private val email: EmailService
) {
    private val rnd = SecureRandom()
    private val emailRegex = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

    // Антифлуд для запроса кода сброса: не более 3 писем на e-mail за 15 минут
    // и не более 10 запросов с одного IP за час (защита от перебора/спама).
    private val forgotByEmail = SlidingWindowRateLimiter(3, Duration.ofMinutes(15))
    private val forgotByIp = SlidingWindowRateLimiter(10, Duration.ofHours(1))

    private fun err(status: HttpStatus, msg: String) =
        ResponseEntity.status(status).body(mapOf("error" to msg))

    private fun unauthorized() =
        ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(mapOf("error" to "Требуется вход"))

    @PostMapping("/register")
    fun register(@RequestBody body: AuthRequest, session: HttpSession): ResponseEntity<*> {
        val u = body.username?.trim()?.lowercase() ?: ""
        val p = body.password ?: ""
        val e = body.email?.trim()?.lowercase() ?: ""
        if (u.length < 3) return err(HttpStatus.BAD_REQUEST, "Логин — минимум 3 символа")
        if (!emailRegex.matches(e)) return err(HttpStatus.BAD_REQUEST, "Укажите корректный e-mail")
        if (p.length < 4) return err(HttpStatus.BAD_REQUEST, "Пароль — минимум 4 символа")
        if (users.existsByUsername(u)) return err(HttpStatus.CONFLICT, "Такой логин уже занят")
        if (users.existsByEmail(e)) return err(HttpStatus.CONFLICT, "Этот e-mail уже зарегистрирован")

        val saved = users.save(
            AppUser(username = u, passHash = encoder.encode(p), email = e, createdAt = Instant.now())
        )
        session.setAttribute("uid", saved.id)
        session.setAttribute("uname", saved.username)
        return ResponseEntity.ok(mapOf("username" to saved.username))
    }

    /** Запрос сброса: генерируем код, сохраняем с TTL 15 мин и отправляем на e-mail.
     *  Ответ всегда ok — не раскрываем, существует ли такой e-mail. */
    @PostMapping("/password/forgot")
    fun forgot(@RequestBody body: ForgotRequest, request: HttpServletRequest): ResponseEntity<*> {
        val e = body.email?.trim()?.lowercase() ?: ""
        if (!emailRegex.matches(e)) return err(HttpStatus.BAD_REQUEST, "Укажите корректный e-mail")

        // Ограничение частоты — до обращения к БД, чтобы не раскрывать наличие e-mail.
        val ip = request.remoteAddr ?: "unknown"
        if (!forgotByIp.tryAcquire(ip) || !forgotByEmail.tryAcquire(e))
            return err(HttpStatus.TOO_MANY_REQUESTS, "Слишком много запросов. Попробуйте позже")

        val user = users.findByEmail(e)
        if (user != null) {
            val code = "%06d".format(rnd.nextInt(1_000_000))
            user.resetCode = code
            user.resetCodeExpiresAt = Instant.now().plus(Duration.ofMinutes(15))
            users.save(user)
            email.sendResetCode(e, code)
        }
        return ResponseEntity.ok(mapOf("ok" to true))
    }

    /** Подтверждение сброса: проверяем код и срок, ставим новый пароль. */
    @PostMapping("/password/reset")
    fun reset(@RequestBody body: ResetRequest): ResponseEntity<*> {
        val e = body.email?.trim()?.lowercase() ?: ""
        val code = body.code?.trim() ?: ""
        val p = body.password ?: ""
        if (p.length < 4) return err(HttpStatus.BAD_REQUEST, "Пароль — минимум 4 символа")
        val user = users.findByEmail(e)
            ?: return err(HttpStatus.BAD_REQUEST, "Неверный код или e-mail")
        val expiry = user.resetCodeExpiresAt
        if (user.resetCode.isNullOrBlank() || user.resetCode != code)
            return err(HttpStatus.BAD_REQUEST, "Неверный код")
        if (expiry == null || expiry.isBefore(Instant.now()))
            return err(HttpStatus.BAD_REQUEST, "Срок действия кода истёк — запросите новый")

        user.passHash = encoder.encode(p)
        user.resetCode = null
        user.resetCodeExpiresAt = null
        users.save(user)
        return ResponseEntity.ok(mapOf("ok" to true))
    }

    @PostMapping("/login")
    fun login(@RequestBody body: AuthRequest, session: HttpSession): ResponseEntity<*> {
        val u = body.username?.trim()?.lowercase() ?: ""
        val p = body.password ?: ""
        val user = users.findByUsername(u)
            ?: return err(HttpStatus.NOT_FOUND, "Пользователь не найден")
        if (!encoder.matches(p, user.passHash))
            return err(HttpStatus.UNAUTHORIZED, "Неверный пароль")

        session.setAttribute("uid", user.id)
        session.setAttribute("uname", user.username)
        return ResponseEntity.ok(mapOf("username" to user.username))
    }

    @PostMapping("/logout")
    fun logout(session: HttpSession): ResponseEntity<*> {
        session.invalidate()
        return ResponseEntity.ok(mapOf("ok" to true))
    }

    @GetMapping("/me")
    fun me(session: HttpSession): ResponseEntity<*> {
        val uname = session.getAttribute("uname") as? String
        return ResponseEntity.ok(mapOf("username" to uname))
    }

    /** Профиль текущего пользователя: логин, e-mail, дата регистрации. */
    @GetMapping("/account")
    fun account(request: HttpServletRequest): ResponseEntity<*> {
        val uid = SessionUser.uid(request) ?: return unauthorized()
        val user = users.findById(uid).orElse(null) ?: return unauthorized()
        return ResponseEntity.ok(
            mapOf(
                "username" to user.username,
                "email" to user.email,
                "createdAt" to user.createdAt.toString()
            )
        )
    }

    /** Добавить или изменить e-mail текущего пользователя. */
    @PostMapping("/account/email")
    fun changeEmail(@RequestBody body: EmailRequest, request: HttpServletRequest): ResponseEntity<*> {
        val uid = SessionUser.uid(request) ?: return unauthorized()
        val e = body.email?.trim()?.lowercase() ?: ""
        if (!emailRegex.matches(e)) return err(HttpStatus.BAD_REQUEST, "Укажите корректный e-mail")
        val user = users.findById(uid).orElse(null) ?: return unauthorized()
        val owner = users.findByEmail(e)
        if (owner != null && owner.id != user.id)
            return err(HttpStatus.CONFLICT, "Этот e-mail уже зарегистрирован")
        user.email = e
        users.save(user)
        return ResponseEntity.ok(mapOf("email" to user.email))
    }

    /** Сменить пароль: требуется текущий пароль для подтверждения. */
    @PostMapping("/account/password")
    fun changePassword(@RequestBody body: ChangePasswordRequest, request: HttpServletRequest): ResponseEntity<*> {
        val uid = SessionUser.uid(request) ?: return unauthorized()
        val cur = body.currentPassword ?: ""
        val new = body.newPassword ?: ""
        if (new.length < 4) return err(HttpStatus.BAD_REQUEST, "Пароль — минимум 4 символа")
        val user = users.findById(uid).orElse(null) ?: return unauthorized()
        if (!encoder.matches(cur, user.passHash))
            return err(HttpStatus.UNAUTHORIZED, "Неверный текущий пароль")
        user.passHash = encoder.encode(new)
        users.save(user)
        return ResponseEntity.ok(mapOf("ok" to true))
    }
}

/** Утилита получения id текущего пользователя из сессии. */
object SessionUser {
    fun uid(request: HttpServletRequest): Long? =
        request.getSession(false)?.getAttribute("uid") as? Long
}
