package ir.hesabyar.finance.sms

/** A sender identifies a bank, never an account. Keep full visible account/card identifiers. */
object AccountIdentity {
    const val UNIDENTIFIED = "unidentified"
    fun normalize(raw:String):String {
        if(raw.isBlank() || raw.startsWith("sender:") || raw==UNIDENTIFIED) return UNIDENTIFIED
        return BankRegistry.normalize(raw).replace(Regex("[.\\-/_\\s]"),"")
            .replace(Regex("[x*]+"),"*")
    }
    fun isFull(raw:String)=raw.isNotBlank() && raw!=UNIDENTIFIED && '*' !in raw && raw.all{it.isDigit()}
    fun matches(a:String,b:String):Boolean {
        if(a==UNIDENTIFIED || b==UNIDENTIFIED) return false
        if(a==b) return true
        val mask=if('*' in a && isFull(b)) a else if('*' in b && isFull(a)) b else return false
        val full=if(mask==a)b else a
        return Regex("^"+mask.split('*').joinToString("[0-9]*"){Regex.escape(it)}+"$").matches(full)
    }
    fun tokens(text:String):List<String> {
        fun find(labels:String):List<String> = Regex("(?:شماره[ \\t]*)?(?:$labels)[ \\t]*[:：]?[ \\t]*([0-9*xX]+(?:[.\\-/ \\t]+[0-9*xX]+)*)")
            .findAll(text).filter { m ->
                val before=text.substring((m.range.first-20).coerceAtLeast(0),m.range.first)
                !Regex("(?:به|مقصد|ذینفع)[ \\t]*$").containsMatchIn(before)
            }.map { normalize(it.groupValues[1]) }.filter{it.count(Char::isDigit)>=4 && ('*' !in it || it.takeLastWhile(Char::isDigit).length>=4)}
            .map { if(labels=="کارت" && it.all(Char::isDigit) && it.length==4) "*$it" else it }
            .filter { labels!="کارت" || '*' in it || it.length==16 }.toList()
        val account=find("حساب|سپرده").take(1)
        val card=find("کارت").take(1)
        val source=if(account.isEmpty()) Regex("(?:^|\\n)از[ \\t]*:[ \\t]*([0-9]+(?:[.\\-/][0-9]+)*)").find(text)?.groupValues?.get(1)?.let{listOf(normalize(it))}.orEmpty() else emptyList()
        return (account+source+card).distinct()
    }
}
