package com.sidharth.lumen.feature.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import androidx.compose.ui.platform.LocalContext
import com.sidharth.lumen.R
import java.io.File
import kotlin.math.absoluteValue

const val BookCardTagPrefix = "book_card_"

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BookCard(
    book: Book,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val author = book.author ?: stringResource(R.string.library_unknown_author)
    val progressLabel = stringResource(R.string.library_progress, book.progressPercent)

    Column(
        modifier = modifier
            .testTag(BookCardTagPrefix + book.id)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .semantics {
                contentDescription = buildString {
                    append(book.title)
                    append(", ")
                    append(author)
                    if (book.progressPercent > 0) {
                        append(", ")
                        append(progressLabel)
                    }
                }
            },
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatioCover()
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            CoverArt(book = book, author = author)

            if (book.progressFraction > 0f) {
                LinearProgressIndicator(
                    progress = { book.progressFraction },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
            }
        }

        Text(
            text = book.title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            text = author,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun CoverArt(book: Book, author: String) {
    val coverFile = remember(book.coverPath) { book.coverPath?.let(::File)?.takeIf(File::exists) }

    // Coil can still fail on a truncated PNG, so the generated card is also the error
    // state, not just the no-cover state.
    var failed by remember(book.coverPath) { mutableStateOf(false) }

    if (coverFile == null || failed) {
        GeneratedCover(title = book.title, author = author)
        return
    }

    val context = LocalContext.current
    AsyncImage(
        model = remember(coverFile) {
            ImageRequest.Builder(context).data(coverFile).build()
        },
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize(),
        onError = { failed = true },
    )
}

/**
 * The fallback for EPUBs with no cover image (§2.1). Colour is derived from the title, so
 * a given book always gets the same card and a shelf of them stays distinguishable.
 */
@Composable
private fun GeneratedCover(title: String, author: String) {
    val palette = GeneratedCoverPalette
    val tint = palette[(title.hashCode().absoluteValue) % palette.size]

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(tint)
            .padding(12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                textAlign = TextAlign.Center,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 18.sp,
            )
            Text(
                text = author,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.75f),
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

/** Muted enough to sit under white text in every theme. */
private val GeneratedCoverPalette = listOf(
    Color(0xFF4A5C6A),
    Color(0xFF6A4A4A),
    Color(0xFF4A6A56),
    Color(0xFF5C4A6A),
    Color(0xFF6A5C3A),
    Color(0xFF3A5A6A),
)

/** Standard trade-paperback proportion; keeps rows even when covers vary. */
private fun Modifier.aspectRatioCover(): Modifier = aspectRatio(COVER_ASPECT_RATIO)

private const val COVER_ASPECT_RATIO = 2f / 3f
