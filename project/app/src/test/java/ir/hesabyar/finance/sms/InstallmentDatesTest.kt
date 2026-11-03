package ir.hesabyar.finance.sms
import ir.hesabyar.finance.util.DateUtil
import org.junit.Assert.*
import org.junit.Test
class InstallmentDatesTest {
    @Test fun sevenMonthlyInstallmentsCrossYearWithoutLoss() {
        val start=DateUtil.fromJalali(1405,8,15)
        val dates=(0L..6L).map{DateUtil.toJalali(DateUtil.plusJalaliMonths(start,it))}
        assertEquals(listOf(1405 to 8,1405 to 9,1405 to 10,1405 to 11,1405 to 12,1406 to 1,1406 to 2),dates.map{it.year to it.month})
        assertTrue(dates.all{it.day==15})
    }
}
