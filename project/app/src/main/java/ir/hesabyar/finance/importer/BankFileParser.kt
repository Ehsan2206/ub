package ir.hesabyar.finance.importer

import android.content.Context
import android.net.Uri
import ir.hesabyar.finance.data.BankEntry
import ir.hesabyar.finance.data.FinanceRepository
import ir.hesabyar.finance.data.TransactionType
import ir.hesabyar.finance.util.DateUtil
import ir.hesabyar.finance.util.normalizeDigits
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.ByteArrayInputStream
import java.security.MessageDigest
import java.util.zip.ZipInputStream

object BankFileParser {
    data class Result(val entries: List<BankEntry>, val warnings: List<String>)

    fun parse(context: Context, uri: Uri, repository: FinanceRepository, valuesAreRial: Boolean, accountId: Long?): Result {
        val name = queryName(context, uri).lowercase()
        val rows = context.contentResolver.openInputStream(uri)?.use { input ->
            val bytes = input.readBytes()
            if (name.endsWith(".xlsx")) readXlsx(bytes) else readDelimited(decodeText(bytes).lineSequence().toList())
        } ?: return Result(emptyList(), listOf("فایل قابل خواندن نیست"))
        if (rows.size < 2) return Result(emptyList(), listOf("ردیف کافی در فایل پیدا نشد"))

        val headerIndex = rows.indexOfFirst { row -> row.any { it.contains("تاریخ") || it.lowercase().contains("date") } }.takeIf { it >= 0 } ?: 0
        val headers = rows[headerIndex].map { clean(it) }
        fun col(vararg keys: String): Int? = headers.indexOfFirst { h -> keys.any { k -> h.contains(k) } }.takeIf { it >= 0 }
        val dateCol = col("تاریخ", "date")
        val descCol = col("شرح", "توضیح", "description", "details", "عنوان")
        val debitCol = col("برداشت", "بدهکار", "debit")
        val creditCol = col("واریز", "بستانکار", "credit")
        val amountCol = col("مبلغ", "amount")
        val balanceCol = col("مانده", "balance")
        val warnings = mutableListOf<String>()
        if (dateCol == null) warnings += "ستون تاریخ به‌صورت قطعی شناسایی نشد؛ برخی ردیف‌ها ممکن است وارد نشوند."
        if (debitCol == null && creditCol == null && amountCol == null) warnings += "ستون مبلغ شناسایی نشد."

        val factor = if (valuesAreRial) 1L else 10L
        val out = rows.drop(headerIndex + 1).mapNotNull { row ->
            val dateText = dateCol?.let { row.getOrNull(it) }.orEmpty()
            val occurredAt = DateUtil.parseFlexible(dateText) ?: return@mapNotNull null
            val debit = debitCol?.let { parseMoney(row.getOrNull(it).orEmpty()) } ?: 0L
            val credit = creditCol?.let { parseMoney(row.getOrNull(it).orEmpty()) } ?: 0L
            val rawAmount = amountCol?.let { parseMoney(row.getOrNull(it).orEmpty()) } ?: 0L
            val type: TransactionType
            val amountRaw: Long
            when {
                debit > 0 -> { type = TransactionType.EXPENSE; amountRaw = debit }
                credit > 0 -> { type = TransactionType.INCOME; amountRaw = credit }
                rawAmount < 0 -> { type = TransactionType.EXPENSE; amountRaw = -rawAmount }
                rawAmount > 0 -> { type = TransactionType.INCOME; amountRaw = rawAmount }
                else -> return@mapNotNull null
            }
            val amount = Math.multiplyExact(amountRaw, factor)
            val desc = descCol?.let { row.getOrNull(it) }.orEmpty().ifBlank { "تراکنش بانکی" }
            val balance = balanceCol?.let { parseMoney(row.getOrNull(it).orEmpty()).takeIf { b -> b != 0L }?.let { Math.multiplyExact(it, factor) } }
            val fp = sha256("$occurredAt|$amount|${type.name}|$desc")
            val match = repository.findMatch(amount, type, occurredAt, accountId)
            BankEntry(occurredAt = occurredAt, amount = amount, type = type, description = desc, balanceAfter = balance, matchedTransactionId = match, fingerprint = fp)
        }
        return Result(out, warnings)
    }

    fun queryName(context: Context, uri: Uri): String {
        context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) return c.getString(0) ?: "bank-file"
        }
        return uri.lastPathSegment ?: "bank-file"
    }

    private fun clean(v: String) = v.normalizeDigits().trim().lowercase().replace("ي", "ی").replace("ك", "ک")
    private fun parseMoney(v: String): Long {
        val s = v.normalizeDigits().replace(",", "").replace("٬", "").replace(" ", "").replace("ریال", "").replace("تومان", "")
        return Regex("-?\\d+(?:\\.\\d+)?").find(s)?.value?.toDoubleOrNull()?.toLong() ?: 0L
    }
    private fun sha256(s: String): String = MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }


    private fun decodeText(bytes: ByteArray): String {
        if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) return bytes.copyOfRange(2, bytes.size).toString(Charsets.UTF_16LE)
        if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) return bytes.copyOfRange(2, bytes.size).toString(Charsets.UTF_16BE)
        val data = if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) bytes.copyOfRange(3, bytes.size) else bytes
        val utf8 = runCatching {
            val decoder = Charsets.UTF_8.newDecoder()
            decoder.onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
            decoder.onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
            decoder.decode(java.nio.ByteBuffer.wrap(data)).toString()
        }.getOrNull()
        return utf8 ?: String(data, runCatching { java.nio.charset.Charset.forName("windows-1256") }.getOrDefault(Charsets.UTF_8))
    }

    private fun readDelimited(lines: List<String>): List<List<String>> {
        if (lines.isEmpty()) return emptyList()
        val sample = lines.take(5).joinToString("\n")
        val delimiter = listOf('\t', ',', ';', '|').maxByOrNull { d -> sample.count { it == d } } ?: ','
        return lines.map { parseCsvLine(it, delimiter) }
    }

    private fun parseCsvLine(line: String, delimiter: Char): List<String> {
        val result = mutableListOf<String>(); val cell = StringBuilder(); var quoted = false; var i = 0
        while (i < line.length) {
            val ch = line[i]
            when {
                ch == '"' && quoted && i + 1 < line.length && line[i + 1] == '"' -> { cell.append('"'); i++ }
                ch == '"' -> quoted = !quoted
                ch == delimiter && !quoted -> { result += cell.toString(); cell.clear() }
                else -> cell.append(ch)
            }
            i++
        }
        result += cell.toString(); return result
    }

    private fun readXlsx(bytes: ByteArray): List<List<String>> {
        var shared: ByteArray? = null; var sheet: ByteArray? = null
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var e = zip.nextEntry
            while (e != null) {
                when (e.name) {
                    "xl/sharedStrings.xml" -> shared = zip.readBytes()
                    "xl/worksheets/sheet1.xml" -> sheet = zip.readBytes()
                }
                zip.closeEntry(); e = zip.nextEntry
            }
        }
        val sharedStrings = shared?.let { parseSharedStrings(it) }.orEmpty()
        return sheet?.let { parseSheet(it, sharedStrings) }.orEmpty()
    }

    private fun parser(bytes: ByteArray): XmlPullParser = XmlPullParserFactory.newInstance().newPullParser().apply {
        setInput(ByteArrayInputStream(bytes), "UTF-8")
    }

    private fun parseSharedStrings(bytes: ByteArray): List<String> {
        val p = parser(bytes); val out = mutableListOf<String>(); var event = p.eventType; var inSi = false; val b = StringBuilder()
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (p.name) { "si" -> { inSi = true; b.clear() }; "t" -> if (inSi) b.append(p.nextText()) }
                XmlPullParser.END_TAG -> if (p.name == "si") { out += b.toString(); inSi = false }
            }
            event = p.next()
        }
        return out
    }

    private fun parseSheet(bytes: ByteArray, shared: List<String>): List<List<String>> {
        val p = parser(bytes); val out = mutableListOf<List<String>>(); var event = p.eventType
        var row = sortedMapOf<Int, String>(); var cellRef = ""; var cellType = ""; var value = ""; var inline = ""
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (p.name) {
                    "row" -> row = sortedMapOf()
                    "c" -> { cellRef = p.getAttributeValue(null, "r") ?: "A1"; cellType = p.getAttributeValue(null, "t") ?: ""; value = ""; inline = "" }
                    "v" -> value = p.nextText()
                    "t" -> if (cellType == "inlineStr") inline += p.nextText()
                }
                XmlPullParser.END_TAG -> when (p.name) {
                    "c" -> {
                        val idx = columnIndex(cellRef)
                        val resolved = when (cellType) { "s" -> shared.getOrNull(value.toIntOrNull() ?: -1).orEmpty(); "inlineStr" -> inline; else -> value }
                        row[idx] = resolved
                    }
                    "row" -> if (row.isNotEmpty()) out += List((row.keys.maxOrNull() ?: -1) + 1) { row[it].orEmpty() }
                }
            }
            event = p.next()
        }
        return out
    }

    private fun columnIndex(ref: String): Int {
        val letters = ref.takeWhile { it.isLetter() }.uppercase(); var n = 0
        letters.forEach { n = n * 26 + (it.code - 'A'.code + 1) }
        return (n - 1).coerceAtLeast(0)
    }
}
