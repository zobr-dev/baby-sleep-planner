package com.sleepplanner.user

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.mail.SimpleMailMessage
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.stereotype.Service

/**
 * Отправка писем с кодом сброса пароля.
 *
 * Работает «best-effort»: если SMTP не настроен (нет бина JavaMailSender)
 * или MAIL_ENABLED=false, код не отправляется по почте, а пишется в лог —
 * это позволяет запускать и тестировать приложение без почтового сервера.
 * Для боевой отправки задайте SMTP_HOST/SMTP_USERNAME/SMTP_PASSWORD и
 * MAIL_ENABLED=true (см. application.yml).
 */
@Service
class EmailService(
    private val mailSender: ObjectProvider<JavaMailSender>,
    @Value("\${app.mail.enabled:false}") private val enabled: Boolean,
    @Value("\${app.mail.from:no-reply@baby-sleep-planner.ru}") private val from: String
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun sendResetCode(to: String, code: String) {
        val sender = mailSender.ifAvailable
        if (!enabled || sender == null) {
            log.warn("Почта отключена/не настроена. Код сброса для {}: {}", to, code)
            return
        }
        val msg = SimpleMailMessage().apply {
            setFrom(from)
            setTo(to)
            setSubject("Код для сброса пароля — Планировщик детского сна")
            setText(
                "Здравствуйте!\n\n" +
                    "Ваш код для сброса пароля: $code\n" +
                    "Код действует 15 минут.\n\n" +
                    "Если вы не запрашивали сброс пароля, просто проигнорируйте это письмо."
            )
        }
        try {
            sender.send(msg)
            log.info("Код сброса отправлен на {}", to)
        } catch (e: Exception) {
            log.error("Не удалось отправить письмо на {}: {}", to, e.message)
        }
    }
}
