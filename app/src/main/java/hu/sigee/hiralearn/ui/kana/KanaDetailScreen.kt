package hu.sigee.hiralearn.ui.kana

import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import hu.sigee.hiralearn.R
import hu.sigee.hiralearn.appContainer
import hu.sigee.hiralearn.core.CardId
import hu.sigee.hiralearn.core.CardState
import hu.sigee.hiralearn.core.DrawingProblem
import hu.sigee.hiralearn.core.DrawingResult
import hu.sigee.hiralearn.core.HandwritingGrader
import hu.sigee.hiralearn.core.Hiragana
import hu.sigee.hiralearn.core.Scheduler
import hu.sigee.hiralearn.core.Skill
import hu.sigee.hiralearn.core.Stroke
import hu.sigee.hiralearn.ui.components.DrawingPad
import hu.sigee.hiralearn.ui.components.SoundButton
import hu.sigee.hiralearn.ui.components.StrokeOrderView
import hu.sigee.hiralearn.ui.formatDuration
import hu.sigee.hiralearn.ui.ink.HangingScroll
import hu.sigee.hiralearn.ui.ink.InkButton
import hu.sigee.hiralearn.ui.ink.InkedKana
import hu.sigee.hiralearn.ui.ink.inkWash
import hu.sigee.hiralearn.ui.ink.washiPaper
import hu.sigee.hiralearn.ui.session.BrushMark
import hu.sigee.hiralearn.ui.theme.LocalInkColors

/** Reference page for one kana: the kana on a scroll, progress per skill, stroke order and free writing practice. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KanaDetailScreen(kanaId: String, onBack: () -> Unit) {
    val kana = Hiragana.byId(kanaId) ?: run {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    val container = LocalContext.current.appContainer
    val cards by container.progress.cards.collectAsStateWithLifecycle(initialValue = emptyMap())
    val reference = container.strokes.strokesFor(kana)?.takeIf { container.strokes.canDraw(kana) }

    Scaffold(
        modifier = Modifier.washiPaper(seed = 61),
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                title = { Text(kana.romaji) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.back)) }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            HangingScroll(kana.char, kana.romaji)
            Spacer(Modifier.height(8.dp))
            SoundButton(kana)
            val others = kana.accepted - kana.romaji
            if (others.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(stringResource(R.string.also_typed_as, others.joinToString(", ")), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(18.dp))
            val skills by container.questionTypes.collectAsStateWithLifecycle()
            ProgressBand(kana.char, Skill.entries.filter { it in skills.skills }.associateWith { cards[CardId(kana.id, it)] }, container.clock())
            if (reference != null) {
                Spacer(Modifier.height(22.dp))
                Text(stringResource(R.string.stroke_order), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                StrokeOrderView(reference, Modifier.widthIn(max = 260.dp).fillMaxWidth())
                Spacer(Modifier.height(26.dp))
                Practice(kana.char, reference) { strokes -> HandwritingGrader(container.strokes).grade(strokes, kana, emptyList()) }
            }
        }
    }
}

@Composable
private fun ProgressBand(char: String, cards: Map<Skill, CardState?>, now: Long) {
    val ink = LocalInkColors.current
    Box(Modifier.fillMaxWidth().inkWash(ink.inkWash, seed = 62)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (cards.values.all { it == null }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    InkedKana(char, null, Modifier.size(44.dp), fontSize = 26.sp)
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.not_learned_yet), style = MaterialTheme.typography.bodyLarge)
                }
            } else {
                cards.forEach { (skill, state) -> SkillLine(char, skillLabel(skill), state, now) }
            }
        }
    }
}

@Composable
private fun SkillLine(char: String, label: String, state: CardState?, now: Long) {
    if (state == null) return
    val stage = stringResource(
        when {
            state.level <= 2 -> R.string.stage_learning
            state.level <= 5 -> R.string.stage_familiar
            else -> R.string.stage_strong
        },
    )
    val due = if (state.isDue(now)) stringResource(R.string.due_now) else stringResource(R.string.next_review_in_short, formatDuration(state.dueAt - now))
    Row(verticalAlignment = Alignment.CenterVertically) {
        InkedKana(char, state.level, Modifier.size(44.dp), fontSize = 26.sp, due = state.isDue(now))
        Spacer(Modifier.width(12.dp))
        Column {
            Text("$label · $stage", style = MaterialTheme.typography.titleSmall)
            Text(
                stringResource(R.string.level_line, state.level, Scheduler().maxLevel, due, state.timesCorrect, state.timesWrong),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Practice(char: String, reference: List<Stroke>, grade: (List<Stroke>) -> DrawingResult) {
    val ink = LocalInkColors.current
    var strokes by remember { mutableStateOf<List<Stroke>>(emptyList()) }
    var showGuide by remember { mutableStateOf(true) }
    var result by remember { mutableStateOf<DrawingResult?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    Text(stringResource(R.string.practise_writing), style = MaterialTheme.typography.titleMedium)
    Text(stringResource(R.string.free_practice), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(8.dp))
    FilterChip(
        selected = showGuide,
        onClick = { showGuide = !showGuide },
        label = { Text(stringResource(R.string.show_guide)) },
        colors = FilterChipDefaults.filterChipColors(containerColor = ink.sheet, selectedContainerColor = ink.ink, selectedLabelColor = ink.sheet),
    )
    Spacer(Modifier.height(6.dp))
    DrawingPad(
        strokes = strokes,
        onStrokesChange = { strokes = it; result = null },
        guide = if (showGuide) reference else null,
        overlay = if (result != null) reference else null,
        modifier = Modifier.widthIn(max = 360.dp).fillMaxWidth(),
    )
    Spacer(Modifier.height(8.dp))
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = { strokes = emptyList(); result = null }, enabled = strokes.isNotEmpty()) { Text(stringResource(R.string.clear)) }
        InkButton(stringResource(R.string.check), { result = grade(strokes); attempt++ }, enabled = strokes.isNotEmpty(), seed = 5)
    }
    result?.let { r ->
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            key(attempt) { BrushMark(r.correct, Modifier.size(44.dp)) }
            Spacer(Modifier.width(10.dp))
            Text(
                when (r.problem) {
                    DrawingProblem.NONE -> stringResource(R.string.looks_right)
                    DrawingProblem.STROKE_COUNT -> pluralStringResource(R.plurals.draw_stroke_count, r.expectedStrokes, char, r.expectedStrokes, r.drawnStrokes)
                    DrawingProblem.STROKE_ORDER -> stringResource(R.string.draw_order)
                    DrawingProblem.STROKE_DIRECTION -> stringResource(R.string.draw_direction)
                    DrawingProblem.SHAPE -> stringResource(R.string.draw_compare)
                },
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
    Spacer(Modifier.height(28.dp))
}

@Composable
private fun skillLabel(skill: Skill): String = stringResource(
    when (skill) {
        Skill.READING -> R.string.skill_reading
        Skill.WRITING -> R.string.skill_writing
        Skill.LISTENING -> R.string.skill_listening
    },
)
