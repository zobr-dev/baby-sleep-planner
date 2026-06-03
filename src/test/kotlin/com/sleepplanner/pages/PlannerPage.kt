package com.sleepplanner.pages

import com.codeborne.selenide.CollectionCondition.size
import com.codeborne.selenide.Condition.exactText
import com.codeborne.selenide.Condition.text
import com.codeborne.selenide.Condition.value
import com.codeborne.selenide.Condition.visible
import com.codeborne.selenide.Selenide.element
import com.codeborne.selenide.Selenide.elements
import com.codeborne.selenide.Selenide.executeJavaScript
import org.openqa.selenium.Keys

/** Основной экран приложения: параметры дня, результаты, история. */
class PlannerPage {
    private val app = element("#app")
    private val whoBox = element("#whoBox")
    private val whoName = element("#whoName")
    private val wakeSlider = element("#wh")
    private val wakeLabel = element("#whVal")
    private val napCount = element("#napCount")
    private val errBanner = element("#errBanner")
    private val errText = element("#errText")
    private val toast = element("#toast")
    private val saveBtn = element("button[onclick='saveCalc()']")
    private val clearBtn = element("button[onclick='clearFields()']")
    private val logoutBtn = element("button[onclick='logout()']")
    private val historyItems = elements(".hitem")
    private val histEmpty = element(".hist-empty")

    fun shouldBeLoaded(name: String): PlannerPage {
        app.shouldBe(visible)
        whoName.shouldHave(text(name))
        return this
    }

    fun headerShouldBeVisible(): PlannerPage {
        whoBox.shouldBe(visible)
        return this
    }

    // --- живой расчёт ---

    fun nudgeWakeSliderRight(): PlannerPage {
        wakeSlider.sendKeys(Keys.ARROW_RIGHT)
        return this
    }

    fun wakeLabelShouldBe(textValue: String): PlannerPage {
        wakeLabel.shouldHave(exactText(textValue))
        return this
    }

    fun wakeSliderValueShouldBe(v: String): PlannerPage {
        wakeSlider.shouldHave(value(v))
        return this
    }

    fun selectNaps(n: Int): PlannerPage {
        napCount.selectOptionByValue(n.toString())
        return this
    }

    fun napRowShouldBeVisible(i: Int): PlannerPage {
        element("#ns$i").shouldBe(visible)
        element("#ne$i").shouldBe(visible)
        return this
    }

    /** Ставит время подъёма и шлёт нативный input-событие, как при вводе в UI. */
    fun setMorningWake(v: String): PlannerPage {
        executeJavaScript<Any?>(
            "var e=document.getElementById('mw');e.value=arguments[0];" +
                "e.dispatchEvent(new Event('input'));",
            v
        )
        return this
    }

    // --- валидация ---

    fun errorBannerShouldBeVisible(): PlannerPage {
        errBanner.shouldBe(visible)
        return this
    }

    fun errorBannerShouldBeHidden(): PlannerPage {
        errBanner.shouldNotBe(visible)
        return this
    }

    fun errorTextShouldContain(part: String): PlannerPage {
        errText.shouldHave(text(part))
        return this
    }

    // --- действия ---

    fun clearFields(): PlannerPage {
        clearBtn.click()
        return this
    }

    fun save(): PlannerPage {
        saveBtn.click()
        return this
    }

    fun toastShouldContain(part: String): PlannerPage {
        toast.shouldHave(text(part))
        return this
    }

    // --- история ---

    fun historyShouldHaveSize(n: Int): PlannerPage {
        historyItems.shouldHave(size(n))
        return this
    }

    fun deleteFirstHistoryItem(): PlannerPage {
        element(".hitem .hdel").click()
        return this
    }

    fun emptyHistoryShouldBeVisible(): PlannerPage {
        histEmpty.shouldBe(visible)
        return this
    }

    fun logout(): AuthPage {
        logoutBtn.click()
        return AuthPage()
    }
}
