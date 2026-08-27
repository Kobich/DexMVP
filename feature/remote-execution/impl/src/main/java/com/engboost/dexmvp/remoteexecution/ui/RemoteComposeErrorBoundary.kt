package com.engboost.dexmvp.remoteexecution.ui

import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.layout.SubcomposeLayout
import java.util.concurrent.CancellationException

@Composable
fun RemoteComposeErrorBoundary(
    resetKey: Any?,
    onError: (Throwable) -> Unit,
    content: @Composable () -> Unit,
) {
    val currentOnError by rememberUpdatedState(onError)
    val failureGate = remember(resetKey) { FailureGate() }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    fun reportFailure(throwable: Throwable) {
        throwable.rethrowIfFatal()
        if (failureGate.close()) {
            Log.e(LogTag, "Remote Compose failed", throwable)
            mainHandler.post {
                currentOnError(throwable)
            }
        }
    }

    SubcomposeLayout { constraints ->
        val placeables = try {
            subcompose(ContentSlot, content).map { measurable ->
                measurable.measure(constraints)
            }
        } catch (throwable: Throwable) {
            reportFailure(throwable)
            emptyList()
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
                reportFailure(throwable)
            }
        }
    }
}

private object ContentSlot

private const val LogTag = "RemoteComposeBoundary"

private class FailureGate {
    private var closed = false

    fun close(): Boolean {
        if (closed) {
            return false
        }
        closed = true
        return true
    }
}

private fun Throwable.rethrowIfFatal() {
    if (this is CancellationException || this is ThreadDeath || this is VirtualMachineError) {
        throw this
    }
}

private fun Int.saturatingAdd(value: Int): Int =
    (toLong() + value).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
