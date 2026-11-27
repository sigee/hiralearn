package hu.sigee.hiralearn.ui.session

import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import hu.sigee.hiralearn.R
import hu.sigee.hiralearn.appContainer
import hu.sigee.hiralearn.core.Answer
import hu.sigee.hiralearn.core.DrawingProblem
import hu.sigee.hiralearn.core.Evaluation
import hu.sigee.hiralearn.core.Hiragana
import hu.sigee.hiralearn.core.Kana
import hu.sigee.hiralearn.core.QuestionType
import hu.sigee.hiralearn.core.SessionMode
import hu.sigee.hiralearn.core.SessionSummary
import hu.sigee.hiralearn.core.Skill
import hu.sigee.hiralearn.core.Stroke
import hu.sigee.hiralearn.core.Task
import hu.sigee.hiralearn.ui.components.DrawingPad
import hu.sigee.hiralearn.ui.components.SoundButton
import hu.sigee.hiralearn.ui.components.StrokeOrderView
import hu.sigee.hiralearn.ui.ink.InkButton
import hu.sigee.hiralearn.ui.ink.ManuscriptSquare
import hu.sigee.hiralearn.ui.ink.washiPaper
import hu.sigee.hiralearn.ui.theme.KanaFont
import hu.sigee.hiralearn.ui.theme.LocalInkColors
import hu.sigee.hiralearn.ui.words.WordList

@Composable
fun SessionScreen(viewModel: SessionViewModel, onExit: () -> Unit, onReadWords: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Surface(Modifier.fillMaxSize().washiPaper(seed = 31), color = Color.Transparent, contentColor = MaterialTheme.colorScheme.onSurface) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onExit) {
                    Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.quit_session), tint = LocalInkColors.current.pencil)
                }
                val progress = (state as? SessionUiState.Active)?.progress ?: if (state is SessionUiState.Finished) 1f else 0f
                InkProgress(progress, Modifier.weight(1f).padding(end = 20.dp))
            }
            when (val s = state) {
                SessionUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                is SessionUiState.Empty -> Message(
                    when (s.mode) {
                        SessionMode.LEARN -> stringResource(R.string.nothing_learn)
                        SessionMode.REVIEW -> stringResource(R.string.nothing_review)
                        SessionMode.PRACTICE -> stringResource(R.string.nothing_practice)
                        SessionMode.WORDS -> stringResource(R.string.nothing_words)
                        SessionMode.DRILL -> stringResource(R.string.nothing_drill)
                    },
                    onExit,
                )
                is SessionUiState.Finished -> Summary(s.summary, onExit, onReadWords)
                is SessionUiState.Active -> key(s.stepKey) {
                    when (val task = s.task) {
                        is Task.Introduce -> Introduction(task, viewModel::onIntroductionSeen)
                        is Task.Question -> Question(
                            task = task,
                            evaluation = s.evaluation,
                            canSkipDrawing = s.canSkipDrawing,
                            onAnswer = viewModel::onAnswer,
                            onSkipDrawing = viewModel::onSkipDrawing,
                            onContinue = viewModel::onContinue,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Message(text: String, onExit: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        InkButton(stringResource(R.string.back), onExit)
    }
}

@Composable
private fun Introduction(task: Task.Introduce, onNext: () -> Unit) {
    val kana = task.kana
    val ink = LocalInkColors.current
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(R.string.new_kana), style = MaterialTheme.typography.labelLarge, color = ink.seal)
            Text(kana.char, fontFamily = KanaFont, fontSize = 112.sp, color = ink.ink)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(kana.romaji, fontSize = 34.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(12.dp))
                SoundButton(kana, size = 48.dp, autoPlay = true)
            }
            AlternativeSpellings(kana)
            Spacer(Modifier.height(16.dp))
            val strokes = task.strokes
            if (strokes != null && kana.char.length == 1) {
                Text(stringResource(R.string.stroke_order), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                StrokeOrderView(strokes, Modifier.widthIn(max = 240.dp).fillMaxWidth(), loop = true)
            } else {
                YoonExplanation(kana)
            }
        }
        InkButton(stringResource(R.string.got_it), onNext, Modifier.fillMaxWidth().padding(vertical = 16.dp), seed = 7)
    }
}

@Composable
private fun AlternativeSpellings(kana: Kana) {
    val others = kana.accepted - kana.romaji
    if (others.isNotEmpty()) {
        Text(
            stringResource(R.string.also_typed_as, others.joinToString(", ")),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun YoonExplanation(kana: Kana) {
    if (kana.char.length != 2) return
    val base = Hiragana.byChar(kana.char.substring(0, 1))
    val small = Hiragana.byChar(kana.char.substring(1, 2).let { mapOf("ゃ" to "や", "ゅ" to "ゆ", "ょ" to "よ")[it] ?: it })
    Text(
        stringResource(R.string.yoon_explanation, kana.char[0].toString(), base?.romaji.orEmpty(), kana.char[1].toString(), small?.romaji.orEmpty(), kana.romaji),
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun Question(
    task: Task.Question,
    evaluation: Evaluation?,
    canSkipDrawing: Boolean,
    onAnswer: (Answer) -> Unit,
    onSkipDrawing: () -> Unit,
    onContinue: (Boolean) -> Unit,
) {
    val answered = evaluation != null
    var strokes by remember { mutableStateOf<List<Stroke>>(emptyList()) }
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Prompt(task)
            Spacer(Modifier.height(12.dp))
            when (task.type) {
                QuestionType.READING_CHOICE, QuestionType.WRITING_CHOICE, QuestionType.LISTENING_CHOICE -> Choices(task, evaluation, onAnswer)
                QuestionType.READING_TYPING -> Typing(answered, onAnswer)
                QuestionType.WRITING_DRAWING, QuestionType.LISTENING_DRAWING -> {
                    DrawingPad(
                        strokes = strokes,
                        onStrokesChange = { strokes = it },
                        enabled = !answered,
                        guide = if (task.guided) task.reference else null,
                        overlay = if (answered) task.reference else null,
                        modifier = Modifier.widthIn(max = 360.dp).fillMaxWidth(),
                    )
                    if (!answered) {
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { strokes = strokes.dropLast(1) }, enabled = strokes.isNotEmpty()) {
                                Icon(painterResource(R.drawable.ic_undo), contentDescription = stringResource(R.string.undo_stroke))
                            }
                            TextButton(onClick = { strokes = emptyList() }, enabled = strokes.isNotEmpty()) { Text(stringResource(R.string.clear)) }
                            InkButton(stringResource(R.string.check), { onAnswer(Answer.Drawn(strokes)) }, enabled = strokes.isNotEmpty(), seed = 5)
                        }
                        if (canSkipDrawing) TextButton(onClick = onSkipDrawing) { Text(stringResource(R.string.cant_draw)) }
                    }
                }
            }
            if (!answered) TextButton(onClick = { onAnswer(Answer.DontKnow) }) { Text(stringResource(R.string.dont_know)) }
        }
        if (evaluation != null) Feedback(evaluation, onContinue)
    }
}

@Composable
private fun Prompt(task: Task.Question) {
    val kana = task.kana
    val ink = LocalInkColors.current
    val label = stringResource(
        when {
            task.type == QuestionType.READING_TYPING -> R.string.prompt_type
            task.type == QuestionType.READING_CHOICE -> R.string.prompt_reading_choice
            task.type == QuestionType.WRITING_CHOICE -> R.string.prompt_writing_choice
            task.type == QuestionType.LISTENING_CHOICE -> R.string.prompt_listening_choice
            task.type == QuestionType.LISTENING_DRAWING -> R.string.prompt_listening_write
            task.guided -> R.string.prompt_trace
            else -> R.string.prompt_write
        },
    )
    Text(label, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    when (task.type.skill) {
        Skill.READING -> Text(kana.char, fontFamily = KanaFont, fontSize = 104.sp, color = ink.ink)
        Skill.WRITING -> Text(kana.romaji, fontSize = 52.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(vertical = 8.dp))
        // Only the sound: showing the kana or its rōmaji would give the answer away.
        Skill.LISTENING -> SoundButton(kana, Modifier.padding(vertical = 14.dp), size = 96.dp, autoPlay = true, seed = 23)
    }
}

@Composable
private fun Choices(task: Task.Question, evaluation: Evaluation?, onAnswer: (Answer) -> Unit) {
    val chosen = (evaluation?.answer as? Answer.Choice)?.kana
    val showKana = task.type != QuestionType.READING_CHOICE
    Column(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.widthIn(max = 420.dp)) {
        task.options.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                row.forEach { option ->
                    val state = when {
                        evaluation == null -> OptionState.IDLE
                        option.id == task.kana.id -> OptionState.RIGHT
                        option.id == chosen?.id -> OptionState.WRONG
                        else -> OptionState.DIMMED
                    }
                    PaperOption(
                        text = if (showKana) option.char else option.romaji,
                        kana = showKana,
                        state = state,
                        onClick = { onAnswer(Answer.Choice(option)) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun Typing(answered: Boolean, onAnswer: (Answer) -> Unit) {
    var text by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val submit = { if (text.isNotBlank()) onAnswer(Answer.Typed(text)) }
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

@Composable
private fun Feedback(evaluation: Evaluation, onContinue: (Boolean) -> Unit) {
    val kana = evaluation.question.kana
    val ok = evaluation.correct
    val strokesLib = LocalContext.current.appContainer.strokes
    FeedbackPanel(
        correct = ok,
        seed = if (ok) 41 else 43,
        summary = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(kana.char, fontFamily = KanaFont, fontSize = 24.sp)
                Text("  " + stringResource(R.string.kana_is, kana.romaji), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.width(8.dp))
                SoundButton(kana, size = 36.dp, seed = 29)
            }
        },
        details = {
            val confused = evaluation.confusedWith
            if (!ok && confused != null) {
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.you_answered, confused.char, confused.romaji),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(8.dp))
                MixUpComparison(kana, confused)
            }
            evaluation.drawing?.let { d ->
                val text = when (d.problem) {
                    DrawingProblem.NONE -> stringResource(R.string.draw_nice)
                    DrawingProblem.STROKE_COUNT -> pluralStringResource(R.plurals.draw_stroke_count, d.expectedStrokes, kana.char, d.expectedStrokes, d.drawnStrokes)
                    DrawingProblem.STROKE_ORDER -> stringResource(R.string.draw_order)
                    DrawingProblem.STROKE_DIRECTION -> stringResource(R.string.draw_direction)
                    DrawingProblem.SHAPE -> stringResource(R.string.draw_shape, kana.char)
                }
                Spacer(Modifier.height(4.dp))
                Text(text, style = MaterialTheme.typography.bodyMedium)
            }
            if (!ok && evaluation.question.type.skill != Skill.READING && confused == null) {
                strokesLib.strokesFor(kana)?.takeIf { kana.char.length == 1 }?.let {
                    Spacer(Modifier.height(8.dp))
                    StrokeOrderView(it, Modifier.size(140.dp).align(Alignment.CenterHorizontally))
                }
            }
        },
        actions = {
            val canOverrule = !ok && evaluation.answer !is Answer.DontKnow && evaluation.answer !is Answer.Choice
            if (canOverrule) {
                InkButton(stringResource(R.string.i_was_right), { onContinue(true) }, Modifier.weight(1f), outlined = true, seed = 12)
            }
            InkButton(stringResource(R.string.continue_), { onContinue(false) }, Modifier.weight(1f), seed = 3)
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Summary(summary: SessionSummary, onExit: () -> Unit, onReadWords: () -> Unit) {
    val ink = LocalInkColors.current
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                when (summary.mode) {
                    SessionMode.LEARN -> stringResource(R.string.lesson_complete)
                    SessionMode.REVIEW -> stringResource(R.string.review_complete)
                    SessionMode.PRACTICE -> stringResource(R.string.practice_complete)
                    SessionMode.WORDS -> stringResource(R.string.words_complete)
                    SessionMode.DRILL -> stringResource(R.string.drill_complete)
                },
                style = MaterialTheme.typography.headlineMedium,
            )
            Spacer(Modifier.height(8.dp))
            StampedSeal(96.dp)
            if (summary.answered > 0) {
                val pct = summary.correct * 100 / summary.answered
                Text(stringResource(R.string.score, summary.correct, summary.answered, pct), style = MaterialTheme.typography.titleMedium)
            }
            if (summary.learned.isNotEmpty()) {
                Spacer(Modifier.height(24.dp))
                Text(stringResource(R.string.learned), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                KanaRow(summary.learned, ink.ink)
                Spacer(Modifier.height(6.dp))
                Text(stringResource(R.string.learned_hint), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
            }
            // The reward for finishing a lesson: real words that can be read now.
            if (summary.newWords.isNotEmpty()) {
                Spacer(Modifier.height(24.dp))
                Text(stringResource(R.string.new_words), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                WordList(summary.newWords.take(MAX_NEW_WORDS_SHOWN))
            }
            if (summary.troublesome.isNotEmpty()) {
                Spacer(Modifier.height(24.dp))
                Text(stringResource(R.string.needs_practice), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                KanaRow(summary.troublesome, ink.vermilion.copy(alpha = 0.85f))
                Spacer(Modifier.height(6.dp))
                Text(stringResource(R.string.needs_practice_hint), style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (summary.newWords.isNotEmpty()) {
            InkButton(stringResource(R.string.read_them_now), onReadWords, Modifier.fillMaxWidth(), outlined = true, seed = 9)
            Spacer(Modifier.height(10.dp))
        }
        InkButton(stringResource(R.string.done), onExit, Modifier.fillMaxWidth(), seed = 8)
    }
}

private const val MAX_NEW_WORDS_SHOWN = 6

/** Kana on a strip of touching manuscript squares. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun KanaRow(kana: List<Kana>, color: Color) {
    FlowRow(horizontalArrangement = Arrangement.Center) {
        kana.forEach { k ->
            ManuscriptSquare(Modifier.size(width = if (k.char.length > 1) 70.dp else 54.dp, height = 54.dp)) {
                Text(k.char, fontFamily = KanaFont, fontSize = if (k.char.length > 1) 24.sp else 32.sp, color = color)
            }
        }
    }
}
