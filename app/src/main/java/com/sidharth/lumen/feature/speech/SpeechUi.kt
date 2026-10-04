package com.sidharth.lumen.feature.speech

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sidharth.lumen.R
import com.sidharth.lumen.core.settings.SpeechSettings
import kotlin.math.roundToInt

const val SpeechBarTag = "speech_bar"
const val SpeechPlayPauseTag = "speech_play_pause"
const val SpeechSheetTag = "speech_sheet"
const val SpeechVoiceListTag = "speech_voice_list"

/**
 * The bar that sits over the page while a book is being read aloud: what is being spoken,
 * previous / play-pause / next, the sleep timer's remaining minutes, settings and stop.
 */
@Composable
fun SpeechBar(
    state: SpeechUiState,
    onToggle: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onOpenSettings: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val active = state as? SpeechUiState.Active ?: return
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .testTag(SpeechBarTag),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 3.dp,
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            active.utterance?.takeIf { it.isNotBlank() }?.let { line ->
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onPrevious) {
                    Icon(Icons.Outlined.SkipPrevious, stringResource(R.string.speech_previous))
                }
                FilledIconButton(
                    onClick = onToggle,
                    modifier = Modifier.testTag(SpeechPlayPauseTag),
                ) {
                    Icon(
                        imageVector = if (active.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = stringResource(
                            if (active.playing) R.string.speech_pause else R.string.speech_play,
                        ),
                    )
                }
                IconButton(onClick = onNext) {
                    Icon(Icons.Outlined.SkipNext, stringResource(R.string.speech_next))
                }
                Spacer(Modifier.weight(1f))
                active.minutesLeftOnTimer?.let { left ->
                    Text(
                        text = stringResource(R.string.speech_timer_left, left),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Outlined.Tune, stringResource(R.string.speech_settings))
                }
                IconButton(onClick = onStop) {
                    Icon(Icons.Outlined.Close, stringResource(R.string.speech_stop))
                }
            }
        }
    }
}

/** Voice, speed, pitch and the sleep timer. */
@Composable
fun SpeechSheet(
    settings: SpeechSettings,
    voices: List<SpeechVoice>,
    onChange: (SpeechSettings) -> Unit,
    onOpenSystemTtsSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 24.dp, end = 24.dp, bottom = 32.dp)
            .testTag(SpeechSheetTag),
    ) {
        SectionLabel(stringResource(R.string.speech_speed))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Slider(
                value = settings.speed,
                onValueChange = { onChange(settings.copy(speed = it)) },
                valueRange = SpeechSettings.MIN_SPEED..SpeechSettings.MAX_SPEED,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = stringResource(R.string.speech_multiplier, settings.speed),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 12.dp),
            )
        }

        SectionLabel(stringResource(R.string.speech_pitch))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Slider(
                value = settings.pitch,
                onValueChange = { onChange(settings.copy(pitch = it)) },
                valueRange = SpeechSettings.MIN_PITCH..SpeechSettings.MAX_PITCH,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = stringResource(R.string.speech_multiplier, settings.pitch),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 12.dp),
            )
        }

        SectionLabel(stringResource(R.string.speech_sleep_timer))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        ) {
            SpeechSettings.SLEEP_PRESETS.forEach { minutes ->
                FilterChip(
                    selected = settings.sleepTimerMinutes == minutes,
                    onClick = { onChange(settings.copy(sleepTimerMinutes = minutes)) },
                    label = {
                        Text(
                            if (minutes == 0) stringResource(R.string.speech_sleep_off)
                            else stringResource(R.string.speech_sleep_minutes, minutes),
                        )
                    },
                )
            }
        }

        SectionLabel(stringResource(R.string.speech_voice))
        // The voices are the phone's, not Lumen's, so the honest fix for a poor voice is
        // to install a better engine — which this app then picks up for free.
        Text(
            text = stringResource(R.string.speech_engine_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = onOpenSystemTtsSettings) {
            Text(stringResource(R.string.speech_open_system_settings))
        }
        if (voices.isEmpty()) {
            // Honest, and actionable: this is what an empty list actually means.
            Text(
                text = stringResource(R.string.speech_no_voices),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(
                text = stringResource(R.string.speech_voice_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            val forBook = voices.filter { it.matchesBook }
            val others = voices.filterNot { it.matchesBook }
            var showAll by remember { mutableStateOf(forBook.isEmpty()) }
            val shown = if (showAll) voices else forBook
            if (others.isNotEmpty()) {
                TextButton(onClick = { showAll = !showAll }) {
                    Text(
                        stringResource(
                            if (showAll) R.string.speech_voices_book_only else R.string.speech_voices_all,
                            others.size,
                        ),
                    )
                }
            }
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 260.dp).testTag(SpeechVoiceListTag),
            ) {
                items(shown, key = { it.id }) { voice ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = settings.voiceId == voice.id,
                                onClick = { onChange(settings.copy(voiceId = voice.id)) },
                            )
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = settings.voiceId == voice.id, onClick = null)
                        Column(Modifier.padding(start = 8.dp)) {
                            Text(
                                text = voice.label,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            // Only when it adds something: the label already leads with
                            // the language for voices of the book's own tongue.
                            voice.languageTag
                                ?.takeUnless { voice.label.startsWith(it) }
                                ?.let {
                                    Text(
                                        text = it,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
    )
}
