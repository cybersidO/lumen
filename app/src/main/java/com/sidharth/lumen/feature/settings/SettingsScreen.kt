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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
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
                hint = stringResource(R.string.gesture_brightness_hint),
                checked = settings.gestures.brightnessDragEnabled,
                onChange = { v -> viewModel.updateGestures { it.copy(brightnessDragEnabled = v) } },
            )
            GestureToggleRow(
                label = stringResource(R.string.gesture_font_size_toggle),
                hint = stringResource(R.string.gesture_font_size_hint),
                checked = settings.gestures.fontSizeDragEnabled,
                onChange = { v -> viewModel.updateGestures { it.copy(fontSizeDragEnabled = v) } },
            )
            GestureToggleRow(
                label = stringResource(R.string.gesture_tap_turn_toggle),
                hint = stringResource(R.string.gesture_tap_turn_hint),
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

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier.padding(top = 8.dp, bottom = 8.dp),
            )
            DiagnosticsSection(viewModel)

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

/**
 * What Lumen knows about its own crashes — which is only what happened on this phone.
 *
 * There is no analytics and no crash reporting, by design, so without this a crash would
 * be invisible to the one person who could do anything about it. The report is read on
 * demand rather than observed: a file written by a dying process has no change
 * notification to subscribe to.
 */
@Composable
private fun DiagnosticsSection(viewModel: SettingsViewModel) {
    val report by viewModel.crashReport.collectAsStateWithLifecycle()
    var showReport by remember { mutableStateOf(false) }
    var copied by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current

    LaunchedEffect(Unit) { viewModel.refreshCrashReport() }

    SectionHeader(R.string.settings_diagnostics)
    Text(
        text = stringResource(R.string.settings_diagnostics_desc),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 8.dp),
    )
    Text(
        text = if (report.count == 0) {
            stringResource(R.string.diagnostics_no_crashes)
        } else {
            stringResource(R.string.diagnostics_crashes, report.count)
        },
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(horizontal = 24.dp),
    )
    if (report.count > 0) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            TextButton(onClick = { showReport = true }) {
                Text(stringResource(R.string.diagnostics_view))
            }
            TextButton(
                onClick = {
                    report.text?.let { clipboard.setText(AnnotatedString(it)) }
                    copied = true
                },
            ) {
                Text(stringResource(R.string.diagnostics_copy))
            }
            TextButton(onClick = viewModel::clearCrashReport) {
                Text(stringResource(R.string.diagnostics_clear))
            }
        }
        if (copied) {
            Text(
                text = stringResource(R.string.diagnostics_copied),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
        }
    }

    if (showReport) {
        AlertDialog(
            onDismissRequest = { showReport = false },
            title = { Text(stringResource(R.string.diagnostics_title)) },
            text = {
                Text(
                    text = report.text.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    // A stack trace is wide and long; let it scroll rather than reflow
                    // into something unreadable.
                    modifier = Modifier
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState())
                        .horizontalScroll(rememberScrollState()),
                    softWrap = false,
                )
            },
            confirmButton = {
                TextButton(onClick = { showReport = false }) {
                    Text(stringResource(R.string.diagnostics_close))
                }
            },
        )
    }
}
