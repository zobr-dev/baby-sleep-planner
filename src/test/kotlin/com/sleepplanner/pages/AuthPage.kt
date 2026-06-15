package com.sleepplanner.pages

import com.codeborne.selenide.Condition.text
import com.codeborne.selenide.Condition.visible
import com.codeborne.selenide.Selenide.element
import com.codeborne.selenide.Selenide.open
import org.openqa.selenium.Keys

/** Экран авторизации: вход, регистрация и сброс пароля по e-mail. */
class AuthPage {
    private val stage = element("#authStage")
    private val user = element("#loginUser")
    private val emailInput = element("#loginEmail")
    private val code = element("#loginCode")
    private val pass = element("#loginPass")
    private val pass2 = element("#loginPass2")
    private val submitBtn = element("#authBtn")
    private val message = element("#authMsg")
    private val toRegister = element("#switcher a")
    private val forgot = element("#forgotLink a")

    fun open(): AuthPage {
        open("/")
        return this
    }

    fun shouldBeVisible(): AuthPage {
        stage.shouldBe(visible)
        return this
    }

    fun goToRegister(): AuthPage {
        toRegister.click()
        return this
    }

    fun goToForgot(): AuthPage {
        forgot.click()
        return this
    }

    /** Заполняет логин и пароль (поле подтверждения — для режима регистрации). */
    fun fill(login: String, password: String, confirm: String = password): AuthPage {
        user.setValue(login)
        pass.setValue(password)
        pass2.setValue(confirm)
        return this
    }

    fun fillEmail(value: String): AuthPage {
        emailInput.setValue(value)
        return this
    }

    fun fillCode(value: String): AuthPage {
        code.setValue(value)
        return this
    }

    fun submit(): AuthPage {
        submitBtn.click()
        return this
    }

    fun messageShouldContain(part: String): AuthPage {
        message.shouldHave(text(part))
        return this
    }

    /**
     * Регистрация: переключается в режим регистрации, заполняет логин, e-mail, пароль
     * и отправляет. После успеха фронт ведёт на экран добавления первого ребёнка.
     */
    fun register(login: String, password: String = "1234"): ChildGatePage {
        goToRegister()
        user.setValue(login)
        emailInput.setValue("$login@test.local")
        pass.setValue(password)
        pass2.setValue(password)
        submitBtn.click()
        return ChildGatePage().shouldBeVisible()
    }

    /** Полный happy-path: регистрация + добавление ребёнка → основное приложение. */
    fun registerAndEnter(login: String, child: String = "Малыш"): PlannerPage =
        register(login).addChild(child).shouldBeLoaded(login)

    /** Вход существующего пользователя нажатием Enter в поле пароля (без клика по кнопке). */
    fun loginByEnter(login: String, password: String = "1234"): PlannerPage {
        user.setValue(login)
        pass.setValue(password)
        pass.sendKeys(Keys.ENTER)
        return PlannerPage()
    }
}
