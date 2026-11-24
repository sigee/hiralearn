package hu.sigee.hiralearn.ui.settings

import androidx.annotation.RawRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import hu.sigee.hiralearn.R
import hu.sigee.hiralearn.ui.ink.washiPaper
import hu.sigee.hiralearn.ui.theme.LocalInkColors

/**
 * Credits and licence texts for everything bundled with the app. The OFL and Apache 2.0 require their texts to
 * travel with every copy, so they ship as raw resources instead of being linked.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LicensesScreen(onBack: () -> Unit) {
    Scaffold(
        modifier = Modifier.washiPaper(seed = 61),
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                title = { Text(stringResource(R.string.licenses)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.back)) }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Section(stringResource(R.string.licenses_strokes_title), seed = 62) {
                Text(stringResource(R.string.licenses_strokes_text), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(6.dp))
                Link(stringResource(R.string.kanjivg_url))
                Link(stringResource(R.string.cc_by_sa_url))
            }

            Section(stringResource(R.string.licenses_fonts_title), seed = 63) {
                Text(stringResource(R.string.licenses_fonts_text), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(6.dp))
                stringArrayResource(R.array.font_copyrights).forEach { Notice(it) }
                LicenceText(R.raw.ofl_1_1)
            }

            Section(stringResource(R.string.licenses_libraries_title), seed = 64) {
                Text(stringResource(R.string.licenses_libraries_text), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(6.dp))
                stringArrayResource(R.array.library_names).forEach { Notice(it) }
                LicenceText(R.raw.apache_2_0)
            }

            Text(
                stringResource(R.string.licenses_english_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        }
    }
}

@Composable
private fun Notice(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 2.dp))
}

@Composable
private fun Link(url: String) {
    val seal = LocalInkColors.current.seal
    val text = remember(url, seal) {
        buildAnnotatedString {
            withLink(LinkAnnotation.Url(url, TextLinkStyles(SpanStyle(color = seal, textDecoration = TextDecoration.Underline)))) {
                append(url)
            }
        }
    }
    Text(text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 2.dp))
}

/** A full licence text, folded away by default since it is long. */
@Composable
private fun LicenceText(@RawRes res: Int) {
    var open by rememberSaveable(res) { mutableStateOf(false) }
    val context = LocalContext.current
    TextButton(onClick = { open = !open }) {
        Text(stringResource(if (open) R.string.hide_licence_text else R.string.show_licence_text))
    }
    if (open) {
        val text = remember(res) { context.resources.openRawResource(res).bufferedReader().use { it.readText() } }
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
