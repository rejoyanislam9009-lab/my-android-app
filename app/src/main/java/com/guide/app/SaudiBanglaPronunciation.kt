package com.guide.app

/**
 * Bengali-script pronunciation helper for the bundled Saudi Arabic course.
 * It is deliberately offline and deterministic. Exact high-frequency forms are
 * overridden first; the fallback transliterator keeps the existing Latin guide
 * readable while converting common Arabic sounds into Bengali script.
 */
object SaudiBanglaPronunciation {
    private val exact = mapOf(
        "as-salaamu alaykum" to "আস-সালামু আলাইকুম",
        "wa alaykum as-salaam" to "ওয়া আলাইকুম আস-সালাম",
        "hala" to "হালা",
        "hala wallah" to "হালা ওয়াল্লাহ",
        "sabaah al-khair" to "সাবাহ আল-খাইর",
        "masa al-khair" to "মাসা আল-খাইর",
        "shlonak?" to "শ্লোনাক?",
        "kaif haalak?" to "কাইফ হালাক?",
        "alhamdulillah" to "আলহামদুলিল্লাহ",
        "shukran" to "শুকরান",
        "afwan" to "আফওয়ান",
        "law samaht" to "লাও সামাহত",
        "maalesh" to "মা'লেশ",
        "khalas" to "খালাস",
        "ma fi mushkilah" to "মা ফি মুশকিলা",
        "wain?" to "ওয়াইন?",
        "wesh?" to "ওয়েশ?",
        "alheen" to "আলহীন",
        "abi" to "আবি",
        "abgha" to "আবগা",
        "abshir" to "আবশির",
        "moyyah" to "মোইয়্যা",
        "jawwaal" to "জাওয়াল",
        "ad-dawaam" to "আদ-দাওয়াম",
        "mata ad-dawaam?" to "মাতা আদ-দাওয়াম?",
        "kam as-saa'ah?" to "কাম আস-সা'আহ?",
        "kam al-hisaab?" to "কাম আল-হিসাব?",
        "kam al-ejaar?" to "কাম আল-ইজার?",
        "ana dafa't" to "আনা দাফা'ত",
        "ana ta'baan" to "আনা তা'বান",
        "mumkin tusaa'idni?" to "মুমকিন তুসা'ইদনি?",
        "wain as-saydaliyyah?" to "ওয়াইন আস-সাইদালিয়্যা?",
        "wain al-mataar?" to "ওয়াইন আল-মাতার?",
        "wain mahattat al-banzeen?" to "ওয়াইন মাহাত্তাত আল-বানজিন?",
        "bidoon haar" to "বিদুন হার",
        "shway haar" to "শুয়াই হার",
        "al-hisaab law samaht" to "আল-হিসাব লাও সামাহত",
        "qahwah arabiyyah" to "কাহওয়া আরাবিয়্যা",
        "bukrah" to "বুকরা",
        "al-yawm" to "আল-ইয়াওম",
        "ams" to "আমস"
    )

    private val wordMap = mapOf(
        "ana" to "আনা", "inta" to "ইন্তা", "inti" to "ইন্তি", "huwa" to "হুয়া", "hiya" to "হিয়া",
        "nahnu" to "নাহনু", "ihna" to "ইহনা", "min" to "মিন", "meen" to "মিন", "fi" to "ফি", "ma" to "মা",
        "wain" to "ওয়াইন", "wein" to "ওয়াইন", "wesh" to "ওয়েশ", "esh" to "এশ", "leish" to "লেইশ", "keif" to "কেইফ", "kaif" to "কাইফ",
        "kam" to "কাম", "mata" to "মাতা", "alheen" to "আলহীন", "heen" to "হীন", "bukrah" to "বুকরা", "ams" to "আমস",
        "yawm" to "ইয়াওম", "saa'ah" to "সা'আহ", "daqiiqah" to "দাকিকা", "daqaa'iq" to "দাকায়িক",
        "abi" to "আবি", "abgha" to "আবগা", "abghaa" to "আবগা", "areed" to "আরিদ", "ahtaaj" to "আহতাজ",
        "mumkin" to "মুমকিন", "la" to "লা", "naam" to "না'আম", "aywah" to "আইওয়া", "tayyib" to "তাইয়্যিব",
        "khalas" to "খালাস", "abshir" to "আবশির", "shukran" to "শুকরান", "afwan" to "আফওয়ান", "samaht" to "সামাহত",
        "wallah" to "ওয়াল্লাহ", "hala" to "হালা", "marhaba" to "মারহাবা", "salaam" to "সালাম", "alaykum" to "আলাইকুম",
        "dawaam" to "দাওয়াম", "shughl" to "শুগল", "mudeer" to "মুদির", "raais" to "রাইস", "mushrif" to "মুশরিফ",
        "raatih" to "রাতিব", "raawatib" to "রাওয়াতিব", "ijazah" to "ইজাজা", "kafeel" to "কাফিল", "iqamah" to "ইকামা",
        "hisaab" to "হিসাব", "fuloos" to "ফুলুস", "mablagh" to "মাবলাগ", "baaqi" to "বাকি", "dafa't" to "দাফা'ত", "adfa'" to "আদফা'",
        "ejaar" to "ইজার", "faatoorah" to "ফাতুরা", "kahrabaa" to "কাহরাবা", "internet" to "ইন্টারনেট", "moyyah" to "মোইয়্যা",
        "sooq" to "সুক", "mahal" to "মাহাল", "ghali" to "গালি", "rakhis" to "রাখিস", "takhfeed" to "তাখফিদ", "shabakah" to "শাবাকা",
        "cash" to "ক্যাশ", "bitaa'qah" to "বিতাকা", "jawwaal" to "জাওয়াল", "shariihah" to "শারিহা",
        "sayyaarah" to "সাইয়্যারা", "taxi" to "ট্যাক্সি", "mataar" to "মাতার", "mahattah" to "মাহাত্তা", "banzeen" to "বানজিন",
        "yameen" to "ইয়ামিন", "yasaar" to "ইয়াসার", "seedah" to "সিদা", "waqif" to "ওয়াকিফ", "nanzil" to "নানজিল",
        "ruz" to "রুজ", "dajaaj" to "দাজাজ", "laham" to "লাহাম", "samak" to "সামাক", "khubz" to "খুবজ", "haar" to "হার",
        "sukkar" to "সুক্কার", "shaay" to "শাই", "qahwah" to "কাহওয়া", "safari" to "সাফারি", "mahalli" to "মাহাল্লি",
        "mustashfa" to "মুস্তাশফা", "saydaliyyah" to "সাইদালিয়্যা", "is'aaf" to "ইস'আফ", "shurtah" to "শুরতা",
        "alam" to "আলাম", "ta'baan" to "তা'বান", "mariidh" to "মারিদ", "musaadah" to "মুসা'আদা",
        "waahid" to "ওয়াহিদ", "ithnain" to "ইথনাইন", "thalaathah" to "থালাথা", "arba'ah" to "আরবা'আ", "khamsah" to "খামসা",
        "sittah" to "সিত্তা", "sab'ah" to "সাব'আ", "thamaaniyah" to "থামানিয়া", "tis'ah" to "তিস'আ", "asharah" to "আশারা",
        "khamseen" to "খামসিন", "miyah" to "মিয়া", "alf" to "আলফ"
    )

    fun fromLatin(value: String): String {
        val clean = value.trim()
        exact[clean.lowercase()]?.let { return it }
        if (clean.isBlank()) return clean
        return clean.split(Regex("(\\s+)")).joinToString("") { token ->
            if (token.isBlank()) token else transliterateToken(token)
        }
    }

    private fun transliterateToken(raw: String): String {
        val punctuation = raw.takeLastWhile { !it.isLetterOrDigit() && it != '\'' }
        val core = raw.dropLast(punctuation.length)
        val key = core.lowercase().trim('-', '–', '—')
        wordMap[key]?.let { return it + punctuation }

        var s = key
        val prefix = when {
            s.startsWith("al-") -> { s = s.removePrefix("al-"); "আল-" }
            s.startsWith("as-") -> { s = s.removePrefix("as-"); "আস-" }
            s.startsWith("ad-") -> { s = s.removePrefix("ad-"); "আদ-" }
            s.startsWith("ar-") -> { s = s.removePrefix("ar-"); "আর-" }
            s.startsWith("ash-") -> { s = s.removePrefix("ash-"); "আশ-" }
            else -> ""
        }
        val pairs = listOf(
            "kh" to "খ", "gh" to "গ", "sh" to "শ", "ch" to "চ", "th" to "থ", "dh" to "ধ",
            "aa" to "া", "ee" to "ী", "ii" to "ী", "oo" to "ু", "ou" to "উ", "ai" to "াই", "ay" to "াই"
        )
        pairs.forEach { (a, b) -> s = s.replace(a, b) }
        val single = mapOf(
            'a' to "া", 'b' to "ব", 't' to "ত", 'j' to "জ", 'h' to "হ", 'd' to "দ", 'r' to "র", 'z' to "জ",
            's' to "স", 'f' to "ফ", 'q' to "ক", 'k' to "ক", 'l' to "ল", 'm' to "ম", 'n' to "ন", 'w' to "ও",
            'y' to "য়", 'i' to "ি", 'u' to "ু", 'e' to "ে", 'o' to "ো", 'g' to "গ", 'p' to "প", 'v' to "ভ",
            '\'' to "'"
        )
        val out = buildString {
            s.forEach { ch -> append(single[ch] ?: ch) }
        }.replace("াা", "া").replace("িি", "ি").replace("ুু", "ু")
        val normalized = if (out.startsWith("া")) "আ" + out.drop(1) else out
        return prefix + normalized + punctuation
    }
}
