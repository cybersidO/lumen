package com.sidharth.lumen.feature.library

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sidharth.lumen.core.books.BookFormat
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * §2.1 is specific about this dialog: it must ask, and the default must be to keep.
 * Tested against the composable directly — no ViewModel, no database — because the
 * question here is purely what the dialog offers and what it returns.
 */
@RunWith(AndroidJUnit4::class)
class DeleteBookDialogTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun book(title: String = "Middlemarch") = Book(
        id = 1,
        contentHash = "hash",
        title = title,
        author = "George Eliot",
        format = BookFormat.EPUB,
        filePath = "/books/hash.epub",
        coverPath = null,
        addedAt = 0,
        lastOpenedAt = null,
        progressFraction = 0f,
    )

    @Test
    fun keepingHighlightsIsTheDefault() {
        var confirmed: HighlightDisposition? = null
        composeRule.setContent {
            DeleteBookDialog(
                pending = PendingDeletion(book(), highlightCount = 3),
                onDismiss = {},
                onConfirm = { confirmed = it },
            )
        }

        composeRule.onNodeWithTag(DeleteDialogKeepTag).assertIsSelected()
        composeRule.onNodeWithTag(DeleteDialogConfirmTag).performClick()

        assertEquals(HighlightDisposition.KEEP, confirmed)
    }

    @Test
    fun theUserCanChooseToDeleteHighlightsToo() {
        var confirmed: HighlightDisposition? = null
        composeRule.setContent {
            DeleteBookDialog(
                pending = PendingDeletion(book(), highlightCount = 3),
                onDismiss = {},
                onConfirm = { confirmed = it },
            )
        }

        composeRule.onNodeWithTag(DeleteDialogRemoveTag).performClick()
        composeRule.onNodeWithTag(DeleteDialogRemoveTag).assertIsSelected()
        composeRule.onNodeWithTag(DeleteDialogConfirmTag).performClick()

        assertEquals(HighlightDisposition.DELETE, confirmed)
    }

    @Test
    fun theHighlightCountIsShown() {
        composeRule.setContent {
            DeleteBookDialog(
                pending = PendingDeletion(book(), highlightCount = 3),
                onDismiss = {},
                onConfirm = {},
            )
        }

        composeRule.onNodeWithText("This book has 3 highlights. What should happen to them?")
            .assertIsDisplayed()
    }

    /** No highlights, no question — just a confirmation. */
    @Test
    fun aBookWithNoHighlightsIsNotAskedAbout() {
        var confirmed: HighlightDisposition? = null
        composeRule.setContent {
            DeleteBookDialog(
                pending = PendingDeletion(book(), highlightCount = 0),
                onDismiss = {},
                onConfirm = { confirmed = it },
            )
        }

        composeRule.onNodeWithTag(DeleteDialogKeepTag).assertDoesNotExist()
        composeRule.onNodeWithTag(DeleteDialogConfirmTag).performClick()

        assertEquals(HighlightDisposition.KEEP, confirmed)
    }

    @Test
    fun cancellingConfirmsNothing() {
        var confirmed: HighlightDisposition? = null
        var dismissed = false
        composeRule.setContent {
            DeleteBookDialog(
                pending = PendingDeletion(book(), highlightCount = 2),
                onDismiss = { dismissed = true },
                onConfirm = { confirmed = it },
            )
        }

        composeRule.onNodeWithText("Cancel").performClick()

        assertEquals(true, dismissed)
        assertEquals(null, confirmed)
    }
}
