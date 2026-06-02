package com.sleepplanner.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.HttpStatusEntryPoint
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

/**
 * Авторизация на основе серверной сессии (cookie JSESSIONID, httponly).
 * Пароли хешируются BCrypt. CSRF отключён для JSON REST API; защита от
 * межсайтовых запросов обеспечивается SameSite-cookie (см. application.yml).
 */
@Configuration
class SecurityConfig {

    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()

    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val cfg = CorsConfiguration()
        cfg.allowedOriginPatterns = listOf("*")
        cfg.allowedMethods = listOf("GET", "POST", "DELETE", "OPTIONS")
        cfg.allowedHeaders = listOf("*")
        cfg.allowCredentials = true
        val source = UrlBasedCorsConfigurationSource()
        source.registerCorsConfiguration("/api/**", cfg)
        return source
    }

    @Bean
    fun filterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .cors { }
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED) }
            .authorizeHttpRequests { auth ->
                // Аутентификация проверяется в контроллерах по session-атрибуту uid
                // (единый механизм на основе серверной сессии), поэтому на уровне
                // фильтра пропускаем все запросы. Контроллеры истории возвращают 401,
                // если пользователь не залогинен.
                auth.anyRequest().permitAll()
            }
            // вместо редиректа на форму логина возвращаем 401 для API
            .exceptionHandling {
                it.authenticationEntryPoint(HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
            }
            .formLogin { it.disable() }
            .httpBasic { it.disable() }
            .logout { it.disable() }
        return http.build()
    }
}
