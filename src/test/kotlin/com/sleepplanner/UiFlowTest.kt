package com.sleepplanner

import com.codeborne.selenide.CollectionCondition.size
import com.codeborne.selenide.Condition.exactText
import com.codeborne.selenide.Condition.text
import com.codeborne.selenide.Condition.value
import com.codeborne.selenide.Condition.visible
import com.codeborne.selenide.Configuration
import com.codeborne.selenide.Selenide.clearBrowserCookies
import com.codeborne.selenide.Selenide.closeWebDriver
import com.codeborne.selenide.Selenide.element
import com.codeborne.selenide.Selenide.elements
import com.codeborne.selenide.Selenide.executeJavaScript
import com.codeborne.selenide.Selenide.open
import com.sleepplanner.history.HistoryRepository
import com.sleepplanner.user.UserRepository
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.extensions.spring.SpringExtension
import org.openqa.selenium.Keys
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.test.context.TestPropertySource

/**
 * UI-тесты фронтенда через Selenide на живом сервере (RANDOM_PORT, H2).
 * Браузер — локальный Chrome без headless. Покрывают фронтовую часть TESTING.md:
 * авторизацию, живой пересчёт, баннер валидации, очистку полей и историю.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@TestPropertySource(
    properties = [
        "spring.datasource.url=jdbc:h2:mem:uitest;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
    ]
)
class UiFlowTest : FeatureSpec() {

    @LocalServerPort var port: Int = 0
    @Autowired lateinit var users: UserRepository
    @Autowired lateinit var history: HistoryRepository

    override fun extensions() = listOf(SpringExtension)

    /** Регистрирует нового пользователя через UI и доводит до экрана приложения. */
    private fun registerViaUi(name: String) {
        open("/")
        element("#switcher a").click() // переключиться в режим регистрации
        element("#loginUser").setValue(name)
        element("#loginPass").setValue("1234")
        element("#loginPass2").setValue("1234")
        element("#authBtn").click()
        element("#app").shouldBe(visible)
        element("#whoName").shouldHave(text(name))
    }

    /** Меняет значение time-инпута и шлёт реальный input-событие, как при вводе. */
    private fun setTime(id: String, v: String) {
        executeJavaScript<Any?>(
            "var e=document.getElementById(arguments[0]);e.value=arguments[1];" +
                "e.dispatchEvent(new Event('input'));",
            id, v
        )
    }

    init {
        beforeSpec {
            Configuration.browser = "chrome"
            Configuration.headless = false
            Configuration.timeout = 6000
        }
        afterSpec {
            closeWebDriver()
        }
        // Чистая БД и свежая сессия перед каждым сценарием.
        beforeTest {
            history.deleteAll()
            users.deleteAll()
            Configuration.baseUrl = "http://localhost:$port"
            open("/")
            clearBrowserCookies()
        }

        feature("Авторизация") {
            scenario("регистрация нового пользователя открывает приложение") {
                registerViaUi("mama")
                element("#authStage").shouldNotBe(visible)
                element("#whoBox").shouldBe(visible)
            }

            scenario("пустые логин и пароль показывают сообщение") {
                open("/")
                element("#authBtn").click()
                element("#authMsg").shouldHave(text("Введите логин и пароль"))
            }

            scenario("несовпадающие пароли при регистрации отклоняются") {
                open("/")
                element("#switcher a").click()
                element("#loginUser").setValue("papa")
                element("#loginPass").setValue("1234")
                element("#loginPass2").setValue("9999")
                element("#authBtn").click()
                element("#authMsg").shouldHave(text("Пароли не совпадают"))
            }

            scenario("выход возвращает на экран авторизации") {
                registerViaUi("mama")
                element("button[onclick='logout()']").click()
                element("#authStage").shouldBe(visible)
                element("#app").shouldNotBe(visible)
            }
        }

        feature("Живой расчёт") {
            scenario("стрелка на ползунке меняет подпись общего бодрствования") {
                registerViaUi("mama")
                element("#whVal").shouldHave(exactText("12 ч"))
                element("#wh").sendKeys(Keys.ARROW_RIGHT)
                element("#whVal").shouldHave(exactText("12.5 ч"))
            }

            scenario("смена количества снов перестраивает строки") {
                registerViaUi("mama")
                element("#napCount").selectOptionByValue("2")
                element("#ns1").shouldBe(visible)
                element("#ne1").shouldBe(visible)
            }
        }

        feature("Валидация расчёта") {
            scenario("первый сон раньше подъёма показывает баннер ошибки") {
                registerViaUi("mama")
                element("#errBanner").shouldNotBe(visible)
                setTime("mw", "14:00") // подъём позже дефолтного сна 13:00
                element("#errBanner").shouldBe(visible)
                element("#errText").shouldHave(text("позже утреннего подъёма"))
            }
        }

        feature("Очистка полей") {
            scenario("кнопка очистки возвращает дефолты и показывает toast") {
                registerViaUi("mama")
                element("#wh").sendKeys(Keys.ARROW_RIGHT)
                element("#whVal").shouldHave(exactText("12.5 ч"))
                element("button[onclick='clearFields()']").click()
                element("#toast").shouldHave(text("Поля очищены"))
                element("#wh").shouldHave(value("12"))
                element("#whVal").shouldHave(exactText("12 ч"))
            }
        }

        feature("История") {
            scenario("сохранение добавляет запись и показывает toast") {
                registerViaUi("mama")
                element("button[onclick='saveCalc()']").click()
                element("#toast").shouldHave(text("Сохранено"))
                elements(".hitem").shouldHave(size(1))
            }

            scenario("удаление убирает запись из истории") {
                registerViaUi("mama")
                element("button[onclick='saveCalc()']").click()
                elements(".hitem").shouldHave(size(1))
                element(".hitem .hdel").click()
                elements(".hitem").shouldHave(size(0))
                element(".hist-empty").shouldBe(visible)
            }
        }
    }
}
