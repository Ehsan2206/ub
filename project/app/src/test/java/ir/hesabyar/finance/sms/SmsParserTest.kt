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
        assertNull(p.amountRial);assertEquals(0L,p.balanceRial);assertEquals("123456789",p.accountIdentity)
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
    @Test fun namedSendersAndCanonicalBankNames() {
        assertEquals("بانک پاسارگاد",BankRegistry.detect("B.Pasargad","واریز:100 ریال"))
        assertEquals("بانک تجارت",BankRegistry.detect("TejaratBank","برداشت:100 ریال"))
        assertEquals("بانک پاسارگاد",BankRegistry.canonicalName("ویپاد"))
        assertEquals("بلوبانک سامان",BankRegistry.detect("BluBank","برداشت:100 ریال"))
        assertEquals("بلوبانک سامان",BankRegistry.detect("20000","بلو\nواریز پول"))
    }
    @Test fun bluDepositRoundAndUnknownNumericSender() {
        val income=SmsParser.parse("BluBank","بلو\nواریز پول\nمشتری عزیز، 3,130,000ریال به حساب شما نشست.\nموجودی: 10,720,458 ریال\n۱۹:۱۴\n۱۴۰۵.۰۸.۱۱",100)!!
        assertEquals(3130000L,income.amountRial);assertEquals(TransactionType.INCOME,income.type);assertEquals(10720458L,income.balanceRial)
        val expense=SmsParser.parse("987654321","بلو\nبرداشت رُند\nمشتری عزیز، 640,000 ریال از حساب شما پرید.\nو 10,000 ریال هم رند شد.\nموجودی: 7,642,736 ریال\n۲۲:۰۴\n۱۴۰۵.۰۸.۱۰",101)!!
        assertEquals(650000L,expense.amountRial);assertEquals(10000L,expense.internalAmount);assertEquals(TransactionType.EXPENSE,expense.type);assertEquals(7642736L,expense.balanceRial)
        assertEquals(AccountIdentity.UNIDENTIFIED,income.accountIdentity);assertEquals(income.accountIdentity,expense.accountIdentity)
        assertFalse(expense.details.contains("مشتری"))
    }
    @Test fun differentSendersAndFormattedIdsUseSameIdentity() {
        val a=SmsParser.parse("B.Pasargad","برداشت:100 ریال\nحساب:123.456.789",100)!!
        val b=SmsParser.parse("BankPasargad","برداشت:100 ریال\nحساب:123456789",101)!!
        assertEquals(a.bankName,b.bankName);assertEquals(a.accountIdentity,b.accountIdentity)
        val c=SmsParser.parse("TejaratBank","برداشت:100 ریال\nسپرده:98765432\nکارت:6037990012345678\nانتقال به کارت:1111222233334444",102)!!
        assertEquals(listOf("98765432","6037990012345678"),c.accountIdentities)
    }
    @Test fun noIdentifierMustNeverUseSenderAsAccount() {
        assertEquals(SmsParser.parse("200033","برداشت:100 ریال",1)!!.accountIdentity,SmsParser.parse("BankMellat","برداشت:100 ریال",2)!!.accountIdentity)
        assertTrue(AccountIdentity.matches("6037*1234","6037999911111234"))
        assertFalse(AccountIdentity.matches("11111234","22221234"))
    }
    @Test fun spacedCardNumbersAreNotReducedToTheirSharedPrefix() {
        val a=SmsParser.parse("TejaratBank","برداشت100ریال\nکارت:6037 9900 1111 1234",1)!!
        val b=SmsParser.parse("TejaratBank","برداشت100ریال\nکارت:6037 9900 2222 1234",2)!!
        assertEquals("6037990011111234",a.accountIdentity);assertNotEquals(a.accountIdentity,b.accountIdentity)
        val c=SmsParser.parse("TejaratBank","برداشت100ریال\nکارت:****1234",3)!!
        assertEquals("*1234",c.accountIdentity)
    }
    @Test fun overflowSkipped() {assertNull(SmsParser.parse("200033","واریز:9223372036854775807 تومان",10))}
}
