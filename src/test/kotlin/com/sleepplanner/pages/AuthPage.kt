package com.sleepplanner.pages

import com.codeborne.selenide.Condition.text
import com.codeborne.selenide.Condition.visible
import com.codeborne.selenide.Selenide.element
import com.codeborne.selenide.Selenide.open

/** Экран авторизации: вход и регистрация. */
class AuthPage {
    private val stage = element("#authStage")
    private val user = element("#loginUser")
    private val pass = element("#loginPass")
    private val pass2 = element("#loginPass2")
    private val submit = element("#authBtn")
    private val message = element("#authMsg")
    private val toggle = element("#switcher a")

    fun open(): AuthPage {
        open("/")
        return this
    }

    fun shouldBeVisible(): AuthPage {
        stage.shouldBe(visible)
        return this
    }

    fun goToRegister(): AuthPage {
        toggle.click()
        return this
    }

    fun fill(login: String, password: String, confirm: String = password): AuthPage {
        user.setValue(login)
        pass.setValue(password)
        pass2.setValue(confirm)
        return this
    }

    fun submit(): AuthPage {
        submit.click()
        return this
    }

    fun messageShouldContain(part: String): AuthPage {
        message.shouldHave(text(part))
        return this
    }

    /** Happy-path регистрация: переключается в режим, заполняет, отправляет и ждёт приложение. */
    fun register(login: String, password: String = "1234"): PlannerPage {
        goToRegister().fill(login, password).submit()
        return PlannerPage().shouldBeLoaded(login)
    }
}
