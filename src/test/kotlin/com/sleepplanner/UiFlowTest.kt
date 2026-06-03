package com.sleepplanner

import com.codeborne.selenide.Configuration
import com.codeborne.selenide.Selenide.clearBrowserCookies
import com.codeborne.selenide.Selenide.closeWebDriver
import com.codeborne.selenide.Selenide.open
import com.sleepplanner.history.HistoryRepository
import com.sleepplanner.pages.AuthPage
import com.sleepplanner.user.UserRepository
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.extensions.spring.SpringExtension
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.test.context.TestPropertySource

/**
 * UI-тесты фронтенда через Selenide на живом сервере (RANDOM_PORT, H2).
 * Браузер — локальный Chrome без headless. Селекторы и действия инкапсулированы
 * в page object'ах [AuthPage] и [com.sleepplanner.pages.PlannerPage];
 * сценарии описывают только поведение из фронтовой части TESTING.md.
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
                AuthPage().open()
                    .register("mama")
                    .headerShouldBeVisible()
            }

            scenario("пустые логин и пароль показывают сообщение") {
                AuthPage().open()
                    .submit()
                    .messageShouldContain("Введите логин и пароль")
            }

            scenario("несовпадающие пароли при регистрации отклоняются") {
                AuthPage().open()
                    .goToRegister()
                    .fill("papa", "1234", "9999")
                    .submit()
                    .messageShouldContain("Пароли не совпадают")
            }

            scenario("выход возвращает на экран авторизации") {
                AuthPage().open()
                    .register("mama")
                    .logout()
                    .shouldBeVisible()
            }
        }

        feature("Живой расчёт") {
            scenario("стрелка на ползунке меняет подпись общего бодрствования") {
                AuthPage().open().register("mama")
                    .wakeLabelShouldBe("12 ч")
                    .nudgeWakeSliderRight()
                    .wakeLabelShouldBe("12.5 ч")
            }

            scenario("смена количества снов перестраивает строки") {
                AuthPage().open().register("mama")
                    .selectNaps(2)
                    .napRowShouldBeVisible(1)
            }
        }

        feature("Валидация расчёта") {
            scenario("первый сон раньше подъёма показывает баннер ошибки") {
                AuthPage().open().register("mama")
                    .errorBannerShouldBeHidden()
                    .setMorningWake("14:00")
                    .errorBannerShouldBeVisible()
                    .errorTextShouldContain("позже утреннего подъёма")
            }
        }

        feature("Очистка полей") {
            scenario("кнопка очистки возвращает дефолты и показывает toast") {
                AuthPage().open().register("mama")
                    .nudgeWakeSliderRight()
                    .wakeLabelShouldBe("12.5 ч")
                    .clearFields()
                    .toastShouldContain("Поля очищены")
                    .wakeSliderValueShouldBe("12")
                    .wakeLabelShouldBe("12 ч")
            }
        }

        feature("История") {
            scenario("сохранение добавляет запись и показывает toast") {
                AuthPage().open().register("mama")
                    .save()
                    .toastShouldContain("Сохранено")
                    .historyShouldHaveSize(1)
            }

            scenario("удаление убирает запись из истории") {
                AuthPage().open().register("mama")
                    .save()
                    .historyShouldHaveSize(1)
                    .deleteFirstHistoryItem()
                    .historyShouldHaveSize(0)
                    .emptyHistoryShouldBeVisible()
            }
        }
    }
}
