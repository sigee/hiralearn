package hu.sigee.hiralearn.ui.chart

import hu.sigee.hiralearn.ui.lessonTitle
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import hu.sigee.hiralearn.R
import hu.sigee.hiralearn.core.Kana
import hu.sigee.hiralearn.core.Lesson
import hu.sigee.hiralearn.ui.home.HomeViewModel
import hu.sigee.hiralearn.ui.home.KanaProgress
import hu.sigee.hiralearn.ui.ink.InkTicks
import hu.sigee.hiralearn.ui.ink.InkedKana
import hu.sigee.hiralearn.ui.ink.washiPaper

/**
 * Every kana on a sheet of manuscript paper. A kana's ink darkens as it is learned:
 * faint outline (new) → pencil (learning) → ink (familiar) → full ink with an OK seal (strong).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KanaChartScreen(viewModel: HomeViewModel, onKana: (Kana) -> Unit, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Box(Modifier.fillMaxSize().washiPaper(seed = 23)) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.kana_chart)) },
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
            LazyColumn(
                contentPadding = PaddingValues(
                    start = 20.dp,
                    end = 20.dp,
                    top = padding.calculateTopPadding(),
                    bottom = padding.calculateBottomPadding() + 24.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item {
                    Column {
                        Text(
                            stringResource(R.string.inked_count, state.learned, state.total),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(6.dp))
                        InkTicks(state.learned, state.total)
                        Spacer(Modifier.height(10.dp))
                        Legend()
                    }
                }
                items(state.lessons, key = { it.first.index }) { (lesson, kana) -> LessonRow(lesson, kana, onKana) }
            }
        }
    }
}

@Composable
private fun Legend() {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        listOf(null to R.string.stage_new, 1 to R.string.stage_learning, 4 to R.string.stage_familiar, 7 to R.string.stage_strong).forEach { (level, label) ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                InkedKana("お", level, Modifier.size(44.dp), fontSize = 26.sp)
                Text(stringResource(label), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LessonRow(lesson: Lesson, kana: List<KanaProgress>, onKana: (Kana) -> Unit) {
    Column {
        Text(lessonTitle(lesson), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        // Squares touch each other like a row of manuscript paper.
        FlowRow {
            kana.forEach { p ->
                val wide = p.kana.char.length > 1
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    InkedKana(
                        p.kana.char,
                        p.level,
                        Modifier.size(width = if (wide) 76.dp else 58.dp, height = 58.dp),
                        due = p.due,
                        fontSize = if (wide) 26.sp else 32.sp,
                        onClick = { onKana(p.kana) },
                    )
                    Text(p.kana.romaji, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
