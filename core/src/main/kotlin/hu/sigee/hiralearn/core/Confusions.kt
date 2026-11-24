package hu.sigee.hiralearn.core

import kotlin.math.pow

/** One logged mix-up: the learner was asked [kanaId] and answered as if it were [confusedWithId]. */
data class MixUp(val kanaId: String, val confusedWithId: String, val at: Long)

/** A right answer in a [SessionMode.DRILL] session. */
data class DrillAnswer(val kanaId: String, val skill: Skill, val at: Long)

/**
 * Two kana the learner mixes up, in either direction. [first] comes before [second] in the catalogue, so
 * あ↔お and お↔あ are the same pair. [score] is the recency-weighted count used for ranking.
 */
data class ConfusionPair(val first: Kana, val second: Kana, val count: Int, val lastAt: Long, val score: Double) {
    val kana: List<Kana> get() = listOf(first, second)
}

object Confusions {
    /** A mix-up counts half as much after two weeks, so pairs the learner has since sorted out fade away. */
    const val HALF_LIFE: Long = 14 * Scheduler.DAY

    /** Below this a pair is no longer shown (one mix-up stays for four weeks). */
    const val MIN_SCORE = 0.25

    /** The skills a drill asks; a pair is cleared when both kana were answered right in each of them. */
    val DRILLED_SKILLS: Set<Skill> = setOf(Skill.READING, Skill.WRITING)

    /** How many pairs are shown and drilled. */
    const val TOP_PAIRS = 5

    /**
     * The pairs the learner confuses most, strongest first (ties: the more recent first). Pairs that share a
     * reading (じ/ぢ) are left out: a contrast question about them would have no single right answer.
     *
     * A pair is cleared once, after its last mix-up, both kana were answered right in a drill for both reading
     * and writing (what one clean drill of the pair does). Only mix-ups after that count if it comes back.
     */
    fun rank(mixUps: List<MixUp>, now: Long, drillAnswers: List<DrillAnswer> = emptyList(), limit: Int = TOP_PAIRS): List<ConfusionPair> {
        val order = Hiragana.all.withIndex().associate { (i, k) -> k.id to i }
        val drilledByKana = drillAnswers.filter { it.skill in DRILLED_SKILLS }.groupBy { it.kanaId }
        return mixUps
            .mapNotNull { m ->
                val a = Hiragana.byId(m.kanaId) ?: return@mapNotNull null
                val b = Hiragana.byId(m.confusedWithId) ?: return@mapNotNull null
                if (a.id == b.id || a.soundsLike(b)) return@mapNotNull null
                val pair = if (order.getValue(a.id) < order.getValue(b.id)) a to b else b to a
                pair to m.at
            }
            .groupBy({ it.first }, { it.second })
            .mapNotNull { (pair, times) ->
                val drilled = drilledByKana[pair.first.id].orEmpty() + drilledByKana[pair.second.id].orEmpty()
                val active = activeMixUps(times, drilled)
                if (active.isEmpty()) return@mapNotNull null
                val score = active.sumOf { 0.5.pow((now - it).coerceAtLeast(0).toDouble() / HALF_LIFE) }
                ConfusionPair(pair.first, pair.second, active.size, active.max(), score)
            }
            .filter { it.score >= MIN_SCORE }
            .sortedWith(compareByDescending<ConfusionPair> { it.score }.thenByDescending { it.lastAt })
            .take(limit)
    }

    /** The mix-up times of one pair that are still open: those after the last time the pair was drilled clean. */
    private fun activeMixUps(times: List<Long>, drilled: List<DrillAnswer>): List<Long> {
        // [drilled] only holds drilled-skill answers about the pair's two kana, so a full drill covers 2 × 2 entries.
        val needed = 2 * DRILLED_SKILLS.size
        val active = mutableListOf<Long>()
        val covered = mutableSetOf<Pair<String, Skill>>()
        // Chronological merge; at equal times the mix-up comes last, so it is never cleared by a simultaneous answer.
        val timeline = times.map { it to null } + drilled.map { it.at to it }
        for ((at, answer) in timeline.sortedWith(compareBy({ it.first }, { it.second == null }))) {
            if (answer == null) {
                active += at
                covered.clear()
            } else if (active.isNotEmpty()) {
                covered += answer.kanaId to answer.skill
                if (covered.size == needed) active.clear()
            }
        }
        return active
    }
}
