package com.sleepplanner.pages

import com.codeborne.selenide.Condition.text
import com.codeborne.selenide.Condition.visible
import com.codeborne.selenide.Selenide.element

/** Экран «Аккаунт»: профиль, управление детьми, e-mail и сменой пароля. */
class AccountPage {
    private val stage = element("#accountStage")
    private val accUser = element("#accUser")
    private val accEmailView = element("#accEmailView")
    private val newChildName = element("#newChildName")
    private val newChildBirth = element("#newChildBirth")
    private val addChildBtn = element("button[onclick='addChild()']")
    private val childMsg = element("#accChildMsg")
    private val backLink = element("a[onclick='closeAccount()']")

    fun shouldBeVisible(): AccountPage {
        stage.shouldBe(visible)
        return this
    }

    fun userShouldBe(login: String): AccountPage {
        accUser.shouldHave(text(login))
        return this
    }

    fun emailShouldContain(part: String): AccountPage {
        accEmailView.shouldHave(text(part))
        return this
    }

    /** Добавляет ещё одного ребёнка через форму аккаунта. */
    fun addChild(name: String): AccountPage {
        newChildName.setValue(name)
        addChildBtn.click()
        return this
    }

    fun childMessageShouldContain(part: String): AccountPage {
        childMsg.shouldHave(text(part))
        return this
    }

    fun childRowsShouldContain(name: String): AccountPage {
        element("#accChildren").shouldHave(text(name))
        return this
    }

    fun back(): PlannerPage {
        backLink.click()
        return PlannerPage()
    }
}
