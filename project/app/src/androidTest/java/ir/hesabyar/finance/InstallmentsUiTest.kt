package ir.hesabyar.finance
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.*
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import ir.hesabyar.finance.ui.screens.InstallmentsList
import ir.hesabyar.finance.ui.theme.HesabYarTheme
import ir.hesabyar.finance.data.Installment
import ir.hesabyar.finance.util.DateUtil
import org.junit.Rule
import org.junit.Test
import java.time.ZoneId
class InstallmentsUiTest {
    @get:Rule val compose=createComposeRule()
    @Test fun allSevenIncludingNextYearAreVisible() {
        val first=DateUtil.fromJalali(1405,8,15)
        val items=(0..6).map { i -> Installment(id=(i+1).toLong(),title="قسط آزمایشی ${i+1}",amount=1000,dueAt=DateUtil.plusJalaliMonths(first,i.toLong()).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),accountId=null,personId=null) }
        compose.setContent { HesabYarTheme { Column(Modifier.verticalScroll(rememberScrollState())) { InstallmentsList(items,{}) } } }
        compose.onNodeWithText("7 قسط پرداخت‌نشده").assertExists()
        compose.onNodeWithText("سال ۱۴۰۵ • 5 قسط").assertExists()
        compose.onNodeWithText("سال ۱۴۰۶ • 2 قسط").assertExists()
        compose.onNodeWithText("قسط آزمایشی 7").performScrollTo().assertIsDisplayed()
    }
}
