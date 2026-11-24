package hu.sigee.hiralearn.ui.home

import hu.sigee.hiralearn.ui.lessonTitle
import hu.sigee.hiralearn.ui.groupTitle
import hu.sigee.hiralearn.ui.formatDuration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import hu.sigee.hiralearn.AppContainer
import hu.sigee.hiralearn.R
import hu.sigee.hiralearn.core.CardId
import hu.sigee.hiralearn.core.CardState
import hu.sigee.hiralearn.core.ConfusionPair
import hu.sigee.hiralearn.core.Hiragana
import hu.sigee.hiralearn.core.Kana
import hu.sigee.hiralearn.core.Lesson
import hu.sigee.hiralearn.core.Scheduler
import hu.sigee.hiralearn.core.Skill
import hu.sigee.hiralearn.core.StudySession
import hu.sigee.hiralearn.core.Word
import hu.sigee.hiralearn.core.WordSession
import hu.sigee.hiralearn.core.Words
import hu.sigee.hiralearn.ui.ink.HangingScroll
import hu.sigee.hiralearn.ui.ink.InkButton
import hu.sigee.hiralearn.ui.ink.InkTicks
import hu.sigee.hiralearn.ui.ink.Seal
import hu.sigee.hiralearn.ui.ink.inkWash
import hu.sigee.hiralearn.ui.ink.washiPaper
import hu.sigee.hiralearn.ui.theme.KanaFont
import hu.sigee.hiralearn.ui.theme.LocalInkColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlin.random.Random

data class KanaProgress(val kana: Kana, val level: Int?, val due: Boolean)

data class HomeUiState(
    val loading: Boolean = true,
    val dueCount: Int = 0,
    val nextDueAt: Long? = null,
    val now: Long = 0,
    val nextLesson: Lesson? = null,
    val newKana: List<Kana> = emptyList(),
    val learned: Int = 0,
    val total: Int = Hiragana.all.size,
    val lessons: List<Pair<Lesson, List<KanaProgress>>> = emptyList(),
    /** The kana on the hanging scroll: a due one, else the weakest learned one, else the next new one. */
    val featured: Kana? = null,
    /** How many words the learner can read, and a few of them (weakest kana first) for the words band. */
    val readableWords: Int = 0,
    val sampleWords: List<Word> = emptyList(),
    /** The pairs the learner mixes up most (see Confusions.rank); these are what "Drill these" asks. */
    val confusions: List<ConfusionPair> = emptyList(),
)

class HomeViewModel(container: AppContainer) : ViewModel() {
    private val clock = container.clock
    private val ticker = flow {
        while (true) {
            emit(clock())
            delay(30_000)
        }
    }

    val state: StateFlow<HomeUiState> = combine(
        container.progress.cards,
        ticker,
        container.questionTypes,
        container.progress.confusions(ticker),
    ) { cards, now, types, confusions ->
        // Skills that aren't asked (listening without a Japanese voice) neither count as due nor colour the kana.
        build(cards.filterKeys { it.skill in types.skills }, cards, now).copy(confusions = confusions)
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    private fun build(cards: Map<CardId, CardState>, allCards: Map<CardId, CardState>, now: Long): HomeUiState {
        val due = StudySession.dueCards(cards, now)
        val newKana = StudySession.nextNewKana(allCards)
        val weakest = cards.values.minWithOrNull(compareBy({ it.level }, { it.dueAt }))
        val featuredId = due.firstOrNull()?.id?.kanaId ?: weakest?.id?.kanaId
        return HomeUiState(
            loading = false,
            dueCount = due.size,
            nextDueAt = cards.values.minOfOrNull { it.dueAt },
            now = now,
            nextLesson = newKana.firstOrNull()?.let { Hiragana.lessons[it.lesson] },
            newKana = newKana,
            learned = Hiragana.all.count { StudySession.isLearned(it, allCards) },
            lessons = Hiragana.lessons.map { lesson ->
                lesson to lesson.kana.map { kana ->
                    val states = Skill.entries.mapNotNull { cards[CardId(kana.id, it)] }
                    KanaProgress(kana, states.minOfOrNull { it.level }, states.any { it.isDue(now) })
                }
            },
            featured = featuredId?.let { Hiragana.byId(it) } ?: newKana.firstOrNull(),
            readableWords = Words.readable(cards).size,
            // A fixed seed keeps the sample steady between ticks.
            sampleWords = WordSession.wordsToRead(cards, Random(cards.size)).take(SAMPLE_WORDS),
        )
    }

    companion object {
        private const val SAMPLE_WORDS = 3

        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { HomeViewModel(container) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onLearn: () -> Unit,
    onReview: () -> Unit,
    onPractice: () -> Unit,
    onReadWords: () -> Unit,
    onDrill: () -> Unit,
    onKana: (Kana) -> Unit,
    onKanaChart: () -> Unit,
    onSettings: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var menuOpen by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize().washiPaper()) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text("HiraLearn", fontWeight = FontWeight.SemiBold) },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                    actions = {
                        Box {
                            IconButton(onClick = { menuOpen = true }) {
                                Icon(painterResource(R.drawable.ic_more_vert), contentDescription = stringResource(R.string.menu))
                            }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                DropdownMenuItem(text = { Text(stringResource(R.string.kana_chart)) }, onClick = { menuOpen = false; onKanaChart() })
                                DropdownMenuItem(text = { Text(stringResource(R.string.settings)) }, onClick = { menuOpen = false; onSettings() })
                            }
                        }
                    },
                )
            },
        ) { padding ->
            if (state.loading) {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                return@Scaffold
            }
            LazyColumn(
                contentPadding = PaddingValues(
                    start = 20.dp,
                    end = 20.dp,
                    top = padding.calculateTopPadding(),
                    bottom = padding.calculateBottomPadding() + 24.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item { Progress(state) }
                state.featured?.let { kana ->
                    item {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            HangingScroll(kana.char, kana.romaji, onClick = { onKana(kana) })
                        }
                    }
                }
                item { ReviewBand(state, onReview, onPractice) }
                if (state.confusions.isNotEmpty()) item { ConfusionsBand(state.confusions, onDrill) }
                item { LearnBand(state, onLearn) }
                if (state.readableWords > 0) item { WordsBand(state, onReadWords) }
            }
        }
    }
}

@Composable
private fun Progress(state: HomeUiState) {
    Column {
        Text(
            stringResource(R.string.inked_count, state.learned, state.total),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        InkTicks(state.learned, state.total)
    }
}

@Composable
private fun ReviewBand(state: HomeUiState, onReview: () -> Unit, onPractice: () -> Unit) {
    val ink = LocalInkColors.current
    val caughtUp = state.dueCount == 0
    // Matcha green when nothing is due (everything is fine), indigo when reviews are waiting.
    Box(Modifier.fillMaxWidth().inkWash(if (caughtUp) ink.matchaWash else ink.indigoWash, seed = 3)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 20.dp)) {
            if (!caughtUp) {
                Text(stringResource(R.string.to_review, state.dueCount), style = MaterialTheme.typography.headlineSmall)
                Text(stringResource(R.string.review_hint), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                InkButton(stringResource(R.string.review_now), onReview, seed = 4)
            } else {
                Text(stringResource(R.string.all_caught_up), style = MaterialTheme.typography.headlineSmall)
                val next = state.nextDueAt
                Text(
                    if (next == null) stringResource(R.string.first_kana_hint)
                    else stringResource(R.string.next_review_in, formatDuration(next - state.now)),
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (state.learned > 0) {
                    Spacer(Modifier.height(12.dp))
                    InkButton(stringResource(R.string.practice_anyway), onPractice, seed = 6)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        stringResource(R.string.practice_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (caughtUp && state.learned > 0) Seal(Modifier.align(Alignment.TopEnd).padding(top = 14.dp, end = 16.dp), size = 44.dp)
    }
}

@Composable
private fun LearnBand(state: HomeUiState, onLearn: () -> Unit) {
    val ink = LocalInkColors.current
    Box(Modifier.fillMaxWidth().inkWash(ink.inkWash, seed = 8)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 20.dp)) {
            val lesson = state.nextLesson
            if (lesson == null) {
                Text(stringResource(R.string.all_inked), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.keep_reviewing), style = MaterialTheme.typography.bodyMedium)
            } else {
                Text(stringResource(R.string.next_lesson, lessonTitle(lesson)), style = MaterialTheme.typography.titleLarge)
                Text(groupTitle(lesson.group), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                // New kana are still faint outlines, waiting for ink.
                Text(
                    state.newKana.joinToString("  ") { it.char },
                    fontFamily = KanaFont,
                    fontSize = 34.sp,
                    color = ink.ink.copy(alpha = 0.28f),
                )
                Spacer(Modifier.height(10.dp))
                InkButton(stringResource(R.string.learn_n_new, state.newKana.size), onLearn, seed = 2)
            }
        }
    }
}

/** Real words to read, once a lesson is done. */
@Composable
private fun WordsBand(state: HomeUiState, onReadWords: () -> Unit) {
    val ink = LocalInkColors.current
    Box(Modifier.fillMaxWidth().inkWash(ink.inkWash, seed = 12)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 20.dp)) {
            Text(stringResource(R.string.words_title), style = MaterialTheme.typography.titleLarge)
            Text(
                pluralStringResource(R.plurals.words_available, state.readableWords, state.readableWords),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            Text(state.sampleWords.joinToString("  ") { it.text }, fontFamily = KanaFont, fontSize = 30.sp, color = ink.ink)
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.words_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(10.dp))
            InkButton(stringResource(R.string.read_now), onReadWords, seed = 10)
        }
    }
}

/** The pairs the learner confuses most, each as its own small ink wash, and a button to drill them. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ConfusionsBand(pairs: List<ConfusionPair>, onDrill: () -> Unit) {
    val ink = LocalInkColors.current
    Box(Modifier.fillMaxWidth().inkWash(ink.inkWash, seed = 12)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 20.dp)) {
            Text(stringResource(R.string.confusions_title), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.confusions_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(10.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                pairs.forEachIndexed { i, pair -> ConfusionPairWash(pair, seed = 40 + i * 7) }
            }
            Spacer(Modifier.height(12.dp))
            InkButton(stringResource(R.string.drill_these), onDrill, seed = 9)
        }
    }
}

@Composable
private fun ConfusionPairWash(pair: ConfusionPair, seed: Int) {
    val ink = LocalInkColors.current
    Column(
        Modifier.inkWash(ink.inkWash, seed = seed).padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(pair.first.char, fontFamily = KanaFont, fontSize = 30.sp, color = ink.ink)
            Text(
                stringResource(R.string.versus),
                style = MaterialTheme.typography.labelSmall,
                color = ink.pencil,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
            Text(pair.second.char, fontFamily = KanaFont, fontSize = 30.sp, color = ink.ink)
        }
        Text(
            pluralStringResource(R.plurals.mixed_up_count, pair.count, pair.count),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
