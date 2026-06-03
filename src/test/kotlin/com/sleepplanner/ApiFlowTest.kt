package com.sleepplanner

import com.sleepplanner.history.HistoryEntry
import com.sleepplanner.history.HistoryRepository
import com.sleepplanner.user.UserRepository
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.hamcrest.Matchers.nullValue
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import java.time.LocalDate

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(
    properties = [
        "spring.datasource.url=jdbc:h2:mem:test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
    ]
)
class ApiFlowTest : FeatureSpec() {

    @Autowired lateinit var mvc: MockMvc
    @Autowired lateinit var users: UserRepository
    @Autowired lateinit var history: HistoryRepository

    override fun extensions() = listOf(SpringExtension)

    /** Регистрирует пользователя через API и возвращает сессию с проставленным uid. */
    private fun register(username: String, password: String = "1234"): MockHttpSession {
        val session = MockHttpSession()
        mvc.perform(
            post("/api/register").session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"username":"$username","password":"$password"}""")
        ).andExpect(status().isOk)
        return session
    }

    private fun saveBody(
        date: String,
        an: String = "21:30",
        naps: String = """[{"s":"13:00","e":"15:00"}]"""
    ) = """
        {"date":"$date","wh":12,"fw":6,"mw":"07:00","napCount":1,
         "naps":$naps,"napTotal":120,
         "recFirst":"13:00","recNight":"21:00","an":"$an","actWakeM":750,
         "firstWindowM":360,"eveWindowM":390,"nightDurM":570}
    """.trimIndent()

    init {
        // Чистая БД перед каждым сценарием — изоляция тестов.
        beforeTest {
            history.deleteAll()
            users.deleteAll()
        }

        feature("Регистрация") {
            scenario("успешная регистрация заводит сессию и возвращает username") {
                val s = MockHttpSession()
                mvc.perform(
                    post("/api/register").session(s)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"username":"mama","password":"1234"}""")
                ).andExpect(status().isOk)
                    .andExpect(jsonPath("$.username").value("mama"))

                mvc.perform(get("/api/me").session(s))
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.username").value("mama"))
            }

            scenario("логин нормализуется: trim + lowercase") {
                mvc.perform(
                    post("/api/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"username":"  MaMa_Arseniya  ","password":"1234"}""")
                ).andExpect(status().isOk)
                    .andExpect(jsonPath("$.username").value("mama_arseniya"))
            }

            scenario("логин короче 3 символов отклоняется") {
                mvc.perform(
                    post("/api/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"username":"ab","password":"1234"}""")
                ).andExpect(status().isBadRequest)
                    .andExpect(jsonPath("$.error").value("Логин — минимум 3 символа"))
            }

            scenario("пароль короче 4 символов отклоняется") {
                mvc.perform(
                    post("/api/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"username":"mama","password":"12"}""")
                ).andExpect(status().isBadRequest)
                    .andExpect(jsonPath("$.error").value("Пароль — минимум 4 символа"))
            }

            scenario("занятый логин даёт 409 с учётом нормализации регистра") {
                register("mama")
                mvc.perform(
                    post("/api/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"username":"MAMA","password":"1234"}""")
                ).andExpect(status().isConflict)
                    .andExpect(jsonPath("$.error").value("Такой логин уже занят"))
            }

            scenario("пароль хранится только как BCrypt-хеш, не в открытом виде") {
                register("papa", "secret123")
                val stored = users.findByUsername("papa")!!
                stored.passHash shouldNotBe "secret123"
                stored.passHash.startsWith("\$2") shouldBe true
            }
        }

        feature("Вход") {
            scenario("верные данные открывают сессию") {
                register("mama")
                val s = MockHttpSession()
                mvc.perform(
                    post("/api/login").session(s)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"username":"mama","password":"1234"}""")
                ).andExpect(status().isOk)
                    .andExpect(jsonPath("$.username").value("mama"))
            }

            scenario("вход находит пользователя при другом регистре и пробелах") {
                register("mama")
                mvc.perform(
                    post("/api/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"username":"  MAMA ","password":"1234"}""")
                ).andExpect(status().isOk)
                    .andExpect(jsonPath("$.username").value("mama"))
            }

            scenario("неверный пароль отклоняется с 401") {
                register("papa")
                mvc.perform(
                    post("/api/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"username":"papa","password":"wrong"}""")
                ).andExpect(status().isUnauthorized)
                    .andExpect(jsonPath("$.error").value("Неверный пароль"))
            }

            scenario("несуществующий пользователь даёт 404") {
                mvc.perform(
                    post("/api/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"username":"ghost","password":"1234"}""")
                ).andExpect(status().isNotFound)
                    .andExpect(jsonPath("$.error").value("Пользователь не найден"))
            }
        }

        feature("Сессия") {
            scenario("logout инвалидирует сессию") {
                val s = register("mama")
                mvc.perform(get("/api/me").session(s))
                    .andExpect(jsonPath("$.username").value("mama"))

                mvc.perform(post("/api/logout").session(s))
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.ok").value(true))
            }

            scenario("/me без сессии возвращает 200 с пустым username") {
                mvc.perform(get("/api/me"))
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.username").value(nullValue()))
            }
        }

        feature("История требует авторизации") {
            scenario("GET без сессии → 401") {
                mvc.perform(get("/api/history"))
                    .andExpect(status().isUnauthorized)
                    .andExpect(jsonPath("$.error").value("Требуется вход"))
            }

            scenario("POST без сессии → 401") {
                mvc.perform(
                    post("/api/history")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(saveBody("2026-06-02"))
                ).andExpect(status().isUnauthorized)
            }

            scenario("DELETE без сессии → 401") {
                mvc.perform(delete("/api/history/2026-06-02"))
                    .andExpect(status().isUnauthorized)
            }
        }

        feature("Сохранение истории") {
            scenario("первое сохранение → updated:false, повторное за ту же дату → updated:true (upsert)") {
                val s = register("mama")
                mvc.perform(
                    post("/api/history").session(s)
                        .contentType(MediaType.APPLICATION_JSON).content(saveBody("2026-06-02"))
                ).andExpect(status().isOk)
                    .andExpect(jsonPath("$.updated").value(false))

                mvc.perform(
                    post("/api/history").session(s)
                        .contentType(MediaType.APPLICATION_JSON).content(saveBody("2026-06-02"))
                ).andExpect(status().isOk)
                    .andExpect(jsonPath("$.updated").value(true))

                // одна запись на день — дубля нет
                mvc.perform(get("/api/history").session(s))
                    .andExpect(jsonPath("$.length()").value(1))
            }

            scenario("сохранение без даты → 400") {
                val s = register("mama")
                mvc.perform(
                    post("/api/history").session(s)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"wh":12,"fw":6,"mw":"07:00"}""")
                ).andExpect(status().isBadRequest)
                    .andExpect(jsonPath("$.error").value("Не указана дата"))
            }

            scenario("пустой 'an' сохраняется как null в actual_night") {
                val s = register("mama")
                mvc.perform(
                    post("/api/history").session(s)
                        .contentType(MediaType.APPLICATION_JSON).content(saveBody("2026-06-02", an = ""))
                ).andExpect(status().isOk)

                mvc.perform(get("/api/history").session(s))
                    .andExpect(jsonPath("$[0].actual_night").value(nullValue()))
            }

            scenario("отдаёт строки в snake_case, как читает фронтенд") {
                val s = register("mama")
                mvc.perform(
                    post("/api/history").session(s)
                        .contentType(MediaType.APPLICATION_JSON).content(saveBody("2026-06-02"))
                ).andExpect(status().isOk)

                mvc.perform(get("/api/history").session(s))
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$[0].date").value("2026-06-02"))
                    .andExpect(jsonPath("$[0].nap_count").value(1))
                    .andExpect(jsonPath("$[0].actual_night").value("21:30"))
                    .andExpect(jsonPath("$[0].actual_wake_m").value(750))
                    .andExpect(jsonPath("$[0].naps_json").value("""[{"s":"13:00","e":"15:00"}]"""))
            }

            scenario("кавычки внутри naps вырезаются, JSON остаётся валидным") {
                val s = register("mama")
                val dirtyNaps = """[{"s":"13:\"00","e":"15:00"}]"""
                mvc.perform(
                    post("/api/history").session(s)
                        .contentType(MediaType.APPLICATION_JSON).content(saveBody("2026-06-02", naps = dirtyNaps))
                ).andExpect(status().isOk)

                mvc.perform(get("/api/history").session(s))
                    .andExpect(jsonPath("$[0].naps_json").value("""[{"s":"13:00","e":"15:00"}]"""))
            }

            scenario("список отсортирован по дате по убыванию") {
                val s = register("mama")
                listOf("2026-06-01", "2026-06-03", "2026-06-02").forEach {
                    mvc.perform(
                        post("/api/history").session(s)
                            .contentType(MediaType.APPLICATION_JSON).content(saveBody(it))
                    ).andExpect(status().isOk)
                }
                mvc.perform(get("/api/history").session(s))
                    .andExpect(jsonPath("$[0].date").value("2026-06-03"))
                    .andExpect(jsonPath("$[1].date").value("2026-06-02"))
                    .andExpect(jsonPath("$[2].date").value("2026-06-01"))
            }
        }

        feature("Изоляция и удаление") {
            scenario("пользователь видит только свои записи") {
                val mama = register("mama")
                val papa = register("papa")
                mvc.perform(
                    post("/api/history").session(mama)
                        .contentType(MediaType.APPLICATION_JSON).content(saveBody("2026-06-02"))
                ).andExpect(status().isOk)

                mvc.perform(get("/api/history").session(papa))
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.length()").value(0))
            }

            scenario("удаление чужой записи не затрагивает владельца") {
                val mama = register("mama")
                val papa = register("papa")
                mvc.perform(
                    post("/api/history").session(mama)
                        .contentType(MediaType.APPLICATION_JSON).content(saveBody("2026-06-02"))
                ).andExpect(status().isOk)

                // papa пытается удалить дату mama — у него такой записи нет
                mvc.perform(delete("/api/history/2026-06-02").session(papa))
                    .andExpect(status().isOk)

                mvc.perform(get("/api/history").session(mama))
                    .andExpect(jsonPath("$.length()").value(1))
            }

            scenario("удаление существующей записи убирает её из списка") {
                val s = register("mama")
                mvc.perform(
                    post("/api/history").session(s)
                        .contentType(MediaType.APPLICATION_JSON).content(saveBody("2026-06-02"))
                ).andExpect(status().isOk)

                mvc.perform(delete("/api/history/2026-06-02").session(s))
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.ok").value(true))

                mvc.perform(get("/api/history").session(s))
                    .andExpect(jsonPath("$.length()").value(0))
            }

            scenario("удаление несуществующей даты не падает") {
                val s = register("mama")
                mvc.perform(delete("/api/history/1999-01-01").session(s))
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.ok").value(true))
            }
        }

        feature("Очистка записей старше 90 дней") {
            scenario("запись старше 90 дней удаляется, граничная (=cutoff) остаётся") {
                val s = register("mama")
                val uid = users.findByUsername("mama")!!.id!!
                val cutoff = LocalDate.now().minusDays(90)

                // напрямую кладём три записи: за пределом, на границе и свежую
                history.save(HistoryEntry(userId = uid, date = cutoff.minusDays(1).toString()))
                history.save(HistoryEntry(userId = uid, date = cutoff.toString()))
                history.save(HistoryEntry(userId = uid, date = LocalDate.now().toString()))

                // GET триггерит deleteOlderThan
                val res = mvc.perform(get("/api/history").session(s))
                    .andExpect(status().isOk)
                    .andReturn().response.contentAsString

                res.contains(cutoff.minusDays(1).toString()) shouldBe false
                res.contains(cutoff.toString()) shouldBe true
                res.contains(LocalDate.now().toString()) shouldBe true
            }
        }
    }
}
