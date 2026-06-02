package com.sleepplanner.user

import jakarta.persistence.*
import org.springframework.data.jpa.repository.JpaRepository
import java.time.Instant

@Entity
@Table(name = "users")
class AppUser(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(unique = true, nullable = false)
    var username: String = "",

    @Column(name = "pass_hash", nullable = false)
    var passHash: String = "",

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now()
)

interface UserRepository : JpaRepository<AppUser, Long> {
    fun findByUsername(username: String): AppUser?
    fun existsByUsername(username: String): Boolean
}
