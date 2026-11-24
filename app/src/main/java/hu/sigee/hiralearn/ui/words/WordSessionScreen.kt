package hu.sigee.hiralearn.ui.words

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import hu.sigee.hiralearn.R
import hu.sigee.hiralearn.appContainer
import hu.sigee.hiralearn.core.DrawingProblem
import hu.sigee.hiralearn.core.DrawingResult
import hu.sigee.hiralearn.core.Kana
import hu.sigee.hiralearn.core.QuestionType
import hu.sigee.hiralearn.core.Skill
import hu.sigee.hiralearn.core.Stroke
import hu.sigee.hiralearn.core.Word
import hu.sigee.hiralearn.core.WordAnswer
import hu.sigee.hiralearn.core.WordEvaluation
import hu.sigee.hiralearn.core.WordQuestion
import hu.sigee.hiralearn.core.WordSummary
import hu.sigee.hiralearn.ui.components.DrawingPad
import hu.sigee.hiralearn.ui.components.SoundButton
import hu.sigee.hiralearn.ui.ink.InkButton
import hu.sigee.hiralearn.ui.ink.ManuscriptSquare
import hu.sigee.hiralearn.ui.ink.inkWash
import hu.sigee.hiralearn.ui.ink.washiPaper
import hu.sigee.hiralearn.ui.session.BrushMark
import hu.sigee.hiralearn.ui.session.InkProgress
import hu.sigee.hiralearn.ui.session.MixUpComparison
import hu.sigee.hiralearn.ui.session.OptionState
import hu.sigee.hiralearn.ui.session.PaperOption
import hu.sigee.hiralearn.ui.session.StampedSeal
import hu.sigee.hiralearn.ui.theme.KanaFont
import hu.sigee.hiralearn.ui.theme.LocalInkColors
import hu.sigee.hiralearn.ui.wordMeaning

@Composable
fun WordSessionScreen(viewModel: WordSessionViewModel, onExit: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Surface(Modifier.fillMaxSize().washiPaper(seed = 37), color = Color.Transparent, contentColor = MaterialTheme.colorScheme.onSurface) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onExit) {
                    Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.quit_session), tint = LocalInkColors.current.pencil)
                }
                val progress = (state as? WordUiState.Active)?.progress ?: if (state is WordUiState.Finished) 1f else 0f
                InkProgress(progress, Modifier.weight(1f).padding(end = 20.dp))
            }
            when (val s = state) {
                WordUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                WordUiState.Empty -> Column(
                    Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(stringResource(R.string.nothing_words), style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(16.dp))
                    InkButton(stringResource(R.string.back), onExit)
                }
                is WordUiState.Finished -> Summary(s.summary, onExit)
                is WordUiState.Active -> key(s.stepKey) {
                    Question(s.question, s.evaluation, s.canSkipDrawing, viewModel::onAnswer, viewModel::onSkipDrawing, viewModel::onContinue)
                }
            }
        }
    }
}

/** Word text size: long words get smaller so they stay on one line. */
private fun wordSize(word: Word): TextUnit = when {
    word.text.length <= 3 -> 76.sp
    word.text.length <= 5 -> 60.sp
    else -> 46.sp
}

/** Kana size on a half-width answer option. */
private fun optionSize(text: String): TextUnit = when {
    text.length <= 3 -> 34.sp
    text.length <= 4 -> 28.sp
    text.length <= 5 -> 23.sp
    else -> 19.sp
}

@Composable
private fun Question(
    question: WordQuestion,
    evaluation: WordEvaluation?,
    canSkipDrawing: Boolean,
    onAnswer: (WordAnswer) -> Unit,
    onSkipDrawing: () -> Unit,
    onContinue: (Boolean) -> Unit,
) {
    val answered = evaluation != null
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Prompt(question, evaluation)
            Spacer(Modifier.height(16.dp))
            when (question.type) {
                QuestionType.READING_CHOICE, QuestionType.WRITING_CHOICE, QuestionType.LISTENING_CHOICE -> Choices(question, evaluation, onAnswer)
                QuestionType.READING_TYPING -> Typing(answered, onAnswer)
                QuestionType.WRITING_DRAWING, QuestionType.LISTENING_DRAWING -> {
                    WordDrawing(question.word, evaluation, onAnswer)
                    if (!answered && canSkipDrawing) TextButton(onClick = onSkipDrawing) { Text(stringResource(R.string.cant_draw)) }
                }
            }
            if (!answered) TextButton(onClick = { onAnswer(WordAnswer.DontKnow) }) { Text(stringResource(R.string.dont_know)) }
        }
        if (evaluation != null) Feedback(evaluation, onContinue)
    }
}

@Composable
private fun Prompt(question: WordQuestion, evaluation: WordEvaluation?) {
    val ink = LocalInkColors.current
    val word = question.word
    val label = when (question.type) {
        QuestionType.READING_CHOICE -> R.string.prompt_word_choice
        QuestionType.READING_TYPING -> R.string.prompt_word_type
        QuestionType.WRITING_CHOICE -> R.string.prompt_word_write_choice
        QuestionType.WRITING_DRAWING -> R.string.prompt_word_write
        QuestionType.LISTENING_CHOICE -> R.string.prompt_word_listen_choice
        QuestionType.LISTENING_DRAWING -> R.string.prompt_word_listen_write
    }
    Text(stringResource(label), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(8.dp))
    when (question.type.skill) {
        Skill.READING -> WordText(word, misreadPosition = evaluation?.misread?.position, fontSize = wordSize(word), color = ink.ink)
        Skill.WRITING -> {
            Text(word.romaji, fontSize = 44.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
            Text(stringResource(R.string.word_means, wordMeaning(word)), style = MaterialTheme.typography.bodyLarge)
        }
        // Only the sound: the word, its rōmaji or its meaning on screen would give the answer away or hint at it.
        Skill.LISTENING -> SoundButton(word.text, word.id, Modifier.padding(vertical = 10.dp), size = 96.dp, autoPlay = true, seed = 23)
    }
}

/** The word in ink; after a mix-up, the kana that was mixed up turns vermilion. */
@Composable
private fun WordText(word: Word, misreadPosition: Int?, fontSize: TextUnit, color: Color) {
    val wrong = LocalInkColors.current.vermilion
    val text = buildAnnotatedString {
        word.kana.forEachIndexed { i, k ->
            if (i == misreadPosition) withStyle(SpanStyle(color = wrong)) { append(k.char) } else append(k.char)
        }
    }
    Text(text, fontFamily = KanaFont, fontSize = fontSize, color = color, maxLines = 1, softWrap = false)
}

@Composable
private fun Choices(question: WordQuestion, evaluation: WordEvaluation?, onAnswer: (WordAnswer) -> Unit) {
    val chosen = (evaluation?.answer as? WordAnswer.Choice)?.option
    // Reading asks for the rōmaji; writing and listening ask for the spelling.
    val showKana = question.type != QuestionType.READING_CHOICE
    Column(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.widthIn(max = 420.dp)) {
        question.options.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                row.forEach { option ->
                    val state = when {
                        evaluation == null -> OptionState.IDLE
                        option.misread == null -> OptionState.RIGHT
                        option == chosen -> OptionState.WRONG
                        else -> OptionState.DIMMED
                    }
                    PaperOption(
                        text = if (showKana) option.text else option.romaji,
                        kana = showKana,
                        state = state,
                        onClick = { onAnswer(WordAnswer.Choice(option)) },
                        modifier = Modifier.weight(1f),
                        fontSize = if (showKana) optionSize(option.text) else TextUnit.Unspecified,
                    )
                }
            }
        }
    }
}

@Composable
private fun Typing(answered: Boolean, onAnswer: (WordAnswer) -> Unit) {
    var text by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val submit = { if (text.isNotBlank()) onAnswer(WordAnswer.Typed(text)) }
    OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        readOnly = answered,
        singleLine = true,
        label = { Text(stringResource(R.string.reading_in_romaji)) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, autoCorrectEnabled = false, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { submit() }),
        textStyle = MaterialTheme.typography.headlineSmall.copy(textAlign = TextAlign.Center),
        modifier = Modifier.widthIn(max = 320.dp).fillMaxWidth().focusRequester(focus),
    )
    if (!answered) {
        Spacer(Modifier.height(10.dp))
        InkButton(stringResource(R.string.check), submit, enabled = text.isNotBlank(), seed = 5)
    }
}

/**
 * Writing a word by hand, one kana at a time: a strip of small squares (one per kana, tap one to go back to it) above
 * a large pad for the selected kana. After checking, every square shows the model as a thin line, and the squares of
 * kana that weren't right get a vermilion edge.
 */
@Composable
private fun WordDrawing(word: Word, evaluation: WordEvaluation?, onAnswer: (WordAnswer) -> Unit) {
    val ink = LocalInkColors.current
    val library = LocalContext.current.appContainer.strokes
    val answered = evaluation != null
    var drawings by remember { mutableStateOf(List(word.kana.size) { emptyList<Stroke>() }) }
    var selected by remember { mutableIntStateOf(0) }
    val results = evaluation?.drawings
    val count = word.kana.size
    fun reference(i: Int) = if (answered) library.strokesFor(word.kana[i]) else null

    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        word.kana.indices.forEach { i ->
            val edge = when {
                results != null && !results[i].correct -> ink.vermilion
                i == selected -> ink.seal
                else -> ink.gridLine
            }
            val cellLabel = stringResource(R.string.word_kana_cell, i + 1, count)
            DrawingPad(
                strokes = drawings[i],
                onStrokesChange = {},
                enabled = false,
                overlay = reference(i),
                modifier = Modifier
                    .size(52.dp)
                    .border(if (i == selected || edge == ink.vermilion) 2.dp else 1.dp, edge)
                    .clickable(role = Role.Tab) { selected = i }
                    .semantics {
                        contentDescription = cellLabel
                        this.selected = i == selected
                    },
            )
        }
    }
    Spacer(Modifier.height(10.dp))
    DrawingPad(
        strokes = drawings[selected],
        onStrokesChange = { strokes -> drawings = drawings.toMutableList().also { it[selected] = strokes } },
        enabled = !answered,
        overlay = reference(selected),
        modifier = Modifier.widthIn(max = 320.dp).fillMaxWidth(),
    )
    if (!answered) {
        Spacer(Modifier.height(8.dp))
        val current = drawings[selected]
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { drawings = drawings.toMutableList().also { it[selected] = current.dropLast(1) } }, enabled = current.isNotEmpty()) {
                Icon(painterResource(R.drawable.ic_undo), contentDescription = stringResource(R.string.undo_stroke))
            }
            TextButton(onClick = { drawings = drawings.toMutableList().also { it[selected] = emptyList() } }, enabled = current.isNotEmpty()) {
                Text(stringResource(R.string.clear))
            }
            // The next empty square, if any: "Next kana" moves there, otherwise the whole word can be checked.
            val nextEmpty = (1 until count).map { (selected + it) % count }.firstOrNull { drawings[it].isEmpty() }
            if (nextEmpty != null) {
                InkButton(stringResource(R.string.word_next_kana), { selected = nextEmpty }, enabled = current.isNotEmpty(), seed = 6)
            } else {
                InkButton(stringResource(R.string.check), { onAnswer(WordAnswer.Drawn(drawings)) }, enabled = current.isNotEmpty(), seed = 5)
            }
        }
    }
}

@Composable
private fun Feedback(evaluation: WordEvaluation, onContinue: (Boolean) -> Unit) {
    val ink = LocalInkColors.current
    val word = evaluation.question.word
    val ok = evaluation.correct
    val wash = if (ok) ink.matchaWash.copy(alpha = (ink.matchaWash.alpha * 1.6f).coerceAtMost(1f)) else ink.vermilion.copy(alpha = 0.17f)
    Box(Modifier.fillMaxWidth().padding(horizontal = 6.dp).inkWash(wash, seed = if (ok) 47 else 49)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BrushMark(ok, Modifier.size(56.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(if (ok) R.string.correct else R.string.not_quite), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        WordText(word, misreadPosition = evaluation.misread?.position, fontSize = 24.sp, color = ink.ink)
                        Text("  " + word.romaji, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.width(8.dp))
                        SoundButton(word.text, word.id, size = 36.dp, seed = 29)
                    }
                    Text(stringResource(R.string.word_means, wordMeaning(word)), style = MaterialTheme.typography.bodyLarge)
                }
            }
            val target = evaluation.misreadKana
            val readAs = evaluation.misread?.readAs
            if (!ok && target != null && readAs != null) {
                Spacer(Modifier.height(6.dp))
                val text = if (evaluation.question.type.skill == Skill.READING) R.string.word_misread else R.string.word_mixed_up
                Text(stringResource(text, target.char, target.romaji, readAs.char, readAs.romaji), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                MixUpComparison(target, readAs)
            }
            evaluation.drawings?.let { results ->
                Spacer(Modifier.height(4.dp))
                Text(drawingFeedback(word, results), style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                // Typing and handwriting can be misjudged (a typo, a messy stroke); a picked option can't.
                val canOverrule = !ok && (evaluation.answer is WordAnswer.Typed || evaluation.answer is WordAnswer.Drawn)
                if (canOverrule) {
                    InkButton(stringResource(R.string.i_was_right), { onContinue(true) }, Modifier.weight(1f), outlined = true, seed = 12)
                }
                InkButton(stringResource(R.string.continue_), { onContinue(false) }, Modifier.weight(1f), seed = 3)
            }
        }
    }
}

/** What went wrong with the first kana that wasn't written right, in the words of the kana session. */
@Composable
private fun drawingFeedback(word: Word, results: List<DrawingResult>): String {
    val i = results.indexOfFirst { !it.correct }
    if (i < 0) return stringResource(R.string.draw_nice)
    val kana: Kana = word.kana[i]
    val d = results[i]
    return when (d.problem) {
        DrawingProblem.NONE, DrawingProblem.SHAPE -> stringResource(R.string.draw_shape, kana.char)
        DrawingProblem.STROKE_COUNT -> pluralStringResource(R.plurals.draw_stroke_count, d.expectedStrokes, kana.char, d.expectedStrokes, d.drawnStrokes)
        DrawingProblem.STROKE_ORDER -> stringResource(R.string.draw_order) + " (" + kana.char + ")"
        DrawingProblem.STROKE_DIRECTION -> stringResource(R.string.draw_direction) + " (" + kana.char + ")"
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Summary(summary: WordSummary, onExit: () -> Unit) {
    val ink = LocalInkColors.current
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.words_complete), style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(8.dp))
            StampedSeal(96.dp)
            if (summary.answered > 0) {
                val pct = summary.correct * 100 / summary.answered
                Text(stringResource(R.string.score, summary.correct, summary.answered, pct), style = MaterialTheme.typography.titleMedium)
            }
            if (summary.read.isNotEmpty()) {
                Spacer(Modifier.height(24.dp))
                Text(stringResource(R.string.words_read), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                WordList(summary.read)
            }
            if (summary.troublesome.isNotEmpty()) {
                Spacer(Modifier.height(24.dp))
                Text(stringResource(R.string.needs_practice), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.Center) {
                    summary.troublesome.forEach { k ->
                        ManuscriptSquare(Modifier.size(width = if (k.char.length > 1) 70.dp else 54.dp, height = 54.dp)) {
                            Text(k.char, fontFamily = KanaFont, fontSize = if (k.char.length > 1) 24.sp else 32.sp, color = ink.vermilion.copy(alpha = 0.85f))
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(stringResource(R.string.needs_practice_hint), style = MaterialTheme.typography.bodyMedium)
            }
        }
        InkButton(stringResource(R.string.done), onExit, Modifier.fillMaxWidth(), seed = 8)
    }
}

/** Words with their reading and meaning, one per line. Also used for the words a lesson unlocked. */
@Composable
fun WordList(words: List<Word>, modifier: Modifier = Modifier) {
    val ink = LocalInkColors.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        words.forEach { w ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(w.text, fontFamily = KanaFont, fontSize = 26.sp, color = ink.ink)
                Text("  " + w.romaji + " · " + wordMeaning(w), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
