package com.sleepplanner.user

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpSession
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.web.bind.annotation.*
import java.time.Instant

data class AuthRequest(val username: String?, val password: String?)

@RestController
@RequestMapping("/api")
class AuthController(
    private val users: UserRepository,
    private val encoder: PasswordEncoder
) {
    private fun err(status: HttpStatus, msg: String) =
        ResponseEntity.status(status).body(mapOf("error" to msg))

    @PostMapping("/register")
    fun register(@RequestBody body: AuthRequest, session: HttpSession): ResponseEntity<*> {
        val u = body.username?.trim()?.lowercase() ?: ""
        val p = body.password ?: ""
        if (u.length < 3) return err(HttpStatus.BAD_REQUEST, "Логин — минимум 3 символа")
        if (p.length < 4) return err(HttpStatus.BAD_REQUEST, "Пароль — минимум 4 символа")
        if (users.existsByUsername(u)) return err(HttpStatus.CONFLICT, "Такой логин уже занят")

        val saved = users.save(
            AppUser(username = u, passHash = encoder.encode(p), createdAt = Instant.now())
        )
        session.setAttribute("uid", saved.id)
        session.setAttribute("uname", saved.username)
        return ResponseEntity.ok(mapOf("username" to saved.username))
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
}

/** Утилита получения id текущего пользователя из сессии. */
object SessionUser {
    fun uid(request: HttpServletRequest): Long? =
        request.getSession(false)?.getAttribute("uid") as? Long
}
