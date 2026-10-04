package com.sidharth.lumen

import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sidharth.lumen.core.settings.ThemeMode
import com.sidharth.lumen.feature.stats.HomeSettingsButtonTag
import com.sidharth.lumen.feature.settings.themeOptionTag
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * M0's "done when", automated: pick each theme, confirm it sticks. Colour assertions
 * live in the screenshots; what is worth automating here is that the choice is applied
 * and read back rather than dropped.
 */
@RunWith(AndroidJUnit4::class)
class ThemeSwitchingTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun everyThemeCanBeSelected() {
        composeRule.onNodeWithTag(HomeSettingsButtonTag).performClick()

        listOf(
            ThemeMode.LIGHT,
            ThemeMode.DARK,
            ThemeMode.AMOLED,
            ThemeMode.SEPIA,
            ThemeMode.SYSTEM,
        ).forEach { mode ->
            composeRule.onNodeWithTag(themeOptionTag(mode)).performClick()
            // The choice travels through DataStore and back as a Flow, so `waitForIdle`
            // alone can return before the new value has been recomposed. Wait for the
            // state the click is supposed to produce rather than assuming it has landed.
            composeRule.awaitSelected(themeOptionTag(mode))
        }
    }

    @Test
    fun themeSurvivesActivityRecreation() {
        composeRule.onNodeWithTag(HomeSettingsButtonTag).performClick()
        composeRule.onNodeWithTag(themeOptionTag(ThemeMode.SEPIA)).performClick()
        composeRule.awaitSelected(themeOptionTag(ThemeMode.SEPIA))

        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()

        // Recreation restores the nav back stack, so we are still on Settings — the
        // choice has to be read back from DataStore, not from surviving UI state.
        composeRule.awaitSelected(themeOptionTag(ThemeMode.SEPIA))
        composeRule.onNodeWithTag(themeOptionTag(ThemeMode.DARK)).assertIsNotSelected()
    }
}

/** Waits for the tagged node to report itself selected, then asserts it. */
private fun androidx.compose.ui.test.junit4.ComposeTestRule.awaitSelected(tag: String) {
    waitUntil(timeoutMillis = 5_000) {
        onAllNodes(
            androidx.compose.ui.test.hasTestTag(tag) and
                androidx.compose.ui.test.isSelected(),
        ).fetchSemanticsNodes().isNotEmpty()
    }
    onNodeWithTag(tag).assertIsSelected()
}
