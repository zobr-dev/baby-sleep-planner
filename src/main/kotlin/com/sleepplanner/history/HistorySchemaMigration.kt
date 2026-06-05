package com.sleepplanner.history

import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import javax.sql.DataSource

/**
 * Одноразовая идемпотентная миграция схемы history под поддержку нескольких детей.
 *
 * При обновлении существующей БниД (ddl-auto=update Hibernate не трогает уже
 * созданные ограничения) нужно вручную:
 *  1) убедиться, что колонка child_id есть;
 *  2) снять устаревшее ограничение уникальности (user_id, date) — оно мешает
 *     сохранять один день для разных детей;
 *  3) добавить новое (user_id, child_id, date), если его ещё нет.
 *
 * Выполняется только на PostgreSQL (боевая БД). На H2/тестах схема создаётся
 * заново из сущности — миграция пропускается. Любая ошибка лишь логируется и
 * не валит старт приложения.
 */
@Component
@Order(0)
class HistorySchemaMigration(private val dataSource: DataSource) : ApplicationRunner {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun run(args: ApplicationArguments) {
        dataSource.connection.use { conn ->
            val product = (conn.metaData.databaseProductName ?: "")
            if (!product.contains("PostgreSQL", ignoreCase = true)) return // только боевая БД

            try {
                conn.createStatement().use { it.execute("ALTER TABLE history ADD COLUMN IF NOT EXISTS child_id BIGINT") }

                val uniqueConstraints = readUniqueConstraints(conn)

                // 1) снимаем устаревшее (user_id, date)
                uniqueConstraints
                    .filter { it.value == listOf("date", "user_id") }
                    .keys.forEach { name ->
                        conn.createStatement().use { it.execute("ALTER TABLE history DROP CONSTRAINT \"$name\"") }
                        log.info("История: удалено устаревшее ограничение уникальности {} (user_id, date)", name)
                    }

                // 2) добавляем (user_id, child_id, date), если ещё нет
                val hasChildScoped = uniqueConstraints.values.any { it == listOf("child_id", "date", "user_id") }
                if (!hasChildScoped) {
                    conn.createStatement().use {
                        it.execute("ALTER TABLE history ADD CONSTRAINT uk_history_user_child_date UNIQUE (user_id, child_id, date)")
                    }
                    log.info("История: добавлено ограничение уникальности (user_id, child_id, date)")
                }
            } catch (e: Exception) {
                log.warn("История: миграция схемы под нескольких детей пропущена — {}", e.message)
            }
        }
    }

    /** Имя ограничения → отсортированный список его колонок. */
    private fun readUniqueConstraints(conn: java.sql.Connection): Map<String, List<String>> {
        val sql = """
            SELECT con.conname AS name, att.attname AS col
            FROM pg_constraint con
            JOIN pg_class rel ON rel.oid = con.conrelid
            JOIN unnest(con.conkey) k(attnum) ON true
            JOIN pg_attribute att ON att.attrelid = con.conrelid AND att.attnum = k.attnum
            WHERE rel.relname = 'history' AND con.contype = 'u'
        """.trimIndent()
        val map = HashMap<String, MutableList<String>>()
        conn.createStatement().use { st ->
            st.executeQuery(sql).use { rs ->
                while (rs.next()) {
                    map.getOrPut(rs.getString("name")) { mutableListOf() }.add(rs.getString("col"))
                }
            }
        }
        return map.mapValues { it.value.sorted() }
    }
}
