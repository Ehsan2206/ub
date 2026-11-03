package ir.hesabyar.finance.sms

import org.junit.Assert.*
import org.junit.Test
import ir.hesabyar.finance.data.TransactionType

class SmsParserTest {
    @Test fun explicitRialAndBalance() {
        val p=SmsParser.parse("+9830009417","برداشت: ۱۲۳٬۴۵۶ ریال\nحساب: ۱۲۳۴۵۶۷۸۹\nمانده: ۹۸۷٬۶۵۴ ریال",100)!!
        assertEquals(123456L,p.amountRial);assertEquals(987654L,p.balanceRial);assertEquals("6789",p.accountLast4);assertEquals("بانک ملی ایران",p.bankName)
    }
    @Test fun explicitTomanMultipliesWithoutRounding() {
        val p=SmsParser.parse("BankMellat","واریز: 120 تومان\nمانده: 999 تومان",100)!!
        assertEquals(1200L,p.amountRial);assertEquals(9990L,p.balanceRial);assertEquals(TransactionType.INCOME,p.type)
    }
    @Test fun unlabeledUnitDefaultsToRial() {assertEquals(502L,SmsParser.parse("200033","برداشت:502\nمانده:9000",10)!!.amountRial)}
    @Test fun otpAdsAndUnknownSendersIgnored() {
        assertNull(SmsParser.parse("200033","رمز پویا خرید مبلغ 300 ریال: 19384",10))
        assertNull(SmsParser.parse("200033","برای خرید و دریافت وام اقدام کنید",10))
        assertNull(SmsParser.parse("09123456789","واریز 1000 ریال",10))
        assertNull(BankRegistry.detect("20003312345","واریز 1000 ریال"))
    }
    @Test fun balanceAloneIsNotATransaction() {
        val p=SmsParser.parse("BankMelli","حساب: 123.456.789\nموجودی: 0 ریال",100)!!
        assertNull(p.amountRial);assertEquals(0L,p.balanceRial);assertEquals("123.456.789",p.accountIdentity)
    }
    @Test fun noBalanceMisreadAsAmount() {val p=SmsParser.parse("200033","خرید انجام شد\nمانده:10000 ریال",10)!!;assertNull(p.amountRial)}
    @Test fun fullIdentityPreventsSuffixCollision() {
        val a=SmsParser.parse("200033","برداشت:10\nحساب:11111234",1)!!
        val b=SmsParser.parse("200033","برداشت:10\nحساب:22221234",1)!!
        assertEquals(a.accountLast4,b.accountLast4);assertNotEquals(a.accountIdentity,b.accountIdentity)
    }
    @Test fun customLocalFundAndHeaderDetection() {
        assertEquals("صندوق نمونه",SmsParser.parse("+981000999","واریز:15 ریال",10,mapOf("1000999" to "صندوق نمونه"))!!.bankName)
        assertEquals("بانک قرض‌الحسنه رسالت",SmsParser.parse("1000000","بانک قرض الحسنه رسالت\nواریز:500 ریال",10)!!.bankName)
        assertNull(SmsParser.parse("999999","انتقال به بانک ملی\nمبلغ: 500 ریال",10))
    }
    @Test fun multilineAndSignedFormats() {
        val a=SmsParser.parse("200033","برداشت\n250000\nمانده:750000",10)!!
        assertEquals(250000L,a.amountRial)
        val b=SmsParser.parse("200033","حساب:1234\n-100,000\nمانده:900,000",11)!!
        assertEquals(TransactionType.EXPENSE,b.type);assertEquals(100000L,b.amountRial)
    }
    @Test fun overflowSkipped() {assertNull(SmsParser.parse("200033","واریز:9223372036854775807 تومان",10))}
}
