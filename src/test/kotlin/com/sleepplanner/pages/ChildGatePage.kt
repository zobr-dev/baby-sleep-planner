package com.sleepplanner.pages

import com.codeborne.selenide.Condition.text
import com.codeborne.selenide.Condition.visible
import com.codeborne.selenide.Selenide.element

/**
 * Экран «Добавьте ребёнка» (childGate) — показывается сразу после регистрации,
 * пока у аккаунта нет ни одного ребёнка.
 */
class ChildGatePage {
    private val stage = element("#childGate")
    private val name = element("#gateChildName")
    private val birth = element("#gateChildBirth")
    private val submitBtn = element("button[onclick='submitFirstChild()']")
    private val message = element("#childGateMsg")

    fun shouldBeVisible(): ChildGatePage {
        stage.shouldBe(visible)
        return this
    }

    fun fillName(value: String): ChildGatePage {
        name.setValue(value)
        return this
    }

    fun fillBirth(value: String): ChildGatePage {
        birth.setValue(value)
        return this
    }

    fun submit(): ChildGatePage {
        submitBtn.click()
        return this
    }

    fun messageShouldContain(part: String): ChildGatePage {
        message.shouldHave(text(part))
        return this
    }

    /** Добавляет ребёнка и переходит в основное приложение. */
    fun addChild(childName: String): PlannerPage {
        name.setValue(childName)
        submitBtn.click()
        return PlannerPage()
    }
}
