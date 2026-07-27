package com.praval.f1calendar.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.praval.f1calendar.domain.model.Race
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.time.Instant
import kotlin.math.abs

/**
 * A spinning-drum picker over the season's rounds.
 *
 * Rows are rotated about the X axis in proportion to how far they sit from the centre of the
 * viewport, and faded and shrunk with them, which reads as a physical wheel turning rather than a
 * flat list scrolling. The transform is applied in a deferred [graphicsLayer] block so it is
 * recalculated at draw time on every scroll frame without recomposing the rows.
 *
 * Selection is reported continuously while the wheel is still moving, not only once it settles, so
 * the detail below tracks the spin.
 */
@Composable
fun RaceWheelPicker(
    races: List<Race>,
    selectedRound: Int?,
    now: Instant,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    visibleCount: Int = 5,
    itemHeight: Dp = 46.dp,
) {
    if (races.isEmpty()) return

    val listState = rememberLazyListState()
    val flingBehavior = rememberSnapFlingBehavior(lazyListState = listState)
    val itemHeightPx = with(LocalDensity.current) { itemHeight.toPx() }

    // Half a wheel of padding at each end lets the first and last rounds reach the centre.
    val edgePadding = itemHeight * (visibleCount / 2)
    val selectedIndex = remember(races, selectedRound) {
        races.indexOfFirst { it.round == selectedRound }
    }

    // Follow selections made elsewhere (a tapped alarm, a season change) without fighting the user
    // while they are actually dragging the wheel.
    LaunchedEffect(selectedIndex) {
        if (selectedIndex < 0 || listState.isScrollInProgress) return@LaunchedEffect
        val alreadyCentred = listState.firstVisibleItemIndex == selectedIndex &&
            listState.firstVisibleItemScrollOffset == 0
        if (!alreadyCentred) listState.scrollToItem(selectedIndex)
    }

    LaunchedEffect(listState, races) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            // The centred row is whichever one covers most of the middle slot.
            .map { (index, offset) -> if (offset > itemHeightPx / 2f) index + 1 else index }
            .map { it.coerceIn(0, races.lastIndex) }
            .distinctUntilChanged()
            .collect { index -> races.getOrNull(index)?.let { onSelect(it.round) } }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(itemHeight * visibleCount),
        contentAlignment = Alignment.Center,
    ) {
        // Static slot marking where the wheel comes to rest.
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .height(itemHeight)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
        )

        LazyColumn(
            state = listState,
            flingBehavior = flingBehavior,
            contentPadding = PaddingValues(vertical = edgePadding),
            modifier = Modifier.fillMaxWidth(),
        ) {
            itemsIndexed(races, key = { _, race -> race.round }) { index, race ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(itemHeight)
                        .graphicsLayer {
                            val info = listState.layoutInfo
                            val viewportCentre =
                                (info.viewportStartOffset + info.viewportEndOffset) / 2f
                            val item = info.visibleItemsInfo.firstOrNull { it.index == index }
                            val itemCentre = item
                                ?.let { it.offset + it.size / 2f }
                                ?: viewportCentre
                            val halfViewport =
                                (info.viewportEndOffset - info.viewportStartOffset) / 2f

                            val fraction = if (halfViewport <= 0f) {
                                0f
                            } else {
                                ((itemCentre - viewportCentre) / halfViewport).coerceIn(-1f, 1f)
                            }
                            val closeness = 1f - abs(fraction)

                            rotationX = -fraction * MAX_ROTATION_DEGREES
                            // Without a generous camera distance the outer rows shear instead of
                            // reading as a curved surface.
                            cameraDistance = CAMERA_DISTANCE_FACTOR * density
                            alpha = MIN_ALPHA + (1f - MIN_ALPHA) * closeness
                            scaleX = MIN_SCALE + (1f - MIN_SCALE) * closeness
                            scaleY = scaleX
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    RaceWheelRow(
                        race = race,
                        selected = race.round == selectedRound,
                        completed = race.isCompleted(now),
                    )
                }
            }
        }
    }
}

@Composable
private fun RaceWheelRow(
    race: Race,
    selected: Boolean,
    completed: Boolean,
) {
    val contentColour = when {
        selected -> MaterialTheme.colorScheme.onPrimaryContainer
        completed -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.onSurface
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = race.flag, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.width(12.dp))
        Text(
            text = "R${race.round}",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = if (selected) contentColour else MaterialTheme.colorScheme.primary,
            modifier = Modifier.width(34.dp),
        )
        Text(
            text = race.name,
            style = if (selected) {
                MaterialTheme.typography.titleSmall
            } else {
                MaterialTheme.typography.bodyMedium
            },
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = contentColour,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (completed) {
            Text(
                text = "✓",
                style = MaterialTheme.typography.labelMedium,
                color = contentColour,
            )
        }
    }
}

private const val MAX_ROTATION_DEGREES = 58f
private const val CAMERA_DISTANCE_FACTOR = 14f
private const val MIN_ALPHA = 0.22f
private const val MIN_SCALE = 0.80f
