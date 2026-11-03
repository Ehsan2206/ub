package ir.hesabyar.finance.util

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

object DateUtil {
    private val zone: ZoneId get() = ZoneId.systemDefault()
    private val weekDays = arrayOf("دوشنبه", "سه‌شنبه", "چهارشنبه", "پنجشنبه", "جمعه", "شنبه", "یکشنبه")
    private val months = arrayOf("فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور", "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند")

    data class JalaliDate(val year: Int, val month: Int, val day: Int)

    fun formatDate(epoch: Long): String = formatLocalDate(Instant.ofEpochMilli(epoch).atZone(zone).toLocalDate())

    fun formatLocalDate(date: LocalDate): String {
        val j = toJalali(date)
        return "${fa(j.year)}/${fa2(j.month)}/${fa2(j.day)}"
    }

    fun formatLongDate(epoch: Long): String = formatLongDate(Instant.ofEpochMilli(epoch).atZone(zone).toLocalDate())

    fun formatLongDate(date: LocalDate): String {
        val j = toJalali(date)
        return "${weekDays[date.dayOfWeek.value - 1]} ${fa(j.day)} ${months[j.month - 1]} ${fa(j.year)}"
    }

    fun jalaliMonthName(date: LocalDate = LocalDate.now(zone)): String {
        val j = toJalali(date)
        return "${months[j.month - 1]} ${fa(j.year)}"
    }

    fun today(): LocalDate = LocalDate.now(zone)

    fun dayRange(date: LocalDate): Pair<Long, Long> {
        val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
        return start to (date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1)
    }

    fun jalaliWeekRange(date: LocalDate = today()): Pair<Long, Long> {
        val daysFromSaturday = (date.dayOfWeek.value - DayOfWeek.SATURDAY.value + 7) % 7
        val startDate = date.minusDays(daysFromSaturday.toLong())
        val start = startDate.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = startDate.plusDays(7).atStartOfDay(zone).toInstant().toEpochMilli() - 1
        return start to end
    }

    fun jalaliMonthRange(date: LocalDate = today()): Pair<Long, Long> {
        val j = toJalali(date)
        val startDate = fromJalali(j.year, j.month, 1)
        val nextDate = plusJalaliMonths(startDate, 1)
        return startDate.atStartOfDay(zone).toInstant().toEpochMilli() to
            (nextDate.atStartOfDay(zone).toInstant().toEpochMilli() - 1)
    }

    fun jalaliYearRange(date: LocalDate = today()): Pair<Long, Long> {
        val j = toJalali(date)
        val startDate = fromJalali(j.year, 1, 1)
        val nextDate = fromJalali(j.year + 1, 1, 1)
        return startDate.atStartOfDay(zone).toInstant().toEpochMilli() to
            (nextDate.atStartOfDay(zone).toInstant().toEpochMilli() - 1)
    }

    fun plusJalaliMonths(date: LocalDate, monthsToAdd: Long): LocalDate {
        val j = toJalali(date)
        val zeroBased = (j.year.toLong() * 12L + (j.month - 1)) + monthsToAdd
        val targetYear = Math.floorDiv(zeroBased, 12L).toInt()
        val targetMonth = Math.floorMod(zeroBased, 12L).toInt() + 1
        val targetDay = j.day.coerceAtMost(daysInJalaliMonth(targetYear, targetMonth))
        return fromJalali(targetYear, targetMonth, targetDay)
    }

    fun daysInJalaliMonth(year: Int, month: Int): Int = when {
        month in 1..6 -> 31
        month in 7..11 -> 30
        month == 12 -> if (isJalaliLeap(year)) 30 else 29
        else -> 30
    }

    fun isJalaliLeap(year: Int): Boolean = jalCal(year)[0] == 0

    fun toJalali(date: LocalDate): JalaliDate {
        val r = d2j(g2d(date.year, date.monthValue, date.dayOfMonth))
        return JalaliDate(r[0], r[1], r[2])
    }

    fun fromJalali(year: Int, month: Int, day: Int): LocalDate {
        require(month in 1..12)
        require(day in 1..daysInJalaliMonth(year, month))
        val g = d2g(j2d(year, month, day))
        return LocalDate.of(g[0], g[1], g[2])
    }

    fun parseFlexible(raw: String): Long? {
        val s = raw.normalizeDigits().trim().replace('-', '/').replace('.', '/')
        s.toDoubleOrNull()?.let { serial ->
            if (serial in 10_000.0..100_000.0) {
                val d = LocalDate.of(1899, 12, 30).plusDays(serial.toLong())
                return d.atStartOfDay(zone).toInstant().toEpochMilli()
            }
        }
        val m = Regex("(\\d{4})/(\\d{1,2})/(\\d{1,2})").find(s) ?: return null
        val y = m.groupValues[1].toInt(); val mo = m.groupValues[2].toInt(); val d = m.groupValues[3].toInt()
        return runCatching {
            val greg = if (y >= 1700) LocalDate.of(y, mo, d) else fromJalali(y, mo, d)
            greg.atStartOfDay(zone).toInstant().toEpochMilli()
        }.getOrNull()
    }

    private fun fa(value: Int): String = value.toString().toPersianDigits()
    private fun fa2(value: Int): String = value.toString().padStart(2, '0').toPersianDigits()

    private fun String.toPersianDigits(): String = map { c ->
        when (c) {
            '0' -> '۰'; '1' -> '۱'; '2' -> '۲'; '3' -> '۳'; '4' -> '۴'
            '5' -> '۵'; '6' -> '۶'; '7' -> '۷'; '8' -> '۸'; '9' -> '۹'
            else -> c
        }
    }.joinToString("")

    private fun div(a: Int, b: Int): Int = a / b
    private fun mod(a: Int, b: Int): Int = a - div(a, b) * b

    private fun jalCal(jy: Int): IntArray {
        val breaks = intArrayOf(-61, 9, 38, 199, 426, 686, 756, 818, 1111, 1181, 1210, 1635, 2060, 2097, 2192, 2262, 2324, 2394, 2456, 3178)
        val gy = jy + 621
        var leapJ = -14
        var jp = breaks[0]
        var jump = 0
        for (i in 1 until breaks.size) {
            val jm = breaks[i]
            jump = jm - jp
            if (jy < jm) break
            leapJ += div(jump, 33) * 8 + div(mod(jump, 33), 4)
            jp = jm
        }
        var n = jy - jp
        leapJ += div(n, 33) * 8 + div(mod(n, 33) + 3, 4)
        if (mod(jump, 33) == 4 && jump - n == 4) leapJ++
        val leapG = div(gy, 4) - div((div(gy, 100) + 1) * 3, 4) - 150
        val march = 20 + leapJ - leapG
        if (jump - n < 6) n = n - jump + div(jump + 4, 33) * 33
        var leap = mod(mod(n + 1, 33) - 1, 4)
        if (leap == -1) leap = 4
        return intArrayOf(leap, gy, march)
    }

    private fun j2d(jy: Int, jm: Int, jd: Int): Int {
        val r = jalCal(jy)
        return g2d(r[1], 3, r[2]) + (jm - 1) * 31 - div(jm, 7) * (jm - 7) + jd - 1
    }

    private fun d2j(jdn: Int): IntArray {
        val g = d2g(jdn)
        var jy = g[0] - 621
        val r = jalCal(jy)
        val jdn1f = g2d(g[0], 3, r[2])
        var k = jdn - jdn1f
        val jm: Int
        val jd: Int
        if (k >= 0) {
            if (k <= 185) {
                jm = 1 + div(k, 31)
                jd = mod(k, 31) + 1
                return intArrayOf(jy, jm, jd)
            }
            k -= 186
        } else {
            jy -= 1
            k += 179
            if (r[0] == 1) k += 1
        }
        jm = 7 + div(k, 30)
        jd = mod(k, 30) + 1
        return intArrayOf(jy, jm, jd)
    }

    private fun g2d(gy: Int, gm: Int, gd: Int): Int {
        var d = div((gy + div(gm - 8, 6) + 100100) * 1461, 4) + div(153 * mod(gm + 9, 12) + 2, 5) + gd - 34840408
        d = d - div(div(gy + 100100 + div(gm - 8, 6), 100) * 3, 4) + 752
        return d
    }

    private fun d2g(jdn: Int): IntArray {
        var j = 4 * jdn + 139361631
        j += div(div(4 * jdn + 183187720, 146097) * 3, 4) * 4 - 3908
        val i = div(mod(j, 1461), 4) * 5 + 308
        val gd = div(mod(i, 153), 5) + 1
        val gm = mod(div(i, 153), 12) + 1
        val gy = div(j, 1461) - 100100 + div(8 - gm, 6)
        return intArrayOf(gy, gm, gd)
    }
}
