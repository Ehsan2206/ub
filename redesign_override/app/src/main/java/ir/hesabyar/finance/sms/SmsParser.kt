package ir.hesabyar.finance.sms

import ir.hesabyar.finance.data.TransactionType
import ir.hesabyar.finance.util.normalizeDigits
import java.security.MessageDigest
import kotlin.math.max
import kotlin.math.min

data class ParsedSms(
    val amountToman: Long,
    val type: TransactionType,
    val reason: String,
    val accountLast4: String,
    val externalKey: String
)

private enum class AmountUnit { TOMAN, RIAL, UNKNOWN }

private data class AmountCandidate(
    val rawAmount: Long,
    val unit: AmountUnit,
    val score: Int,
    val start: Int,
    val end: Int
)

object SmsParser {
    private val expenseWords = listOf("برداشت", "خرید", "پرداخت", "کسر", "انتقال به", "پرداخت اینترنتی", "پوز", "pos")
    private val incomeWords = listOf("واریز", "دریافت", "انتقال از", "افزایش", "بستانکار", "واریزی")
    private val balanceWords = listOf("مانده", "موجودی", "balance", "باقیمانده", "باقی مانده")

    private const val NUMBER = "[0-9][0-9,٬. \\t\\u00A0]{0,20}"
    private const val UNIT = "ریال|تومان|تومن|rial|irr|toman"

    private val labeledAmount = Regex(
        """(?:مبلغ(?:[ \\t\\u00A0]+(?:تراکنش|خرید|برداشت|واریز|پرداخت))?|amount)[ \\t\\u00A0]*[:：\-]?[ \\t\\u00A0]*($NUMBER)[ \\t\\u00A0]*($UNIT)?""",
        RegexOption.IGNORE_CASE
    )
    private val amountThenUnit = Regex("""($NUMBER)[ \\t\\u00A0]*($UNIT)""", RegexOption.IGNORE_CASE)
    private val unitThenAmount = Regex("""($UNIT)[ \\t\\u00A0]*($NUMBER)""", RegexOption.IGNORE_CASE)

    fun parse(sender: String, body: String, timestamp: Long): ParsedSms? {
        val normalized = body.normalizeDigits().replace("ي", "ی").replace("ك", "ک")
        val lower = normalized.lowercase()
        val type = when {
            expenseWords.any { lower.contains(it) } -> TransactionType.EXPENSE
            incomeWords.any { lower.contains(it) } -> TransactionType.INCOME
            else -> return null
        }

        val amount = extractAmount(normalized) ?: return null
        val toman = when (amount.unit) {
            AmountUnit.RIAL -> rialToToman(amount.rawAmount)
            AmountUnit.TOMAN, AmountUnit.UNKNOWN -> amount.rawAmount
        }
        if (toman <= 0) return null

        val last4 = Regex("(?:کارت|حساب|سپرده|شماره)[^0-9]{0,18}([0-9]{4})(?![0-9])").find(normalized)?.groupValues?.getOrNull(1)
            ?: Regex("[*xX-]{2,}([0-9]{4})").find(normalized)?.groupValues?.getOrNull(1).orEmpty()
        val reason = when (type) {
            TransactionType.EXPENSE -> if (lower.contains("خرید")) "خرید بانکی" else "برداشت بانکی"
            TransactionType.INCOME -> "واریز بانکی"
        }
        return ParsedSms(toman, type, reason, last4, sha256("$sender|$timestamp|$body"))
    }

    private fun extractAmount(text: String): AmountCandidate? {
        val candidates = mutableListOf<AmountCandidate>()

        labeledAmount.findAll(text).forEach { match ->
            val raw = parseNumber(match.groupValues.getOrNull(1).orEmpty()) ?: return@forEach
            val explicitUnit = parseUnit(match.groupValues.getOrNull(2).orEmpty())
            val unit = if (explicitUnit == AmountUnit.UNKNOWN) inferNearbyUnit(text, match.range.first, match.range.last + 1) else explicitUnit
            candidates += AmountCandidate(
                rawAmount = raw,
                unit = unit,
                score = scoreCandidate(text, match.range.first, match.range.last + 1, unit, labeled = true),
                start = match.range.first,
                end = match.range.last + 1
            )
        }

        amountThenUnit.findAll(text).forEach { match ->
            val raw = parseNumber(match.groupValues.getOrNull(1).orEmpty()) ?: return@forEach
            val unit = parseUnit(match.groupValues.getOrNull(2).orEmpty())
            candidates += AmountCandidate(
                rawAmount = raw,
                unit = unit,
                score = scoreCandidate(text, match.range.first, match.range.last + 1, unit, labeled = false),
                start = match.range.first,
                end = match.range.last + 1
            )
        }

        unitThenAmount.findAll(text).forEach { match ->
            val raw = parseNumber(match.groupValues.getOrNull(2).orEmpty()) ?: return@forEach
            val unit = parseUnit(match.groupValues.getOrNull(1).orEmpty())
            candidates += AmountCandidate(
                rawAmount = raw,
                unit = unit,
                score = scoreCandidate(text, match.range.first, match.range.last + 1, unit, labeled = false),
                start = match.range.first,
                end = match.range.last + 1
            )
        }

        return candidates
            .filter { it.rawAmount > 0 }
            .distinctBy { Triple(it.rawAmount, it.start, it.end) }
            .maxWithOrNull(compareBy<AmountCandidate> { it.score }.thenBy { it.rawAmount })
    }

    private fun scoreCandidate(text: String, start: Int, end: Int, unit: AmountUnit, labeled: Boolean): Int {
        val lineStart = text.lastIndexOf('\n', max(0, start - 1)).let { if (it < 0) 0 else it + 1 }
        val lineEnd = text.indexOf('\n', end).let { if (it < 0) text.length else it }
        val line = text.substring(lineStart, lineEnd).lowercase()
        val nearStart = max(0, start - 36)
        val nearEnd = min(text.length, end + 36)
        val near = text.substring(nearStart, nearEnd).lowercase()

        var score = 0
        if (labeled || line.contains("مبلغ") || line.contains("amount")) score += 120
        if (expenseWords.any { line.contains(it) } || incomeWords.any { line.contains(it) }) score += 70
        else if (expenseWords.any { near.contains(it) } || incomeWords.any { near.contains(it) }) score += 35
        if (unit != AmountUnit.UNKNOWN) score += 35
        if (balanceWords.any { line.contains(it) }) score -= 140
        if (line.contains("شماره کارت") || line.contains("کارت ****") || line.contains("کارت****")) score -= 35
        return score
    }

    private fun inferNearbyUnit(text: String, start: Int, end: Int): AmountUnit {
        val from = max(0, start - 18)
        val to = min(text.length, end + 18)
        val nearby = text.substring(from, to)
        val units = Regex("""($UNIT)""", RegexOption.IGNORE_CASE).findAll(nearby).map { parseUnit(it.value) }.filter { it != AmountUnit.UNKNOWN }.toList()
        return if (units.distinct().size == 1) units.first() else AmountUnit.UNKNOWN
    }

    private fun parseUnit(raw: String): AmountUnit = when (raw.trim().lowercase()) {
        "ریال", "rial", "irr" -> AmountUnit.RIAL
        "تومان", "تومن", "toman" -> AmountUnit.TOMAN
        else -> AmountUnit.UNKNOWN
    }

    private fun parseNumber(raw: String): Long? = raw
        .replace(",", "")
        .replace("٬", "")
        .replace(".", "")
        .replace(" ", "")
        .replace("\t", "")
        .replace("\u00A0", "")
        .toLongOrNull()

    private fun rialToToman(rial: Long): Long = (rial + 5L) / 10L

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray())
        .joinToString("") { "%02x".format(it) }
}
