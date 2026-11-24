package hu.sigee.hiralearn.core

import hu.sigee.hiralearn.core.Fixtures.kana
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Whole-catalogue invariants: these guard the permanent ids and the data the scheduler and quiz rely on. */
class CatalogInvariantsTest {

    @Test
    fun idsAndCharsAreUnique() {
        assertEquals(Hiragana.all.size, Hiragana.all.map { it.id }.toSet().size)
        assertEquals(Hiragana.all.size, Hiragana.all.map { it.char }.toSet().size)
        assertEquals(104, Hiragana.all.size)
    }

    /**
     * Ids are database and sync keys and must never change. This pins every shipped id in catalogue order;
     * a new kana must be appended to a lesson with a new id, never by renaming one of these.
     */
    @Test
    fun shippedIdsNeverChange() {
        val expected = (
            "a i u e o ka ki ku ke ko sa shi su se so ta chi tsu te to na ni nu ne no ha hi fu he ho " +
                "ma mi mu me mo ya yu yo ra ri ru re ro wa wo n " +
                "ga gi gu ge go za ji zu ze zo da di du de do ba bi bu be bo pa pi pu pe po " +
                "kya kyu kyo sha shu sho cha chu cho nya nyu nyo hya hyu hyo mya myu myo " +
                "rya ryu ryo gya gyu gyo ja ju jo bya byu byo pya pyu pyo"
            ).split(" ")
        assertEquals(expected, Hiragana.all.map { it.id })
    }

    @Test
    fun lookupsReturnTheSameObjects() {
        Hiragana.all.forEach {
            assertSame(it, Hiragana.byId(it.id))
            assertSame(it, Hiragana.byChar(it.char))
        }
        assertNull(Hiragana.byId("xx"))
        assertNull(Hiragana.byChar("ア"), "katakana is not in the catalogue")
    }

    @Test
    fun everyKanaAcceptsItsOwnRomajiAndOnlyLowercaseAscii() {
        Hiragana.all.forEach { k ->
            assertTrue(k.romaji in k.accepted, k.char)
            k.accepted.forEach { assertTrue(it.matches(Regex("[a-z]+")), "${k.char}: '$it'") }
            assertFalse(k.accepted.any { it.startsWith("#") }, "id markers must not leak into answers")
        }
    }

    @Test
    fun alternativeSpellingsAreAccepted() {
        assertEquals(setOf("shi", "si"), kana("し").accepted)
        assertEquals(setOf("tsu", "tu"), kana("つ").accepted)
        assertEquals(setOf("fu", "hu"), kana("ふ").accepted)
        assertEquals(setOf("n", "nn"), kana("ん").accepted)
        assertEquals(setOf("cha", "tya", "cya"), kana("ちゃ").accepted)
        assertEquals(setOf("ji", "di"), kana("ぢ").accepted)
        assertEquals(setOf("zu", "du"), kana("づ").accepted)
        assertEquals(setOf("a"), kana("あ").accepted)
    }

    @Test
    fun explicitIdsKeepHepburnRomaji() {
        val di = kana("ぢ")
        assertEquals("di", di.id)
        assertEquals("ji", di.romaji)
        val du = kana("づ")
        assertEquals("du", du.id)
        assertEquals("zu", du.romaji)
    }

    @Test
    fun soundsLikeIsSymmetricAndOnlyForSharedReadings() {
        val pairs = mutableSetOf<Set<String>>()
        Hiragana.all.forEach { a ->
            assertTrue(a.soundsLike(a))
            Hiragana.all.forEach { b ->
                assertEquals(a.soundsLike(b), b.soundsLike(a), "${a.char}/${b.char}")
                if (a != b && a.soundsLike(b)) pairs += setOf(a.char, b.char)
            }
        }
        assertEquals(setOf(setOf("じ", "ぢ"), setOf("ず", "づ"), setOf("お", "を")), pairs)
    }

    @Test
    fun lessonsAreNumberedInOrderAndKanaKnowTheirLesson() {
        Hiragana.lessons.forEachIndexed { i, lesson ->
            assertEquals(i, lesson.index)
            assertTrue(lesson.kana.isNotEmpty())
            lesson.kana.forEach {
                assertEquals(i, it.lesson)
                assertEquals(lesson.group, it.group)
            }
        }
        assertEquals(Hiragana.lessons.flatMap { it.kana }, Hiragana.all)
        assertEquals("Vowels", Hiragana.lessons.first().title)
        assertEquals(listOf("あ", "い", "う", "え", "お"), Hiragana.lessons.first().kana.map { it.char })
        // Groups come in teaching order: all basic, then dakuten, then yōon.
        assertEquals(KanaGroup.entries, Hiragana.lessons.map { it.group }.distinct())
    }

    @Test
    fun yoonAreTwoCharactersAndOthersOne() {
        Hiragana.all.forEach {
            assertEquals(if (it.group == KanaGroup.YOON) 2 else 1, it.char.length, it.char)
        }
    }

    @Test
    fun lookAlikesAreSymmetricIrreflexiveAndDistinct() {
        Hiragana.all.forEach { a ->
            val alikes = Hiragana.lookAlikesOf(a)
            assertFalse(a in alikes, "${a.char} is not its own look-alike")
            assertEquals(alikes.size, alikes.toSet().size)
            alikes.forEach { b -> assertTrue(a in Hiragana.lookAlikesOf(b), "${a.char} ~ ${b.char} but not back") }
        }
    }

    @Test
    fun lookAlikeRulesCoverHandListsDakutenAndYoon() {
        fun alike(a: String, b: String) = kana(b) in Hiragana.lookAlikesOf(kana(a))
        // Hand-written groups.
        assertTrue(alike("ぬ", "め"))
        assertTrue(alike("る", "ろ"))
        assertTrue(alike("さ", "ち"))
        // Voiced ↔ base, voiced ↔ half-voiced, voiced inherits the base's look-alikes.
        assertTrue(alike("が", "か"))
        assertTrue(alike("ぱ", "は"))
        assertTrue(alike("ば", "ぱ"))
        assertTrue(alike("ぼ", "ば"), "ほ~は, so ぼ~ば")
        assertTrue(alike("ぼ", "ぽ"))
        // Yōon: same initial, or same small kana with look-alike initials.
        assertTrue(alike("きゃ", "きゅ"))
        assertTrue(alike("きゃ", "ぎゃ"))
        assertFalse(alike("きゃ", "ぎゅ"))
        assertFalse(alike("きゃ", "にゃ"))
        // Unrelated kana stay apart.
        assertFalse(alike("あ", "ん"))
        assertFalse(alike("か", "ぱ"))
    }

    @Test
    fun groupTitlesAreSet() {
        assertEquals(listOf("Basic hiragana", "Dakuten & handakuten", "Combinations (yōon)"), KanaGroup.entries.map { it.title })
    }
}
