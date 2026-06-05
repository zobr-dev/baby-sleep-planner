package com.sleepplanner.user

import jakarta.persistence.*
import org.springframework.data.jpa.repository.JpaRepository
import java.time.Instant

@Entity
@Table(name = "users")
data class AppUser(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(unique = true, nullable = false)
    var username: String = "",

    @Column(name = "pass_hash", nullable = false)
    var passHash: String = "",

    // nullable на уровне БД, чтобы ddl-auto=update не падал на существующих строках;
    // при регистрации email обязателен (проверка в контроллере).
    @Column(unique = true)
    var email: String? = null,

    // одноразовый код сброса пароля и срок его действия
    @Column(name = "reset_code")
    var resetCode: String? = null,

    @Column(name = "reset_code_expires_at")
    var resetCodeExpiresAt: Instant? = null,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now()
)

interface UserRepository : JpaRepository<AppUser, Long> {
    fun findByUsername(username: String): AppUser?
    fun existsByUsername(username: String): Boolean
    fun findByEmail(email: String): AppUser?
    fun existsByEmail(email: String): Boolean
}
