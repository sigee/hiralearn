package hu.sigee.hiralearn.ui.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.ui.platform.LocalConfiguration
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import hu.sigee.hiralearn.data.ReminderSettings
import hu.sigee.hiralearn.reminder.ReviewReminder
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import android.os.LocaleList
import android.os.Build
import android.app.LocaleManager
import androidx.compose.ui.res.stringResource
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import hu.sigee.hiralearn.BuildConfig
import hu.sigee.hiralearn.R
import hu.sigee.hiralearn.appContainer
import hu.sigee.hiralearn.audio.KanaVoice
import hu.sigee.hiralearn.core.QuestionTypes
import hu.sigee.hiralearn.data.FeedbackPanelMode
import hu.sigee.hiralearn.data.ThemeMode
import hu.sigee.hiralearn.ui.ink.InkButton
import hu.sigee.hiralearn.ui.ink.inkWash
import hu.sigee.hiralearn.ui.ink.washiPaper
import hu.sigee.hiralearn.ui.theme.LocalInkColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, onLicenses: () -> Unit) {
    val context = LocalContext.current
    val container = context.appContainer
    val ink = LocalInkColors.current
    val types by container.settings.questionTypes.collectAsStateWithLifecycle()
    val themeMode by container.settings.themeMode.collectAsStateWithLifecycle()
    val reminder by container.settings.reminder.collectAsStateWithLifecycle()
    val feedbackMode by container.settings.feedbackMode.collectAsStateWithLifecycle()
    val autoAdvance by container.settings.autoAdvance.collectAsStateWithLifecycle()
    val voiceStatus by container.voice.status.collectAsStateWithLifecycle()
    // Coming back from installing a Japanese voice should turn listening on without restarting the app.
    LifecycleResumeEffect(Unit) {
        container.voice.refresh()
        onPauseOrDispose { }
    }
    var confirmReset by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.washiPaper(seed = 47),
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                title = { Text(stringResource(R.string.settings)) },
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
            Section(stringResource(R.string.appearance), seed = 51) {
                ThemeChoice(themeMode, container.settings::setThemeMode)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Section(stringResource(R.string.language), seed = 56) { LanguageChoice() }
            }

            Section(stringResource(R.string.reminder), seed = 57) {
                ReminderChoice(reminder) {
                    container.settings.setReminder(it)
                    ReviewReminder.apply(context, it, container.clock())
                }
            }

            Section(stringResource(R.string.question_types), seed = 52) {
                Text(
                    stringResource(R.string.question_types_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                TypeSwitch(stringResource(R.string.qt_choice), stringResource(R.string.qt_choice_hint), types.choice, types.copy(choice = !types.choice)) {
                    container.settings.setQuestionTypes(it)
                }
                TypeSwitch(stringResource(R.string.qt_typing), stringResource(R.string.qt_typing_hint), types.typing, types.copy(typing = !types.typing)) {
                    container.settings.setQuestionTypes(it)
                }
                TypeSwitch(
                    stringResource(R.string.qt_drawing),
                    stringResource(R.string.qt_drawing_hint),
                    types.drawing,
                    types.copy(drawing = !types.drawing),
                ) { container.settings.setQuestionTypes(it) }
                val voiceReady = voiceStatus == KanaVoice.Status.READY
                TypeSwitch(
                    stringResource(R.string.qt_listening),
                    stringResource(R.string.qt_listening_hint),
                    types.listening && voiceReady,
                    types.copy(listening = !types.listening),
                    enabled = voiceReady,
                ) { container.settings.setQuestionTypes(it) }
                if (voiceStatus == KanaVoice.Status.UNAVAILABLE) {
                    Text(
                        stringResource(R.string.no_japanese_voice),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    InkButton(stringResource(R.string.install_voice), {
                        if (!container.voice.openInstall(context)) {
                            Toast.makeText(context, R.string.no_voice_settings, Toast.LENGTH_SHORT).show()
                        }
                    }, outlined = true, seed = 11)
                }
            }

            Section(stringResource(R.string.feedback_panel), seed = 58) {
                Text(
                    stringResource(R.string.feedback_panel_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                FeedbackModeChoice(feedbackMode, container.settings::setFeedbackMode)
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.auto_advance), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            stringResource(R.string.auto_advance_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = autoAdvance, onCheckedChange = container.settings::setAutoAdvance, colors = inkSwitchColors())
                }
            }

            Section(stringResource(R.string.progress), seed = 53) {
                Text(
                    stringResource(R.string.progress_local),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                InkButton(stringResource(R.string.reset_all), { confirmReset = true }, outlined = true, seed = 9)
            }

            if (BuildConfig.DEBUG) {
                Section(stringResource(R.string.developer), seed = 54) {
                    Text(
                        stringResource(R.string.developer_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    InkButton(stringResource(R.string.make_all_due), {
                        container.appScope.launch {
                            val count = container.progress.makeAllDue(container.clock())
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, context.resources.getQuantityString(R.plurals.cards_due_now, count, count), Toast.LENGTH_SHORT).show()
                            }
                        }
                    }, outlined = true, seed = 10)
                    Spacer(Modifier.height(8.dp))
                    InkButton(stringResource(R.string.demo_progress), {
                        container.appScope.launch { container.progress.seedDemo(container.clock()) }
                    }, outlined = true, seed = 13)
                }
            }

            Section(stringResource(R.string.about), seed = 55) {
                Text(
                    stringResource(R.string.about_text),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(8.dp))
                InkButton(stringResource(R.string.licenses), onLicenses, outlined = true, seed = 12)
                Spacer(Modifier.height(8.dp))
                // versionName from app/build.gradle.kts, so the version has a single source.
                Text(
                    stringResource(R.string.app_version, BuildConfig.VERSION_NAME),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            containerColor = ink.sheet,
            shape = RoundedCornerShape(topStart = 4.dp, topEnd = 14.dp, bottomEnd = 6.dp, bottomStart = 12.dp),
            title = { Text(stringResource(R.string.reset_title)) },
            text = { Text(stringResource(R.string.reset_text)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmReset = false
                    container.appScope.launch { container.progress.resetAll() }
                }) { Text(stringResource(R.string.reset), color = ink.vermilion) }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

/** A settings group on its own soft ink wash. */
@Composable
internal fun Section(title: String, seed: Int, content: @Composable ColumnScope.() -> Unit) {
    val ink = LocalInkColors.current
    Box(Modifier.fillMaxWidth().inkWash(ink.inkWash, seed)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ThemeChoice(selected: ThemeMode, onSelect: (ThemeMode) -> Unit) {
    val ink = LocalInkColors.current
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(ThemeMode.SYSTEM to R.string.theme_system, ThemeMode.LIGHT to R.string.theme_paper, ThemeMode.DARK to R.string.theme_night).forEach { (mode, label) ->
            FilterChip(
                selected = selected == mode,
                onClick = { onSelect(mode) },
                label = { Text(stringResource(label)) },
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = ink.sheet,
                    selectedContainerColor = ink.ink,
                    selectedLabelColor = ink.sheet,
                ),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FeedbackModeChoice(selected: FeedbackPanelMode, onSelect: (FeedbackPanelMode) -> Unit) {
    val ink = LocalInkColors.current
    val options = listOf(
        FeedbackPanelMode.REMEMBER to R.string.feedback_remember,
        FeedbackPanelMode.OPEN to R.string.feedback_open,
        FeedbackPanelMode.FOLDED to R.string.feedback_folded,
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (mode, label) ->
            FilterChip(
                selected = selected == mode,
                onClick = { onSelect(mode) },
                label = { Text(stringResource(label)) },
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = ink.sheet,
                    selectedContainerColor = ink.ink,
                    selectedLabelColor = ink.sheet,
                ),
            )
        }
    }
}

/**
 * App language (Android 13+ per-app language). Language names are shown in their own language.
 * Older Android versions simply follow the phone language.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LanguageChoice() {
    val context = LocalContext.current
    val ink = LocalInkColors.current
    val manager = remember { context.getSystemService(LocaleManager::class.java) }
    var current by remember { mutableStateOf(manager.applicationLocales.toLanguageTags()) }
    val options = listOf("" to stringResource(R.string.theme_system), "en" to "English", "hu" to "Magyar", "de" to "Deutsch")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (tag, label) ->
            FilterChip(
                selected = current.substringBefore('-') == tag,
                onClick = {
                    current = tag
                    // Android recreates the activity with the new language.
                    manager.applicationLocales = if (tag.isEmpty()) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(tag)
                },
                label = { Text(label) },
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = ink.sheet,
                    selectedContainerColor = ink.ink,
                    selectedLabelColor = ink.sheet,
                ),
            )
        }
    }
}

/** One question-type switch; disabled when flipping it would leave a skill with no question type. */
@Composable
private fun TypeSwitch(
    title: String,
    description: String,
    checked: Boolean,
    flipped: QuestionTypes,
    enabled: Boolean = true,
    onChange: (QuestionTypes) -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(
            checked = checked,
            enabled = enabled && flipped.isValid,
            onCheckedChange = { onChange(flipped) },
            colors = inkSwitchColors(),
        )
    }
}

@Composable
private fun inkSwitchColors(): SwitchColors {
    val ink = LocalInkColors.current
    return SwitchDefaults.colors(
        checkedTrackColor = ink.ink,
        checkedThumbColor = ink.sheet,
        uncheckedTrackColor = ink.sheet,
        uncheckedBorderColor = ink.gridLine,
        uncheckedThumbColor = ink.pencil,
    )
}

/**
 * The daily reminder: an on/off switch and the time. Turning it on asks for the notification permission on Android 13+;
 * when notifications are blocked, it says so and links to the system settings instead of failing silently.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderChoice(settings: ReminderSettings, onChange: (ReminderSettings) -> Unit) {
    val context = LocalContext.current
    val ink = LocalInkColors.current
    var canNotify by remember { mutableStateOf(ReviewReminder.canNotify(context)) }
    var denied by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }
    // The user may come back from the system settings with notifications allowed (or blocked).
    LifecycleResumeEffect(Unit) {
        canNotify = ReviewReminder.canNotify(context)
        onPauseOrDispose { }
    }
    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        canNotify = ReviewReminder.canNotify(context)
        denied = !granted
        if (granted) onChange(settings.copy(enabled = true))
    }
    val is24Hour = DateFormat.is24HourFormat(context)

    Text(
        stringResource(R.string.reminder_hint),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(4.dp))
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.reminder_switch), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(
            checked = settings.enabled,
            onCheckedChange = { on ->
                val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                if (on && needsPermission) {
                    askPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    onChange(settings.copy(enabled = on))
                }
            },
            colors = inkSwitchColors(),
        )
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(R.string.reminder_time),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
            color = if (settings.enabled) Color.Unspecified else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = { pickTime = true }, enabled = settings.enabled) {
            Text(formatTime(settings.time, is24Hour), style = MaterialTheme.typography.titleMedium, color = if (settings.enabled) ink.ink else ink.pencil)
        }
    }
    if ((settings.enabled || denied) && !canNotify) {
        Spacer(Modifier.height(4.dp))
        Text(stringResource(R.string.reminder_blocked), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(8.dp))
        InkButton(stringResource(R.string.open_notification_settings), {
            context.startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
            )
        }, outlined = true, seed = 11)
    }

    if (pickTime) {
        val state = rememberTimePickerState(settings.time.hour, settings.time.minute, is24Hour)
        AlertDialog(
            onDismissRequest = { pickTime = false },
            containerColor = ink.sheet,
            shape = RoundedCornerShape(topStart = 4.dp, topEnd = 14.dp, bottomEnd = 6.dp, bottomStart = 12.dp),
            title = { Text(stringResource(R.string.reminder_time)) },
            text = {
                TimePicker(
                    state,
                    colors = TimePickerDefaults.colors(
                        selectorColor = ink.ink,
                        clockDialSelectedContentColor = ink.sheet,
                        timeSelectorSelectedContainerColor = ink.inkWash,
                        periodSelectorSelectedContainerColor = ink.inkWash,
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pickTime = false
                    onChange(settings.copy(time = LocalTime.of(state.hour, state.minute)))
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { pickTime = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

/** The reminder time in the user's 12/24-hour format and locale. */
@Composable
private fun formatTime(time: LocalTime, is24Hour: Boolean): String {
    val locale = LocalConfiguration.current.locales[0]
    val pattern = DateFormat.getBestDateTimePattern(locale, if (is24Hour) "Hm" else "hm")
    return time.format(DateTimeFormatter.ofPattern(pattern, locale))
}
