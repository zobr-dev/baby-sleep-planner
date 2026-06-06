package com.sleepplanner

import com.sleepplanner.child.ChildRepository
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
import java.time.Instant
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
    @Autowired lateinit var childrenRepo: ChildRepository

    override fun extensions() = listOf(SpringExtension)

    /** Регистрирует пользователя через API и возвращает сессию с проставленным uid. */
    private fun register(username: String, password: String = "1234"): MockHttpSession {
        val session = MockHttpSession()
        mvc.perform(
            post("/api/register").session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"username":"$username","password":"$password","email":"$username@test.local"}""")
        ).andExpect(status().isOk)
        return session
    }

    /** Создаёт ребёнка через API и возвращает его id. */
    private fun addChild(session: MockHttpSession, name: String = "Малыш"): Long {
        val body = mvc.perform(
            post("/api/children").session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"$name"}""")
        ).andExpect(status().isOk).andReturn().response.contentAsString
        return Regex("\"id\":(\\d+)").find(body)!!.groupValues[1].toLong()
    }

    private fun saveBody(
        date: String,
        an: String = "21:30",
        naps: String = """[{"s":"13:00","e":"15:00"}]""",
        childId: Long? = null
    ): String {
        val childLine = if (childId != null) "\"childId\":$childId," else ""
        return """
        {"date":"$date",${childLine}"wh":12,"fw":6,"mw":"07:00","napCount":1,
         "naps":$naps,"napTotal":120,
         "recFirst":"13:00","recNight":"21:00","an":"$an","actWakeM":750,
         "firstWindowM":360,"eveWindowM":390,"nightDurM":570}
        """.trimIndent()
    }

    init {
        // Чистая БД перед каждым сценарием — изоляция тестов.
        beforeTest {
            history.deleteAll()
            childrenRepo.deleteAll()
            users.deleteAll()
        }

        feature("Регистрация") {
            scenario("успешная регистрация заводит сессию и возвращает username") {
                val s = MockHttpSession()
                mvc.perform(
                    post("/api/register").session(s)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"username":"mama","password":"1234","email":"mama@test.local"}""")
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
                        .content("""{"username":"  MaMa_Arseniya  ","password":"1234","email":"arseniya@test.local"}""")
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
                        .content("""{"username":"mama","password":"12","email":"mama@test.local"}""")
                ).andExpect(status().isBadRequest)
                    .andExpect(jsonPath("$.error").value("Пароль — минимум 4 символа"))
            }

            scenario("занятый логин даёт 409 с учётом нормализации регистра") {
                register("mama")
                mvc.perform(
                    post("/api/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"username":"MAMA","password":"1234","email":"mama2@test.local"}""")
                ).andExpect(status().isConflict)
                    .andExpect(jsonPath("$.error").value("Такой логин уже занят"))
            }

            scenario("пароль хранится только как BCrypt-хеш, не в открытом виде") {
                register("papa", "secret123")
                val stored = users.findByUsername("papa")!!
                stored.passHash shouldNotBe "secret123"
                stored.passHash.startsWith("\$2") shouldBe true
            }

            scenario("некорректный e-mail отклоняется с 400") {
                mvc.perform(
                    post("/api/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"username":"mama","password":"1234","email":"not-an-email"}""")
                ).andExpect(status().isBadRequest)
                    .andExpect(jsonPath("$.error").value("Укажите корректный e-mail"))
            }

            scenario("занятый e-mail даёт 409") {
                register("mama") // e-mail mama@test.local
                mvc.perform(
                    post("/api/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"username":"mama2","password":"1234","email":"mama@test.local"}""")
                ).andExpect(status().isConflict)
                    .andExpect(jsonPath("$.error").value("Этот e-mail уже зарегистрирован"))
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

        feature("Сброс пароля по e-mail") {
            scenario("полный цикл: forgot выдаёт код, reset меняет пароль") {
                register("mama") // e-mail mama@test.local
                mvc.perform(
                    post("/api/password/forgot")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"email":"mama@test.local"}""")
                ).andExpect(status().isOk)
                    .andExpect(jsonPath("$.ok").value(true))

                val code = users.findByEmail("mama@test.local")!!.resetCode!!

                mvc.perform(
                    post("/api/password/reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"email":"mama@test.local","code":"$code","password":"newpass"}""")
                ).andExpect(status().isOk)
                    .andExpect(jsonPath("$.ok").value(true))

                // старый пароль больше не подходит
                mvc.perform(
                    post("/api/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"username":"mama","password":"1234"}""")
                ).andExpect(status().isUnauthorized)

                // новый пароль работает
                mvc.perform(
                    post("/api/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"username":"mama","password":"newpass"}""")
                ).andExpect(status().isOk)

                // код одноразовый — обнулён после использования
                users.findByEmail("mama@test.local")!!.resetCode shouldBe null
            }

            scenario("forgot на несуществующий e-mail всё равно отвечает ok") {
                mvc.perform(
                    post("/api/password/forgot")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"email":"ghost@test.local"}""")
                ).andExpect(status().isOk)
                    .andExpect(jsonPath("$.ok").value(true))
            }

            scenario("неверный код отклоняется с 400") {
                register("mama")
                mvc.perform(
                    post("/api/password/forgot")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"email":"mama@test.local"}""")
                ).andExpect(status().isOk)

                mvc.perform(
                    post("/api/password/reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"email":"mama@test.local","code":"wrong","password":"newpass"}""")
                ).andExpect(status().isBadRequest)
                    .andExpect(jsonPath("$.error").value("Неверный код"))
            }

            scenario("reset на несуществующий e-mail → 400") {
                mvc.perform(
                    post("/api/password/reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"email":"ghost@test.local","code":"123456","password":"newpass"}""")
                ).andExpect(status().isBadRequest)
                    .andExpect(jsonPath("$.error").value("Неверный код или e-mail"))
            }

            scenario("слишком короткий новый пароль при reset → 400") {
                // короткий пароль отсекается ещё до проверки кода — forgot не нужен
                register("mama")
                mvc.perform(
                    post("/api/password/reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"email":"mama@test.local","code":"123456","password":"12"}""")
                ).andExpect(status().isBadRequest)
                    .andExpect(jsonPath("$.error").value("Пароль — минимум 4 символа"))
            }

            scenario("истёкший код отклоняется с 400") {
                // код проставляем напрямую с истёкшим сроком — без обращения к forgot,
                // чтобы не зависеть от общего на контекст ограничителя частоты.
                register("mama")
                val user = users.findByEmail("mama@test.local")!!
                user.resetCode = "123456"
                user.resetCodeExpiresAt = Instant.now().minusSeconds(60)
                users.save(user)

                mvc.perform(
                    post("/api/password/reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"email":"mama@test.local","code":"123456","password":"newpass"}""")
                ).andExpect(status().isBadRequest)
                    .andExpect(jsonPath("$.error").value("Срок действия кода истёк — запросите новый"))
            }

            scenario("повторные запросы forgot на один e-mail упираются в лимит (429)") {
                register("flood") // e-mail flood@test.local, лимит 3 письма / 15 мин
                repeat(3) {
                    mvc.perform(
                        post("/api/password/forgot")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""{"email":"flood@test.local"}""")
                    ).andExpect(status().isOk)
                }
                mvc.perform(
                    post("/api/password/forgot")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"email":"flood@test.local"}""")
                ).andExpect(status().isTooManyRequests)
                    .andExpect(jsonPath("$.error").value("Слишком много запросов. Попробуйте позже"))
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

        feature("Аккаунт") {
            scenario("GET /account возвращает профиль текущего пользователя") {
                val s = register("mama")
                mvc.perform(get("/api/account").session(s))
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.username").value("mama"))
                    .andExpect(jsonPath("$.email").value("mama@test.local"))
                    .andExpect(jsonPath("$.createdAt").isNotEmpty)
            }

            scenario("GET /account без сессии → 401") {
                mvc.perform(get("/api/account"))
                    .andExpect(status().isUnauthorized)
                    .andExpect(jsonPath("$.error").value("Требуется вход"))
            }

            scenario("смена e-mail сохраняется") {
                val s = register("mama")
                mvc.perform(
                    post("/api/account/email").session(s)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"email":"new@test.local"}""")
                ).andExpect(status().isOk)
                    .andExpect(jsonPath("$.email").value("new@test.local"))

                users.findByUsername("mama")!!.email shouldBe "new@test.local"
            }

            scenario("нельзя занять e-mail другого пользователя → 409") {
                register("papa") // e-mail papa@test.local
                val s = register("mama")
                mvc.perform(
                    post("/api/account/email").session(s)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"email":"papa@test.local"}""")
                ).andExpect(status().isConflict)
                    .andExpect(jsonPath("$.error").value("Этот e-mail уже зарегистрирован"))
            }

            scenario("смена пароля: с верным текущим проходит, старый пароль перестаёт работать") {
                val s = register("mama")
                mvc.perform(
                    post("/api/account/password").session(s)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"currentPassword":"1234","newPassword":"newpass"}""")
                ).andExpect(status().isOk)
                    .andExpect(jsonPath("$.ok").value(true))

                mvc.perform(
                    post("/api/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"username":"mama","password":"newpass"}""")
                ).andExpect(status().isOk)

                mvc.perform(
                    post("/api/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"username":"mama","password":"1234"}""")
                ).andExpect(status().isUnauthorized)
            }

            scenario("смена пароля с неверным текущим → 401") {
                val s = register("mama")
                mvc.perform(
                    post("/api/account/password").session(s)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"currentPassword":"wrong","newPassword":"newpass"}""")
                ).andExpect(status().isUnauthorized)
                    .andExpect(jsonPath("$.error").value("Неверный текущий пароль"))
            }

            scenario("смена e-mail на некорректный формат → 400") {
                val s = register("mama")
                mvc.perform(
                    post("/api/account/email").session(s)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"email":"broken"}""")
                ).andExpect(status().isBadRequest)
                    .andExpect(jsonPath("$.error").value("Укажите корректный e-mail"))
            }

            scenario("смена пароля на слишком короткий новый → 400") {
                val s = register("mama")
                mvc.perform(
                    post("/api/account/password").session(s)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"currentPassword":"1234","newPassword":"12"}""")
                ).andExpect(status().isBadRequest)
                    .andExpect(jsonPath("$.error").value("Пароль — минимум 4 символа"))
            }

            scenario("смена e-mail без сессии → 401") {
                mvc.perform(
                    post("/api/account/email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"email":"new@test.local"}""")
                ).andExpect(status().isUnauthorized)
                    .andExpect(jsonPath("$.error").value("Требуется вход"))
            }
        }

        feature("Дети") {
            scenario("создание ребёнка и список") {
                val s = register("mama")
                mvc.perform(
                    post("/api/children").session(s)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"name":"Арсений","birthDate":"2025-01-15"}""")
                ).andExpect(status().isOk)
                    .andExpect(jsonPath("$.name").value("Арсений"))
                    .andExpect(jsonPath("$.birthDate").value("2025-01-15"))

                mvc.perform(get("/api/children").session(s))
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].name").value("Арсений"))
            }

            scenario("ребёнок без имени → 400") {
                val s = register("mama")
                mvc.perform(
                    post("/api/children").session(s)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"name":"   "}""")
                ).andExpect(status().isBadRequest)
                    .andExpect(jsonPath("$.error").value("Укажите имя ребёнка"))
            }

            scenario("некорректная дата рождения → 400") {
                val s = register("mama")
                mvc.perform(
                    post("/api/children").session(s)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"name":"Арсений","birthDate":"15.01.2025"}""")
                ).andExpect(status().isBadRequest)
                    .andExpect(jsonPath("$.error").value("Некорректная дата рождения"))
            }

            scenario("слишком длинное имя ребёнка → 400") {
                val s = register("mama")
                val longName = "А".repeat(41)
                mvc.perform(
                    post("/api/children").session(s)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"name":"$longName"}""")
                ).andExpect(status().isBadRequest)
                    .andExpect(jsonPath("$.error").value("Имя слишком длинное"))
            }

            scenario("GET /children без сессии → 401") {
                mvc.perform(get("/api/children"))
                    .andExpect(status().isUnauthorized)
            }

            scenario("обновление ребёнка меняет имя и дату рождения") {
                val s = register("mama")
                val cid = addChild(s, "Арсений")
                mvc.perform(
                    put("/api/children/$cid").session(s)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"name":"Мира","birthDate":"2024-03-10"}""")
                ).andExpect(status().isOk)
                    .andExpect(jsonPath("$.name").value("Мира"))
                    .andExpect(jsonPath("$.birthDate").value("2024-03-10"))

                mvc.perform(get("/api/children").session(s))
                    .andExpect(jsonPath("$[0].name").value("Мира"))
                    .andExpect(jsonPath("$[0].birthDate").value("2024-03-10"))
            }

            scenario("обновление несуществующего ребёнка → 404") {
                val s = register("mama")
                mvc.perform(
                    put("/api/children/9999").session(s)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"name":"Никто"}""")
                ).andExpect(status().isNotFound)
                    .andExpect(jsonPath("$.error").value("Ребёнок не найден"))
            }

            scenario("обновление чужого ребёнка → 404 (не раскрываем существование)") {
                val mama = register("mama")
                val papa = register("papa")
                val papaChild = addChild(papa, "Чужой")
                mvc.perform(
                    put("/api/children/$papaChild").session(mama)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"name":"Перехват"}""")
                ).andExpect(status().isNotFound)
                    .andExpect(jsonPath("$.error").value("Ребёнок не найден"))
            }

            scenario("обновление с пустым именем → 400") {
                val s = register("mama")
                val cid = addChild(s, "Арсений")
                mvc.perform(
                    put("/api/children/$cid").session(s)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"name":"   "}""")
                ).andExpect(status().isBadRequest)
                    .andExpect(jsonPath("$.error").value("Укажите имя ребёнка"))
            }

            scenario("обновление без сессии → 401") {
                mvc.perform(
                    put("/api/children/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"name":"Аноним"}""")
                ).andExpect(status().isUnauthorized)
            }

            scenario("первый ребёнок забирает старую историю без привязки (миграция)") {
                val s = register("mama")
                val uid = users.findByUsername("mama")!!.id!!
                // запись из старой схемы — без child_id
                history.save(HistoryEntry(userId = uid, date = "2026-06-02"))

                val cid = addChild(s, "Арсений")

                mvc.perform(get("/api/history").session(s).param("childId", cid.toString()))
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].child_id").value(cid.toInt()))
            }

            scenario("история разводится по детям") {
                val s = register("mama")
                val a = addChild(s, "Арсений")
                val b = addChild(s, "Мира")

                mvc.perform(
                    post("/api/history").session(s)
                        .contentType(MediaType.APPLICATION_JSON).content(saveBody("2026-06-02", childId = a))
                ).andExpect(status().isOk)
                mvc.perform(
                    post("/api/history").session(s)
                        .contentType(MediaType.APPLICATION_JSON).content(saveBody("2026-06-02", childId = b))
                ).andExpect(status().isOk)
                mvc.perform(
                    post("/api/history").session(s)
                        .contentType(MediaType.APPLICATION_JSON).content(saveBody("2026-06-03", childId = b))
                ).andExpect(status().isOk)

                mvc.perform(get("/api/history").session(s).param("childId", a.toString()))
                    .andExpect(jsonPath("$.length()").value(1))
                mvc.perform(get("/api/history").session(s).param("childId", b.toString()))
                    .andExpect(jsonPath("$.length()").value(2))
            }

            scenario("нельзя читать историю чужого ребёнка → 403") {
                val mama = register("mama")
                val papa = register("papa")
                val papaChild = addChild(papa, "Чужой")

                mvc.perform(get("/api/history").session(mama).param("childId", papaChild.toString()))
                    .andExpect(status().isForbidden)
                    .andExpect(jsonPath("$.error").value("Нет доступа к этому ребёнку"))
            }

            scenario("нельзя сохранять историю в чужого ребёнка → 403") {
                val mama = register("mama")
                val papa = register("papa")
                val papaChild = addChild(papa, "Чужой")

                mvc.perform(
                    post("/api/history").session(mama)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(saveBody("2026-06-02", childId = papaChild))
                ).andExpect(status().isForbidden)
                    .andExpect(jsonPath("$.error").value("Нет доступа к этому ребёнку"))
            }

            scenario("нельзя удалять историю чужого ребёнка → 403") {
                val mama = register("mama")
                val papa = register("papa")
                val papaChild = addChild(papa, "Чужой")

                mvc.perform(
                    delete("/api/history/2026-06-02").session(mama)
                        .param("childId", papaChild.toString())
                ).andExpect(status().isForbidden)
                    .andExpect(jsonPath("$.error").value("Нет доступа к этому ребёнку"))
            }

            scenario("удаление ребёнка уносит его историю") {
                val s = register("mama")
                val cid = addChild(s, "Арсений")
                mvc.perform(
                    post("/api/history").session(s)
                        .contentType(MediaType.APPLICATION_JSON).content(saveBody("2026-06-02", childId = cid))
                ).andExpect(status().isOk)

                mvc.perform(delete("/api/children/$cid").session(s))
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.ok").value(true))

                childrenRepo.findByUserIdOrderByCreatedAtAsc(users.findByUsername("mama")!!.id!!).size shouldBe 0
                history.findByUserIdOrderByDateDesc(users.findByUsername("mama")!!.id!!).size shouldBe 0
            }

            scenario("один день для двух детей сохраняется без конфликта уникальности") {
                val s = register("mama")
                val a = addChild(s, "Арсений")
                val b = addChild(s, "Мира")
                // тот же день, оба ребёнка — обе записи должны существовать
                mvc.perform(
                    post("/api/history").session(s)
                        .contentType(MediaType.APPLICATION_JSON).content(saveBody("2026-06-02", childId = a))
                ).andExpect(status().isOk).andExpect(jsonPath("$.updated").value(false))
                mvc.perform(
                    post("/api/history").session(s)
                        .contentType(MediaType.APPLICATION_JSON).content(saveBody("2026-06-02", childId = b))
                ).andExpect(status().isOk).andExpect(jsonPath("$.updated").value(false))
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
