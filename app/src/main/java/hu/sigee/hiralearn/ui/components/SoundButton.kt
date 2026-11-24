package hu.sigee.hiralearn.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import hu.sigee.hiralearn.R
import hu.sigee.hiralearn.appContainer
import hu.sigee.hiralearn.audio.KanaVoice
import hu.sigee.hiralearn.core.Kana
import hu.sigee.hiralearn.ui.ink.inkWash
import hu.sigee.hiralearn.ui.theme.LocalInkColors

/**
 * Says [kana] aloud: an indigo ink blot with a speaker. Shows nothing when the phone has no Japanese voice.
 * With [autoPlay] it speaks once as soon as it appears (listening questions, new kana).
 */
@Composable
fun SoundButton(kana: Kana, modifier: Modifier = Modifier, size: Dp = 56.dp, autoPlay: Boolean = false, seed: Int = 17) {
    SoundButton(kana.char, kana.id, modifier, size, autoPlay, seed)
}

/** Says [text] (a kana or a word in kana) aloud, like the kana version; [utteranceId] names it for the engine. */
@Composable
fun SoundButton(text: String, utteranceId: String, modifier: Modifier = Modifier, size: Dp = 56.dp, autoPlay: Boolean = false, seed: Int = 17) {
    val voice = LocalContext.current.appContainer.voice
    val status by voice.status.collectAsStateWithLifecycle()
    if (autoPlay) {
        LaunchedEffect(text, status) { if (status == KanaVoice.Status.READY) voice.speak(text, utteranceId) }
    }
    if (status != KanaVoice.Status.READY) return
    val ink = LocalInkColors.current
    Box(
        modifier
            .size(size)
            .inkWash(ink.seal.copy(alpha = 0.16f), seed)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClickLabel = stringResource(R.string.play_sound)) { voice.speak(text, utteranceId) },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painterResource(R.drawable.ic_volume),
            contentDescription = stringResource(R.string.play_sound),
            tint = ink.seal,
            modifier = Modifier.padding(size / 4),
        )
    }
}
