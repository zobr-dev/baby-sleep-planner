package com.sleepplanner.pages

import com.codeborne.selenide.CollectionCondition.size
import com.codeborne.selenide.Condition.disabled
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
    private val childSelect = element("#childSelect")
    private val cfgToggle = element("#cfgToggle")
    private val cfgParams = element("#cfgParams")
    private val wakeSlider = element("#wh")
    private val wakeLabel = element("#whVal")
    private val betweenSlider = element("#fw")
    private val advSwitch = element(".adv-row .switch")
    private val advOpenBtn = element(".adv-open-btn")
    private val advStage = element("#advStage")
    private val napCount = element("#napCount")
    private val errBanner = element("#errBanner")
    private val errText = element("#errText")
    private val toast = element("#toast")
    private val saveBtn = element("button[onclick='saveCalc()']")
    private val clearBtn = element("button[onclick='clearFields()']")
    private val accountBtn = element("button[onclick='openAccount()']")
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

    fun childSelectShouldBeHidden(): PlannerPage {
        childSelect.shouldNotBe(visible)
        return this
    }

    fun childSelectShouldBeVisible(): PlannerPage {
        childSelect.shouldBe(visible)
        return this
    }

    /** Раскрывает свёрнутый по умолчанию блок «Параметры расчёта» (ползунки, число снов). */
    fun expandParams(): PlannerPage {
        if (!cfgParams.isDisplayed) cfgToggle.click()
        cfgParams.shouldBe(visible)
        return this
    }

    // --- живой расчёт ---
    // Ползунки минутные (step=5): значение 720 = «12 ч», один шаг вправо = «12 ч 5 м».

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

    // --- расширенные настройки ---

    /** Включает расширенный режим переключателем рядом с ползунком (клик по label-свитчу). */
    fun enableAdvanced(): PlannerPage {
        advSwitch.click()
        return this
    }

    /** В расширенном режиме ползунок бодрствования между снами блокируется. */
    fun betweenWakeSliderShouldBeDisabled(): PlannerPage {
        betweenSlider.shouldBe(disabled)
        return this
    }

    fun betweenWakeSliderShouldBeEnabled(): PlannerPage {
        betweenSlider.shouldNotBe(disabled)
        return this
    }

    /** Открывает отдельную страницу расширенных настроек по кнопке «Настроить →». */
    fun openAdvanced(): PlannerPage {
        advOpenBtn.click()
        return this
    }

    /** Страница расширенных настроек видна и содержит ползунки бодрствований/снов. */
    fun advancedPageShouldBeVisible(): PlannerPage {
        advStage.shouldBe(visible)
        element("#aw0").shouldBe(visible) // бодрствование до 1-го сна
        element("#ad0").shouldBe(visible) // длительность 1-го сна
        return this
    }

    /** Ставит время подъёма и шлёт нативное input-событие, как при вводе в UI. */
    fun setMorningWake(v: String): PlannerPage {
        dispatchInput("mw", v)
        return this
    }

    /** Очищает обязательное поле утреннего подъёма — должен появиться баннер ошибки. */
    fun clearMorningWake(): PlannerPage {
        dispatchInput("mw", "")
        return this
    }

    /** Заполняет время первого сна (засыпание/пробуждение) — делает сны «полными». */
    fun setFirstNap(start: String, end: String): PlannerPage {
        dispatchInput("ns0", start)
        dispatchInput("ne0", end)
        return this
    }

    private fun dispatchInput(id: String, v: String) {
        executeJavaScript<Any?>(
            "var e=document.getElementById(arguments[0]);e.value=arguments[1];" +
                "e.dispatchEvent(new Event('input'));",
            id, v
        )
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

    fun openAccount(): AccountPage {
        accountBtn.click()
        return AccountPage().shouldBeVisible()
    }

    fun logout(): AuthPage {
        logoutBtn.click()
        return AuthPage()
    }
}
