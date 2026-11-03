package ir.hesabyar.finance

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import android.graphics.Bitmap

@RunWith(AndroidJUnit4::class)
class UiSmokeTest {
    @get:Rule val compose=createEmptyComposeRule()
    @Test fun navigationAndPeopleForms() {
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.onNodeWithText("حساب‌یار").assertExists()
            compose.onNodeWithText("ورود پیامک‌های قبلی").assertExists()
            screenshot("home")
            compose.onNodeWithText("اشخاص").performClick()
            compose.onNodeWithText("حساب اشخاص").assertExists()
            compose.onNodeWithContentDescription("افزودن شخص").performClick()
            compose.onNodeWithText("شخص جدید").assertExists()
            compose.onNodeWithText("نام").performTextInput("شخص آزمون")
            compose.onNodeWithText("ذخیره").performClick()
            compose.waitUntil(10000) { compose.onAllNodesWithText("شخص آزمون").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("شخص آزمون").performClick()
            compose.onNodeWithText("ریز گردش").assertExists()
            screenshot("person")
            compose.onNodeWithText("ثبت گردش شخص").performClick()
            compose.onNodeWithText("مبلغ (ریال)").performTextInput("10000")
            compose.onNodeWithText("ثبت",substring=false).performClick()
            compose.waitUntil(10000) { compose.onAllNodesWithText("طلب من").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithContentDescription("بستن").performClick()
            compose.onNodeWithText("حساب‌ها",substring=false).performClick()
            compose.onNodeWithText("حساب‌ها و ابزارها").assertExists()
            screenshot("accounts")
        }
    }
    private fun screenshot(name:String) {
        compose.waitForIdle()
        val inst=InstrumentationRegistry.getInstrumentation()
        val file=File(inst.targetContext.getExternalFilesDir(null),"$name.png")
        inst.uiAutomation.takeScreenshot()?.let { bitmap -> file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) };bitmap.recycle() }
    }
}
