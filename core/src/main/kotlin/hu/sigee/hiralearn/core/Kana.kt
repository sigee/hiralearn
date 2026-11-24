package hu.sigee.hiralearn.core

enum class KanaGroup(val title: String) {
    BASIC("Basic hiragana"),
    DAKUTEN("Dakuten & handakuten"),
    YOON("Combinations (yōon)"),
}

/**
 * One learnable item.
 *
 * @param id stable identifier used for persistence and (future) sync. Never change it once shipped.
 * @param char the hiragana as displayed.
 * @param romaji the Hepburn reading shown to the learner.
 * @param accepted every typed answer counted as correct (Hepburn plus Kunrei/Nihon-shiki variants).
 */
data class Kana(
    val id: String,
    val char: String,
    val romaji: String,
    val accepted: Set<String>,
    val lesson: Int,
    val group: KanaGroup,
) {
    /** Two kana are indistinguishable by sound when they share an accepted reading (じ/ぢ, ず/づ, お/を). */
    fun soundsLike(other: Kana): Boolean = accepted.any { it in other.accepted }
}

data class Lesson(val index: Int, val title: String, val group: KanaGroup, val kana: List<Kana>)

object Hiragana {

    private class Row(val title: String, val group: KanaGroup, val items: List<Triple<String, String, Set<String>>>)

    private fun row(title: String, group: KanaGroup, vararg items: String): Row =
        Row(title, group, items.map { spec ->
            // spec: "char:romaji[:alt1|alt2]" — the id is the romaji unless the alt list starts with "#id"
            val parts = spec.split(":")
            val alts = parts.getOrNull(2)?.split("|").orEmpty()
            Triple(parts[0], parts[1], alts.toSet())
        })

    private val rows: List<Row> = listOf(
        row("Vowels", KanaGroup.BASIC, "あ:a", "い:i", "う:u", "え:e", "お:o"),
        row("K-row", KanaGroup.BASIC, "か:ka", "き:ki", "く:ku", "け:ke", "こ:ko"),
        row("S-row", KanaGroup.BASIC, "さ:sa", "し:shi:si", "す:su", "せ:se", "そ:so"),
        row("T-row", KanaGroup.BASIC, "た:ta", "ち:chi:ti", "つ:tsu:tu", "て:te", "と:to"),
        row("N-row", KanaGroup.BASIC, "な:na", "に:ni", "ぬ:nu", "ね:ne", "の:no"),
        row("H-row", KanaGroup.BASIC, "は:ha", "ひ:hi", "ふ:fu:hu", "へ:he", "ほ:ho"),
        row("M-row", KanaGroup.BASIC, "ま:ma", "み:mi", "む:mu", "め:me", "も:mo"),
        row("Y-row", KanaGroup.BASIC, "や:ya", "ゆ:yu", "よ:yo"),
        row("R-row", KanaGroup.BASIC, "ら:ra", "り:ri", "る:ru", "れ:re", "ろ:ro"),
        row("W-row & N", KanaGroup.BASIC, "わ:wa", "を:wo:o", "ん:n:nn"),
        row("G-row", KanaGroup.DAKUTEN, "が:ga", "ぎ:gi", "ぐ:gu", "げ:ge", "ご:go"),
        row("Z-row", KanaGroup.DAKUTEN, "ざ:za", "じ:ji:zi", "ず:zu", "ぜ:ze", "ぞ:zo"),
        row("D-row", KanaGroup.DAKUTEN, "だ:da", "ぢ:ji:di|#di", "づ:zu:du|#du", "で:de", "ど:do"),
        row("B-row", KanaGroup.DAKUTEN, "ば:ba", "び:bi", "ぶ:bu", "べ:be", "ぼ:bo"),
        row("P-row", KanaGroup.DAKUTEN, "ぱ:pa", "ぴ:pi", "ぷ:pu", "ぺ:pe", "ぽ:po"),
        row("KY & SH", KanaGroup.YOON, "きゃ:kya", "きゅ:kyu", "きょ:kyo", "しゃ:sha:sya", "しゅ:shu:syu", "しょ:sho:syo"),
        row("CH & NY", KanaGroup.YOON, "ちゃ:cha:tya|cya", "ちゅ:chu:tyu|cyu", "ちょ:cho:tyo|cyo", "にゃ:nya", "にゅ:nyu", "にょ:nyo"),
        row("HY & MY", KanaGroup.YOON, "ひゃ:hya", "ひゅ:hyu", "ひょ:hyo", "みゃ:mya", "みゅ:myu", "みょ:myo"),
        row("RY & GY", KanaGroup.YOON, "りゃ:rya", "りゅ:ryu", "りょ:ryo", "ぎゃ:gya", "ぎゅ:gyu", "ぎょ:gyo"),
        row("J & BY", KanaGroup.YOON, "じゃ:ja:zya|jya", "じゅ:ju:zyu|jyu", "じょ:jo:zyo|jyo", "びゃ:bya", "びゅ:byu", "びょ:byo"),
        row("PY", KanaGroup.YOON, "ぴゃ:pya", "ぴゅ:pyu", "ぴょ:pyo"),
    )

    val lessons: List<Lesson> = rows.mapIndexed { index, row ->
        Lesson(
            index = index,
            title = row.title,
            group = row.group,
            kana = row.items.map { (char, romaji, rawAlts) ->
                val explicitId = rawAlts.firstOrNull { it.startsWith("#") }?.removePrefix("#")
                val alts = rawAlts.filterNot { it.startsWith("#") }.toSet()
                Kana(
                    id = explicitId ?: romaji,
                    char = char,
                    romaji = romaji,
                    accepted = alts + romaji,
                    lesson = index,
                    group = row.group,
                )
            },
        )
    }

    val all: List<Kana> = lessons.flatMap { it.kana }

    private val byId: Map<String, Kana> = all.associateBy { it.id }
    private val byChar: Map<String, Kana> = all.associateBy { it.char }

    init {
        check(byId.size == all.size) { "Duplicate kana id" }
    }

    fun byId(id: String): Kana? = byId[id]
    fun byChar(char: String): Kana? = byChar[char]

    /**
     * Groups of kana that learners commonly mix up because they look alike. Used to pick
     * tricky distractors, so the learner has to actually tell them apart.
     */
    private val lookAlikeGroups: List<String> = listOf(
        "あおめぬ", "ぬめね", "ねれわ", "るろ", "さちきら", "はほけ", "いこりに", "うつらろ",
        "くへし", "まもほ", "そて", "たなに", "むす", "われね", "しつ", "ゆよ", "かや", "けは",
    )

    private val dakutenBase = mapOf(
        "が" to "か", "ぎ" to "き", "ぐ" to "く", "げ" to "け", "ご" to "こ",
        "ざ" to "さ", "じ" to "し", "ず" to "す", "ぜ" to "せ", "ぞ" to "そ",
        "だ" to "た", "ぢ" to "ち", "づ" to "つ", "で" to "て", "ど" to "と",
        "ば" to "は", "び" to "ひ", "ぶ" to "ふ", "べ" to "へ", "ぼ" to "ほ",
        "ぱ" to "は", "ぴ" to "ひ", "ぷ" to "ふ", "ぺ" to "へ", "ぽ" to "ほ",
    )

    private val lookAlikes: Map<String, Set<String>> by lazy {
        val map = mutableMapOf<String, MutableSet<String>>()
        fun link(a: String, b: String) {
            if (a == b || byChar[a] == null || byChar[b] == null) return
            map.getOrPut(a) { mutableSetOf() } += b
            map.getOrPut(b) { mutableSetOf() } += a
        }
        lookAlikeGroups.forEach { group ->
            val chars = group.map { it.toString() }
            chars.forEach { a -> chars.forEach { b -> link(a, b) } }
        }
        // Voiced kana are confused with their unvoiced base, with each other (ば/ぱ), and
        // inherit the look-alikes of their base (ぼ/ば like ほ/は).
        dakutenBase.forEach { (voiced, base) -> link(voiced, base) }
        dakutenBase.entries.groupBy({ it.value }, { it.key }).values.forEach { family ->
            family.forEach { a -> family.forEach { b -> link(a, b) } }
        }
        dakutenBase.forEach { (voiced, base) ->
            map[base].orEmpty().toList().forEach { baseAlike ->
                dakutenBase.filter { it.value == baseAlike }.keys.forEach { link(voiced, it) }
            }
        }
        // Yōon: same initial with a different small kana, or same small kana with a look-alike initial.
        val yoon = all.filter { it.group == KanaGroup.YOON }
        yoon.forEach { a ->
            yoon.forEach { b ->
                val sameInitial = a.char[0] == b.char[0]
                val sameSmall = a.char[1] == b.char[1]
                val initialsAlike = map[a.char[0].toString()]?.contains(b.char[0].toString()) == true
                if (sameInitial || (sameSmall && initialsAlike)) link(a.char, b.char)
            }
        }
        map
    }

    /** Kana that look similar to [kana] (unordered). */
    fun lookAlikesOf(kana: Kana): List<Kana> =
        lookAlikes[kana.char].orEmpty().mapNotNull { byChar[it] }

    /** Other kana pronounced the same (じ/ぢ, ず/づ, お/を): hearing the sound alone can't tell them apart. */
    fun soundAlikesOf(kana: Kana): List<Kana> = all.filter { it.id != kana.id && it.soundsLike(kana) }
}
