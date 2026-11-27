package hu.sigee.hiralearn.ui.words

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
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
import hu.sigee.hiralearn.core.Word
import hu.sigee.hiralearn.core.Words
import hu.sigee.hiralearn.ui.components.SoundButton
import hu.sigee.hiralearn.ui.ink.InkButton
import hu.sigee.hiralearn.ui.ink.inkLevel
import hu.sigee.hiralearn.ui.ink.inkWash
import hu.sigee.hiralearn.ui.ink.washiPaper
import hu.sigee.hiralearn.ui.theme.KanaFont
import hu.sigee.hiralearn.ui.theme.LocalInkColors
import hu.sigee.hiralearn.ui.wordMeaning
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** A word the learner can read, inked by its weakest kana. */
data class GlossaryEntry(val word: Word, val level: Int)

data class GlossaryUiState(
    val loading: Boolean = true,
    /** Readable words, in the order the lessons unlock them. */
    val entries: List<GlossaryEntry> = emptyList(),
    /** Words that later lessons will unlock. */
    val locked: Int = 0,
)

class GlossaryViewModel(container: AppContainer) : ViewModel() {
    val state: StateFlow<GlossaryUiState> = container.progress.cards
        .map { cards ->
            val entries = Words.all.mapNotNull { w -> Words.readingLevel(w, cards)?.let { GlossaryEntry(w, it) } }
            GlossaryUiState(loading = false, entries = entries, locked = Words.all.size - entries.size)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GlossaryUiState())

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { GlossaryViewModel(container) }
        }
    }
}

/** Every word the learner can read, with reading and meaning; tap one to practise just that word. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlossaryScreen(viewModel: GlossaryViewModel, onPractice: (List<Word>) -> Unit, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    val resources = LocalContext.current.resources
    val shown = remember(state.entries, query, resources) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) state.entries
        else state.entries.filter { (w, _) ->
            w.text.contains(q) || w.romaji.replace("'", "").contains(q.replace("'", "")) || wordMeaning(w, resources).lowercase().contains(q)
        }
    }
    Box(Modifier.fillMaxSize().washiPaper(seed = 29)) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.glossary)) },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                    navigationIcon = {
                        IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.back)) }
                    },
                )
            },
        ) { padding ->
            if (state.loading) {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                return@Scaffold
            }
            if (state.entries.isEmpty()) {
                Column(
                    Modifier.fillMaxSize().padding(padding).padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(stringResource(R.string.nothing_words), style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(16.dp))
                    InkButton(stringResource(R.string.back), onBack)
                }
                return@Scaffold
            }
            LazyColumn(
                contentPadding = PaddingValues(
                    start = 20.dp,
                    end = 20.dp,
                    top = padding.calculateTopPadding(),
                    bottom = padding.calculateBottomPadding() + 24.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                item {
                    Column(Modifier.padding(bottom = 8.dp)) {
                        Text(
                            pluralStringResource(R.plurals.words_available, state.entries.size, state.entries.size),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (state.locked > 0) {
                            Text(
                                pluralStringResource(R.plurals.words_locked, state.locked, state.locked),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(stringResource(R.string.glossary_hint), style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            singleLine = true,
                            label = { Text(stringResource(R.string.glossary_search)) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        // A search narrows the list to a handful of words: practise them together.
                        if (query.isNotBlank() && shown.size > 1) {
                            Spacer(Modifier.height(10.dp))
                            InkButton(
                                pluralStringResource(R.plurals.practise_shown, shown.size, shown.size),
                                { onPractice(shown.map { it.word }) },
                                seed = 14,
                            )
                        }
                    }
                }
                if (shown.isEmpty()) {
                    item {
                        Text(
                            stringResource(R.string.glossary_no_match),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(shown, key = { it.word.id }) { entry -> GlossaryRow(entry, onClick = { onPractice(listOf(entry.word)) }) }
            }
        }
    }
}

@Composable
private fun GlossaryRow(entry: GlossaryEntry, onClick: () -> Unit) {
    val ink = LocalInkColors.current
    val level = inkLevel(entry.level)
    val practise = stringResource(R.string.practise_word)
    Row(
        Modifier
            .fillMaxWidth()
            .inkWash(ink.inkWash, seed = entry.word.id.hashCode())
            .clickable(role = Role.Button, onClickLabel = practise, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(entry.word.text, fontFamily = KanaFont, fontSize = 28.sp, color = level.color, fontWeight = level.weight)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(entry.word.romaji, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(wordMeaning(entry.word), style = MaterialTheme.typography.bodyMedium)
        }
        SoundButton(entry.word.text, entry.word.id, size = 40.dp, seed = 31)
    }
}
