package ir.hesabyar.finance.util

import java.text.NumberFormat
import java.util.Locale

object Money {
    private val formatter = NumberFormat.getNumberInstance(Locale.forLanguageTag("fa-IR"))
    fun format(amount: Long): String = "${formatter.format(amount)} ریال"
    fun compact(amount: Long): String = when {
        kotlin.math.abs(amount) >= 1_000_000_000 -> "${trim(amount / 1_000_000_000.0)} میلیارد"
        kotlin.math.abs(amount) >= 1_000_000 -> "${trim(amount / 1_000_000.0)} میلیون"
        kotlin.math.abs(amount) >= 1_000 -> "${trim(amount / 1_000.0)} هزار"
        else -> formatter.format(amount)
    }
    private fun trim(v: Double): String = if (v % 1.0 == 0.0) v.toLong().toString() else String.format(Locale.US, "%.1f", v)

    fun parse(input: String): Long = input.normalizeDigits().replace(",", "").replace("٬", "").replace(" ", "").filter { it.isDigit() || it == '-' }.toLongOrNull() ?: 0L
}

fun String.normalizeDigits(): String = buildString(length) {
    for (ch in this@normalizeDigits) append(
        when (ch) {
            '۰','٠' -> '0'; '۱','١' -> '1'; '۲','٢' -> '2'; '۳','٣' -> '3'; '۴','٤' -> '4'
            '۵','٥' -> '5'; '۶','٦' -> '6'; '۷','٧' -> '7'; '۸','٨' -> '8'; '۹','٩' -> '9'
            else -> ch
        }
    )
}
