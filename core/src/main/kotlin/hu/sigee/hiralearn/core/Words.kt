package hu.sigee.hiralearn.core

/**
 * A real Japanese word written only with kana from the catalogue, read once all of its kana are known.
 *
 * @param id stable identifier (persistence, translations of the meaning, future sync). Never change it once shipped.
 * @param text the word in hiragana.
 * @param kana [text] split into catalogue kana; a yōon (きゃ) is one unit, like its card.
 */
data class Word(val id: String, val text: String, val kana: List<Kana>) {
    /** Hepburn reading for display, with n' where ん would otherwise merge with the next kana (ほんや → hon'ya). */
    val romaji: String get() = romajiOf(kana)

    /** True if [typed] is a reading of the whole word, allowing every accepted spelling of each kana (si, tu, nn, …). */
    fun isReadAs(typed: String): Boolean {
        val text = StudySession.normalizeTyped(typed)
        return text.length in forwardEnds(text, kana.size).last()
    }

    /**
     * Finds which kana the learner misread in a wrong [typed] answer: the reading of exactly one kana was replaced
     * by the reading of another kana, the rest is right (ねこ typed "reko": ね read as れ). Kana the learner knows
     * ([known]) and look-alikes are preferred when several explanations fit. Returns null if no single swap explains it.
     */
    fun misreadIn(typed: String, known: Collection<Kana>): Misread? {
        val text = StudySession.normalizeTyped(typed)
        if (isReadAs(text)) return null
        val forward = forwardEnds(text, kana.size)
        val backward = backwardStarts(text)
        val knownIds = known.map { it.id }.toSet()
        var best: Misread? = null
        var bestRank = Int.MAX_VALUE
        kana.forEachIndexed { i, target ->
            val alike = Hiragana.lookAlikesOf(target).map { it.id }.toSet()
            for (start in forward[i]) for (end in backward[i + 1]) {
                if (end <= start) continue
                val segment = text.substring(start, end)
                for (other in Hiragana.all) {
                    if (other.id == target.id || segment !in other.accepted) continue
                    val rank = (if (other.id in knownIds) 0 else 2) + (if (other.id in alike) 0 else 1)
                    if (rank < bestRank) {
                        best = Misread(i, other)
                        bestRank = rank
                    }
                }
            }
        }
        return best
    }

    /** forward[i] = every end position in [text] reachable by reading the first i kana. */
    private fun forwardEnds(text: String, count: Int): List<Set<Int>> {
        val ends = mutableListOf(setOf(0))
        for (i in 0 until count) {
            ends += ends[i].flatMap { start -> kana[i].accepted.filter { text.startsWith(it, start) }.map { start + it.length } }.toSet()
        }
        return ends
    }

    /** backward[i] = every start position in [text] from which kana i..end read the rest of [text] exactly. */
    private fun backwardStarts(text: String): List<Set<Int>> {
        val starts = MutableList(kana.size + 1) { emptySet<Int>() }
        starts[kana.size] = setOf(text.length)
        for (i in kana.lastIndex downTo 0) {
            starts[i] = starts[i + 1].flatMap { end ->
                kana[i].accepted.filter { text.startsWith(it, end - it.length) }.map { end - it.length }
            }.toSet()
        }
        return starts
    }

    companion object {
        fun romajiOf(kana: List<Kana>): String = buildString {
            kana.forEachIndexed { i, k ->
                append(k.romaji)
                val next = kana.getOrNull(i + 1)?.romaji?.first()
                if (k.id == "n" && next != null && (next in "aiueo" || next == 'y')) append('\'')
            }
        }
    }
}

/** In a word, the kana at [position] was read as [readAs]. */
data class Misread(val position: Int, val readAs: Kana)

object Words {
    /**
     * id:text. Ordered roughly by the lesson that unlocks them. Words use only catalogue kana (no small っ, no ー),
     * so every word can be read with what the lessons teach. Meanings are translated in the app by id.
     */
    private val specs = listOf(
        // Vowels
        "ai:あい", "ie:いえ", "ue:うえ", "ao:あお", "iie:いいえ",
        // K
        "aka:あか", "aki:あき", "eki:えき", "kao:かお", "koe:こえ", "ike:いけ", "koko:ここ", "kuuki:くうき", "kikai:きかい", "oka:おか",
        // S
        "sushi:すし", "asa:あさ", "isu:いす", "ushi:うし", "shio:しお", "kasa:かさ", "ashi:あし", "sekai:せかい", "okashi:おかし",
        "suika:すいか", "soko:そこ",
        // T
        "tako:たこ", "kutsu:くつ", "tsukue:つくえ", "kuchi:くち", "tsuki:つき", "soto:そと", "tokei:とけい", "taiko:たいこ",
        "chikatetsu:ちかてつ", "ichi:いち", "kita:きた",
        // N
        "neko:ねこ", "inu:いぬ", "sakana:さかな", "natsu:なつ", "niku:にく", "nani:なに", "naka:なか", "okane:おかね", "anata:あなた",
        "kinoko:きのこ", "kuni:くに",
        // H
        "hana:はな", "hito:ひと", "fune:ふね", "hoshi:ほし", "hashi:はし", "hikouki:ひこうき", "fuku:ふく", "hako:はこ", "hone:ほね",
        "heso:へそ",
        // M
        "mimi:みみ", "mushi:むし", "momo:もも", "atama:あたま", "umi:うみ", "kuma:くま", "namae:なまえ", "ame:あめ", "mise:みせ",
        "musume:むすめ", "kami:かみ",
        // Y
        "yama:やま", "yuki:ゆき", "fuyu:ふゆ", "heya:へや", "yasai:やさい", "yume:ゆめ", "yasumi:やすみ",
        // R
        "sakura:さくら", "kuruma:くるま", "tori:とり", "yoru:よる", "haru:はる", "sora:そら", "iro:いろ", "kusuri:くすり", "furo:ふろ",
        "risu:りす", "rekishi:れきし",
        // W & N
        "watashi:わたし", "kawa:かわ", "niwa:にわ", "wani:わに", "hon:ほん", "mikan:みかん", "tenki:てんき", "sensei:せんせい",
        "nihon:にほん", "honya:ほんや",
        // G
        "kagi:かぎ", "gohan:ごはん", "megane:めがね", "tamago:たまご", "ringo:りんご", "eigo:えいご", "hiragana:ひらがな",
        "usagi:うさぎ", "ginkou:ぎんこう", "kagami:かがみ",
        // Z
        "kaze:かぜ", "mizu:みず", "chizu:ちず", "zou:ぞう", "jikan:じかん", "shizuka:しずか", "naze:なぜ",
        // D (ぢ and づ get their own ids, like their cards)
        "mado:まど", "karada:からだ", "denwa:でんわ", "tomodachi:ともだち", "doko:どこ", "daigaku:だいがく", "hanadi:はなぢ",
        "mikaduki:みかづき",
        // B
        "kaban:かばん", "yubi:ゆび", "buta:ぶた", "kabe:かべ", "boushi:ぼうし", "ebi:えび", "soba:そば", "tabemono:たべもの",
        // P
        "tenpura:てんぷら", "sanpo:さんぽ", "enpitsu:えんぴつ", "kanpai:かんぱい", "shinpai:しんぱい", "pikapika:ぴかぴか",
        // KY & SH
        "kyou:きょう", "shashin:しゃしん", "isha:いしゃ", "kyaku:きゃく", "shukudai:しゅくだい", "densha:でんしゃ", "kaisha:かいしゃ",
        "shouyu:しょうゆ", "toshokan:としょかん", "kyuuri:きゅうり",
        // CH & NY
        "ocha:おちゃ", "chawan:ちゃわん", "chuui:ちゅうい", "choucho:ちょうちょ", "chuugoku:ちゅうごく", "konnyaku:こんにゃく",
        // HY & MY
        "hyaku:ひゃく", "myouji:みょうじ", "hyoutan:ひょうたん", "myaku:みゃく",
        // RY & GY
        "ryouri:りょうり", "ryuu:りゅう", "gyuunyuu:ぎゅうにゅう", "ryokou:りょこう", "gyouza:ぎょうざ", "kingyo:きんぎょ",
        // J & BY
        "juu:じゅう", "jouzu:じょうず", "byouin:びょういん", "jisho:じしょ", "sanbyaku:さんびゃく",
        // PY
        "pyonpyon:ぴょんぴょん",
    )

    val all: List<Word> = specs.map { spec ->
        val (id, text) = spec.split(":")
        Word(id, text, split(text) ?: error("Word $id uses kana outside the catalogue"))
    }

    private val byId: Map<String, Word> = all.associateBy { it.id }

    init {
        check(byId.size == all.size) { "Duplicate word id" }
    }

    fun byId(id: String): Word? = byId[id]

    /** Splits [text] into catalogue kana, taking a yōon (two characters) before a single kana. Null if it can't. */
    fun split(text: String): List<Kana>? {
        val out = mutableListOf<Kana>()
        var i = 0
        while (i < text.length) {
            val kana = Hiragana.byChar(text.substring(i, minOf(i + 2, text.length))) ?: Hiragana.byChar(text.substring(i, i + 1))
            out += kana ?: return null
            i += kana.char.length
        }
        return out
    }

    /** The learner can read a word once every kana in it has a reading card. */
    fun isReadable(word: Word, states: Map<CardId, CardState>): Boolean =
        word.kana.all { states.containsKey(CardId(it.id, Skill.READING)) }

    fun readable(states: Map<CardId, CardState>): List<Word> = all.filter { isReadable(it, states) }

    /** Words that became readable because the learner just learned [newKana]: the reward for finishing a lesson. */
    fun unlockedBy(newKana: Collection<Kana>, states: Map<CardId, CardState>): List<Word> {
        val ids = newKana.map { it.id }.toSet()
        return readable(states).filter { word -> word.kana.any { it.id in ids } }
    }
}
