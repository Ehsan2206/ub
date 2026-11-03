package ir.hesabyar.finance.sms

import ir.hesabyar.finance.util.normalizeDigits

/** Exact matches only: a short code is never interpreted as a phone-number prefix. */
data class BankDefinition(val name: String, val senders: List<String>, val aliases: List<String>)
object BankRegistry {
    val banks = listOf(
        BankDefinition("بانک ملی ایران", listOf("300017","700717","30009417","BankMelli","BMI"), listOf("بانک ملی ایران","بانک ملی")),
        BankDefinition("بانک سپه", listOf("200020","200021","200022","BankSepah","Sepah"), listOf("بانک سپه")),
        BankDefinition("بانک ملت", listOf("200033","200030","BankMellat"), listOf("بانک ملت")),
        BankDefinition("بانک صادرات ایران", listOf("200060","BankSaderat","BSI"), listOf("بانک صادرات ایران","بانک صادرات")),
        BankDefinition("بانک تجارت", listOf("200070","20007010","BankTejarat"), listOf("بانک تجارت")),
        BankDefinition("بانک کشاورزی", listOf("2000911","BankKeshavarzi"), listOf("بانک کشاورزی")),
        BankDefinition("بانک مسکن", listOf("300014","BankMaskan"), listOf("بانک مسکن")),
        BankDefinition("بانک رفاه کارگران", listOf("300066","300044","BankRefah"), listOf("بانک رفاه کارگران","بانک رفاه")),
        BankDefinition("بانک سامان", listOf("20000","BankSaman","SamanBank"), listOf("بانک سامان")),
        BankDefinition("بانک پاسارگاد", listOf("1000900","50009000","BankPasargad"), listOf("بانک پاسارگاد")),
        BankDefinition("بانک پارسیان", listOf("300054","50001099","300055","30007","BankParsian"), listOf("بانک پارسیان")),
        BankDefinition("بانک اقتصاد نوین", listOf("200050","ENBank"), listOf("بانک اقتصاد نوین","بانک اقتصادنوین")),
        BankDefinition("بانک سینا", listOf("200038","SinaBank"), listOf("بانک سینا")),
        BankDefinition("بانک سرمایه", listOf("100088","SarmayehBank"), listOf("بانک سرمایه")),
        BankDefinition("بانک توسعه تعاون", listOf("1000700","100070007","ToseeTaavon"), listOf("بانک توسعه تعاون")),
        BankDefinition("بانک قرض‌الحسنه مهر ایران", listOf("QMehrBank","MehrIran","QMB"), listOf("بانک قرض الحسنه مهر ایران","بانک مهر ایران","قرض الحسنه مهر ایران")),
        BankDefinition("بانک قرض‌الحسنه رسالت", listOf("RQBank","ResalatBank"), listOf("بانک قرض الحسنه رسالت","بانک رسالت","قرض الحسنه رسالت")),
        BankDefinition("بانک شهر", listOf("ShahrBank","BankShahr"), listOf("بانک شهر")),
        BankDefinition("بانک دی", listOf("DayBank","BankDay"), listOf("بانک دی")),
        BankDefinition("بانک ایران زمین", listOf("IZBank","IranZamin"), listOf("بانک ایران زمین")),
        BankDefinition("بانک گردشگری", listOf("TourismBank"), listOf("بانک گردشگری")),
        BankDefinition("بانک کارآفرین", listOf("KarafarinBank"), listOf("بانک کارآفرین","بانک کارافرین")),
        BankDefinition("بانک خاورمیانه", listOf("MiddleEastBank"), listOf("بانک خاورمیانه")),
        BankDefinition("پست بانک ایران", listOf("PostBank"), listOf("پست بانک ایران","پست بانک")),
        BankDefinition("بانک صنعت و معدن", listOf("BIM","BankSanat"), listOf("بانک صنعت و معدن")),
        BankDefinition("بانک توسعه صادرات ایران", listOf("EDBI"), listOf("بانک توسعه صادرات")),
        BankDefinition("بانک مشترک ایران و ونزوئلا", listOf("IVBB"), listOf("بانک ایران و ونزوئلا","بانک مشترک ایران ونزوئلا")),
        BankDefinition("مؤسسه اعتباری ملل", listOf("MelalBank"), listOf("موسسه اعتباری ملل","مؤسسه اعتباری ملل","بانک ملل")),
        // Historical identities are retained for importing old inbox messages; no license claim.
        BankDefinition("بانک آینده", listOf("AyandehBank"), listOf("بانک آینده")),
        BankDefinition("بانک انصار", listOf("AnsarBank"), listOf("بانک انصار")),
        BankDefinition("بانک قوامین", listOf("Ghavamin"), listOf("بانک قوامین")),
        BankDefinition("بانک حکمت ایرانیان", listOf("HekmatBank"), listOf("بانک حکمت ایرانیان")),
        BankDefinition("بانک مهر اقتصاد", listOf("MehrEghtesad"), listOf("بانک مهر اقتصاد")),
        BankDefinition("مؤسسه کوثر", listOf("KosarBank"), listOf("موسسه اعتباری کوثر","مؤسسه اعتباری کوثر")),
        BankDefinition("مؤسسه نور", listOf("NoorBank"), listOf("موسسه اعتباری نور","مؤسسه اعتباری نور")),
        BankDefinition("مؤسسه کاسپین", listOf("Caspian"), listOf("موسسه اعتباری کاسپین","مؤسسه اعتباری کاسپین")),
        BankDefinition("صندوق امداد ولایت", listOf("EmdadVelayat"), listOf("صندوق امداد ولایت","قرض الحسنه امداد ولایت"))
    )
    fun normalize(value: String) = value.normalizeDigits().lowercase().replace('ي','ی').replace('ك','ک').replace('\u200c',' ').replace(Regex("\\s+")," ").trim()
    fun senderKey(value: String): String {
        var s=normalize(value).replace(Regex("[\\s()\\-]"),"")
        s=when { s.startsWith("+98") -> s.drop(3); s.startsWith("0098") -> s.drop(4); s.startsWith("098") -> s.drop(3); else -> s }
        return s
    }
    fun detect(sender: String, body: String, custom: Map<String,String> = emptyMap()): String? {
        val key=senderKey(sender)
        custom[key]?.let { return it }
        banks.firstOrNull { bank -> bank.senders.any { senderKey(it)==key } }?.let { return it.name }
        // Read only the header; a recipient bank mentioned in a transfer must not rename the source account.
        val header=normalize(body).lineSequence().take(2).joinToString(" ").take(100)
        return banks.flatMap { b -> b.aliases.map { it to b.name } }.sortedByDescending { it.first.length }
            .firstOrNull { header.startsWith(normalize(it.first)) }?.second
    }
}
