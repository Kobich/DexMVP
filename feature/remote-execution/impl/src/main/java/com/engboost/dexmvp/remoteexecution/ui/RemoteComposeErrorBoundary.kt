package com.engboost.dexmvp.remoteexecution.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.SubcomposeLayout
import java.util.concurrent.CancellationException

@Composable
internal fun RemoteComposeErrorBoundary(
    resetKey: Any?,
    onError: (Throwable) -> Unit,
    fallback: @Composable (Throwable) -> Unit,
    content: @Composable () -> Unit,
) {
    var capturedError by remember(resetKey) { mutableStateOf<Throwable?>(null) }
    val currentOnError by rememberUpdatedState(onError)

    val currentError = capturedError
    if (currentError != null) {
        LaunchedEffect(currentError) {
            currentOnError(currentError)
        }
        fallback(currentError)
        return
    }

    SubcomposeLayout { constraints ->
        val placeables = try {
            subcompose(ContentSlot, content).map { measurable ->
                measurable.measure(constraints)
            }
        } catch (throwable: Throwable) {
            throwable.rethrowIfFatal()
            capturedError = throwable
            subcompose(FallbackSlot) {
                fallback(throwable)
            }.map { measurable ->
                measurable.measure(constraints)
            }
        }

        val width = (placeables.maxOfOrNull { it.width } ?: 0)
            .coerceIn(constraints.minWidth, constraints.maxWidth)
        val height = placeables
            .fold(0) { totalHeight, placeable ->
                totalHeight.saturatingAdd(placeable.height)
            }
            .coerceIn(constraints.minHeight, constraints.maxHeight)

        layout(width, height) {
            var y = 0
            try {
                placeables.forEach { placeable ->
                    placeable.placeRelative(0, y)
                    y = y.saturatingAdd(placeable.height)
                }
            } catch (throwable: Throwable) {
                throwable.rethrowIfFatal()
                capturedError = throwable
            }
        }
    }
}

private object ContentSlot

private object FallbackSlot

private fun Throwable.rethrowIfFatal() {
    if (this is CancellationException || this is ThreadDeath || this is VirtualMachineError) {
        throw this
    }
}

private fun Int.saturatingAdd(value: Int): Int =
    (toLong() + value).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
