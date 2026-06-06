package com.sleepplanner

import com.codeborne.selenide.Configuration
import com.codeborne.selenide.Selenide.clearBrowserCookies
import com.codeborne.selenide.Selenide.closeWebDriver
import com.codeborne.selenide.Selenide.open
import com.sleepplanner.child.ChildRepository
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
 * в page object'ах [AuthPage], [com.sleepplanner.pages.ChildGatePage],
 * [com.sleepplanner.pages.PlannerPage] и [com.sleepplanner.pages.AccountPage];
 * сценарии описывают только поведение из фронтовой части TESTING.md.
 *
 * Учитываем актуальный поток UI: регистрация ведёт на экран добавления первого
 * ребёнка (childGate), и только после него открывается основное приложение.
 * Ползунки бодрствования — минутные (step=5): 720 → «12 ч», шаг вправо → «12 ч 5 м».
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
    @Autowired lateinit var children: ChildRepository

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
            children.deleteAll()
            users.deleteAll()
            Configuration.baseUrl = "http://localhost:$port"
            open("/")
            clearBrowserCookies()
        }

        feature("Авторизация") {
            scenario("регистрация + первый ребёнок открывают приложение") {
                AuthPage().open()
                    .registerAndEnter("mama")
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
                    .fillEmail("papa@test.local")
                    .fill("papa", "1234", "9999")
                    .submit()
                    .messageShouldContain("Пароли не совпадают")
            }

            scenario("некорректный e-mail при регистрации отклоняется") {
                AuthPage().open()
                    .goToRegister()
                    .fillEmail("broken")
                    .fill("papa", "1234")
                    .submit()
                    .messageShouldContain("Укажите корректный e-mail")
            }

            scenario("выход возвращает на экран авторизации") {
                AuthPage().open()
                    .registerAndEnter("mama")
                    .logout()
                    .shouldBeVisible()
            }
        }

        feature("Первый ребёнок") {
            scenario("после регистрации показывается экран добавления ребёнка") {
                AuthPage().open()
                    .register("mama")
                    .shouldBeVisible()
            }

            scenario("пустое имя ребёнка показывает сообщение об ошибке") {
                AuthPage().open()
                    .register("mama")
                    .submit()
                    .messageShouldContain("Укажите имя ребёнка")
            }
        }

        feature("Живой расчёт") {
            scenario("стрелка на ползунке меняет подпись общего бодрствования") {
                AuthPage().open().registerAndEnter("mama")
                    .wakeLabelShouldBe("12 ч")
                    .nudgeWakeSliderRight()
                    .wakeLabelShouldBe("12 ч 5 м")
            }

            scenario("смена количества снов перестраивает строки") {
                AuthPage().open().registerAndEnter("mama")
                    .selectNaps(2)
                    .napRowShouldBeVisible(1)
            }
        }

        feature("Валидация расчёта") {
            scenario("пустой утренний подъём показывает баннер ошибки") {
                AuthPage().open().registerAndEnter("mama")
                    .errorBannerShouldBeHidden()
                    .clearMorningWake()
                    .errorBannerShouldBeVisible()
                    .errorTextShouldContain("Укажите фактическое время утреннего подъёма")
            }

            scenario("первый сон раньше подъёма показывает баннер ошибки") {
                AuthPage().open().registerAndEnter("mama")
                    .errorBannerShouldBeHidden()
                    .setFirstNap("06:00", "06:30")
                    .errorBannerShouldBeVisible()
                    .errorTextShouldContain("позже утреннего подъёма")
            }
        }

        feature("Очистка полей") {
            scenario("кнопка очистки возвращает дефолты и показывает toast") {
                AuthPage().open().registerAndEnter("mama")
                    .nudgeWakeSliderRight()
                    .wakeLabelShouldBe("12 ч 5 м")
                    .clearFields()
                    .toastShouldContain("Поля очищены")
                    .wakeSliderValueShouldBe("720")
                    .wakeLabelShouldBe("12 ч")
            }
        }

        feature("История") {
            scenario("сохранение добавляет запись и показывает toast") {
                AuthPage().open().registerAndEnter("mama")
                    .save()
                    .toastShouldContain("Сохранено")
                    .historyShouldHaveSize(1)
            }

            scenario("удаление убирает запись из истории") {
                AuthPage().open().registerAndEnter("mama")
                    .save()
                    .historyShouldHaveSize(1)
                    .deleteFirstHistoryItem()
                    .historyShouldHaveSize(0)
                    .emptyHistoryShouldBeVisible()
            }
        }

        feature("Аккаунт") {
            scenario("экран аккаунта показывает логин пользователя") {
                AuthPage().open().registerAndEnter("mama")
                    .openAccount()
                    .userShouldBe("mama")
            }

            scenario("добавление второго ребёнка через аккаунт") {
                AuthPage().open().registerAndEnter("mama")
                    .openAccount()
                    .addChild("Мира")
                    .childMessageShouldContain("Ребёнок добавлен")
                    .childRowsShouldContain("Мира")
            }
        }
    }
}
