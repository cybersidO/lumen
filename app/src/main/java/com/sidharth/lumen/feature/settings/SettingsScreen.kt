package com.sidharth.lumen.feature.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sidharth.lumen.R
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.FilterChip
import com.sidharth.lumen.core.settings.GestureSettings
import com.sidharth.lumen.core.settings.ReadingSettings
import kotlin.math.roundToInt
import com.sidharth.lumen.core.settings.ResolvedTheme
import com.sidharth.lumen.core.settings.ThemeMode
import com.sidharth.lumen.ui.theme.colorSchemeFor

fun themeOptionTag(mode: ThemeMode): String = "theme_option_${mode.storageKey}"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.nav_back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
        ) {
            SectionHeader(R.string.settings_appearance)
            Text(
                text = stringResource(R.string.settings_theme_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 8.dp),
            )

            Column(Modifier.selectableGroup()) {
                ThemeOptions.forEach { option ->
                    ThemeRow(
                        option = option,
                        selected = settings.themeMode == option.mode,
                        onSelect = { viewModel.setThemeMode(option.mode) },
                    )
                }
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier.padding(top = 8.dp, bottom = 8.dp),
            )

            SectionHeader(R.string.settings_gestures)
            Text(
                text = stringResource(R.string.settings_gestures_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 12.dp),
            )

            GestureZonePreview(
                settings = settings.gestures,
                modifier = Modifier
                    .padding(horizontal = 48.dp)
                    .fillMaxWidth(),
            )

            GestureSliderRow(
                label = stringResource(R.string.gesture_edge_width),
                value = settings.gestures.edgeStripFraction,
                range = GestureSettings.MIN_EDGE_STRIP..GestureSettings.MAX_EDGE_STRIP,
                onChange = { v -> viewModel.updateGestures { it.copy(edgeStripFraction = v) } },
            )
            GestureSliderRow(
                label = stringResource(R.string.gesture_tap_width),
                value = settings.gestures.tapZoneFraction,
                range = GestureSettings.MIN_TAP_ZONE..GestureSettings.MAX_TAP_ZONE,
                onChange = { v -> viewModel.updateGestures { it.copy(tapZoneFraction = v) } },
            )

            GestureToggleRow(
                label = stringResource(R.string.gesture_brightness_toggle),
                checked = settings.gestures.brightnessDragEnabled,
                onChange = { v -> viewModel.updateGestures { it.copy(brightnessDragEnabled = v) } },
            )
            GestureToggleRow(
                label = stringResource(R.string.gesture_font_size_toggle),
                checked = settings.gestures.fontSizeDragEnabled,
                onChange = { v -> viewModel.updateGestures { it.copy(fontSizeDragEnabled = v) } },
            )
            GestureToggleRow(
                label = stringResource(R.string.gesture_tap_turn_toggle),
                checked = settings.gestures.tapToTurnPageEnabled,
                onChange = { v -> viewModel.updateGestures { it.copy(tapToTurnPageEnabled = v) } },
            )
            GestureToggleRow(
                label = stringResource(R.string.gesture_two_finger_toggle),
                hint = stringResource(R.string.gesture_two_finger_hint),
                checked = settings.gestures.twoFingerTapThemeEnabled,
                onChange = { v ->
                    viewModel.updateGestures { it.copy(twoFingerTapThemeEnabled = v) }
                },
            )

            SectionHeader(R.string.settings_reading)
            Text(
                text = stringResource(R.string.settings_reading_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 4.dp),
            )
            GestureToggleRow(
                label = stringResource(R.string.reading_volume_keys),
                hint = stringResource(R.string.reading_volume_keys_hint),
                checked = settings.reading.volumeButtonsTurnPages,
                onChange = { v -> viewModel.updateReading { it.copy(volumeButtonsTurnPages = v) } },
            )
            GestureToggleRow(
                label = stringResource(R.string.reading_haptics),
                hint = stringResource(R.string.reading_haptics_hint),
                checked = settings.reading.hapticsEnabled,
                onChange = { v -> viewModel.updateReading { it.copy(hapticsEnabled = v) } },
            )
            GestureToggleRow(
                label = stringResource(R.string.reading_keep_screen_on),
                hint = pluralStringResource(
                    R.plurals.reading_keep_screen_on_hint,
                    settings.reading.keepScreenOnMinutes,
                    settings.reading.keepScreenOnMinutes,
                ),
                checked = settings.reading.keepScreenOn,
                onChange = { v -> viewModel.updateReading { it.copy(keepScreenOn = v) } },
            )
            if (settings.reading.keepScreenOn) {
                GestureSliderRow(
                    label = stringResource(R.string.reading_keep_screen_on_minutes),
                    value = settings.reading.keepScreenOnMinutes.toFloat(),
                    range = 0f..ReadingSettings.MAX_KEEP_SCREEN_ON_MINUTES.toFloat(),
                    onChange = { v ->
                        viewModel.updateReading { it.copy(keepScreenOnMinutes = v.roundToInt()) }
                    },
                )
            }
            GestureToggleRow(
                label = stringResource(R.string.reading_lock_orientation),
                checked = settings.reading.lockOrientation,
                onChange = { v -> viewModel.updateReading { it.copy(lockOrientation = v) } },
            )
            GestureToggleRow(
                label = stringResource(R.string.reading_full_screen),
                hint = stringResource(R.string.reading_full_screen_hint),
                checked = settings.reading.fullScreen,
                onChange = { v -> viewModel.updateReading { it.copy(fullScreen = v) } },
            )
            SectionSubLabel(
                stringResource(R.string.reading_daily_goal),
                stringResource(R.string.reading_daily_goal_hint),
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 4.dp),
            ) {
                ReadingSettings.DAILY_GOAL_PRESETS.forEach { minutes ->
                    FilterChip(
                        selected = settings.reading.dailyGoalMinutes == minutes,
                        onClick = { viewModel.updateReading { it.copy(dailyGoalMinutes = minutes) } },
                        label = {
                            Text(
                                if (minutes == 0) stringResource(R.string.reading_goal_off)
                                else stringResource(R.string.speech_sleep_minutes, minutes),
                            )
                        },
                    )
                }
            }
            GestureToggleRow(
                label = stringResource(R.string.reading_chapter_time),
                hint = stringResource(R.string.reading_chapter_time_hint),
                checked = settings.reading.showChapterTimeLeft,
                onChange = { v -> viewModel.updateReading { it.copy(showChapterTimeLeft = v) } },
            )

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun GestureSliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
) {
    Column(Modifier.padding(horizontal = 24.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "${(value * 100).toInt()}%",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Slider(value = value, onValueChange = onChange, valueRange = range)
    }
}

@Composable
private fun GestureToggleRow(
    label: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    hint: String? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            if (hint != null) {
                Text(
                    text = hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun SectionSubLabel(title: String, hint: String) {
    Column(Modifier.padding(horizontal = 24.dp).padding(top = 12.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = hint,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SectionHeader(@StringRes title: Int) {
    Text(
        text = stringResource(title),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 4.dp),
    )
}

@Composable
private fun ThemeRow(
    option: ThemeOption,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    val label = stringResource(option.label)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = onSelect,
            )
            .testTag(themeOptionTag(option.mode))
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        ThemeSwatch(
            mode = option.mode,
            modifier = Modifier.padding(start = 12.dp),
        )
        Column(
            modifier = Modifier.padding(start = 16.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = stringResource(option.description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * A dot showing the palette the option will produce, so the choice can be made without
 * applying it. "Follow system" has no single colour, so it shows the one it resolves to
 * right now.
 */
@Composable
private fun ThemeSwatch(mode: ThemeMode, modifier: Modifier = Modifier) {
    val resolved: ResolvedTheme = mode.resolve(
        systemInDarkTheme = androidx.compose.foundation.isSystemInDarkTheme(),
    )
    val scheme = colorSchemeFor(resolved)
    Box(
        modifier = modifier
            .size(24.dp)
            .background(scheme.background, CircleShape)
            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(10.dp)
                .background(scheme.onBackground, RoundedCornerShape(2.dp)),
        )
    }
}

private data class ThemeOption(
    val mode: ThemeMode,
    @StringRes val label: Int,
    @StringRes val description: Int,
)

private val ThemeOptions = listOf(
    ThemeOption(ThemeMode.SYSTEM, R.string.theme_system, R.string.theme_system_desc),
    ThemeOption(ThemeMode.LIGHT, R.string.theme_light, R.string.theme_light_desc),
    ThemeOption(ThemeMode.DARK, R.string.theme_dark, R.string.theme_dark_desc),
    ThemeOption(ThemeMode.AMOLED, R.string.theme_amoled, R.string.theme_amoled_desc),
    ThemeOption(ThemeMode.SEPIA, R.string.theme_sepia, R.string.theme_sepia_desc),
)
