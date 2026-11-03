package ir.hesabyar.finance

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.*
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*
import ir.hesabyar.finance.data.*
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
    @Test fun editTransactionPrefillsSavesAndCancelKeepsData() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase("hesabyar.db")
        val time=System.currentTimeMillis()
        var account=0L;var id=0L
        FinanceRepository(context).use { r ->
            account=r.addAccount(Account(name="حساب آزمون ویرایش",balance=1000))
            id=r.addTransaction(FinanceTransaction(amount=100,type=TransactionType.EXPENSE,occurredAt=time,reason="خرید آزمون",accountId=account,categoryId=null,personId=null,source=TransactionSource.MANUAL,note="یادداشت قبلی"))
        }
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.waitUntil(10000){compose.onAllNodesWithContentDescription("ویرایش خرید آزمون").fetchSemanticsNodes().isNotEmpty()}
            compose.onNodeWithText("تراکنش‌ها",substring=false).performClick()
            compose.onNodeWithContentDescription("ویرایش خرید آزمون").performClick()
            compose.waitUntil(10000){compose.onAllNodesWithText("ویرایش تراکنش").fetchSemanticsNodes().isNotEmpty()}
            compose.onNodeWithText("100",substring=false).assertExists()
            compose.onNodeWithText("مبلغ (ریال)").performTextClearance()
            compose.onNodeWithText("مبلغ (ریال)").performTextInput("200")
            compose.onNodeWithText("علت / شرح").performTextClearance()
            compose.onNodeWithText("علت / شرح").performTextInput("خرید ویرایش شده")
            screenshot("edit")
            compose.onNodeWithText("ذخیره تغییرات").performClick()
            compose.waitUntil(10000){compose.onAllNodesWithText("ویرایش تراکنش").fetchSemanticsNodes().isEmpty()}
            FinanceRepository(context).use { r ->
                assertEquals(200L,r.transaction(id)!!.amount);assertEquals(time,r.transaction(id)!!.occurredAt)
                assertEquals("یادداشت قبلی",r.transaction(id)!!.note);assertNull(r.transaction(id)!!.categoryId)
                assertEquals(800L,r.accounts().first{it.id==account}.balance)
            }
            compose.onNodeWithContentDescription("ویرایش خرید ویرایش شده").performClick()
            compose.waitUntil(10000){compose.onAllNodesWithText("ویرایش تراکنش").fetchSemanticsNodes().isNotEmpty()}
            compose.onNodeWithText("مبلغ (ریال)").performTextClearance()
            compose.onNodeWithText("مبلغ (ریال)").performTextInput("999")
            compose.onNodeWithContentDescription("بستن ویرایش تراکنش").performClick()
            FinanceRepository(context).use { r ->assertEquals(200L,r.transaction(id)!!.amount);assertEquals(800L,r.accounts().first{it.id==account}.balance)}
        }
    }
    private fun screenshot(name:String) {
        compose.waitForIdle()
        val inst=InstrumentationRegistry.getInstrumentation()
        val file=File(inst.targetContext.getExternalFilesDir(null),"$name.png")
        inst.uiAutomation.takeScreenshot()?.let { bitmap -> file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) };bitmap.recycle() }
        val copy=inst.uiAutomation.executeShellCommand("cp ${file.absolutePath} /sdcard/Download/hesabyar-$name.png")
        android.os.ParcelFileDescriptor.AutoCloseInputStream(copy).use{it.readBytes()}
    }
}
