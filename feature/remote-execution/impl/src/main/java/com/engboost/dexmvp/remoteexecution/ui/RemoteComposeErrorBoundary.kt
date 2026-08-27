package com.engboost.dexmvp.remoteexecution.ui

import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import java.util.concurrent.CancellationException

@Composable
internal fun RemoteComposeErrorBoundary(
    errorKey: Any?,
    onError: (Throwable) -> Unit,
    fallback: @Composable (Throwable) -> Unit,
    content: @Composable () -> Unit,
) {
    var capturedError by remember(errorKey) { mutableStateOf<Throwable?>(null) }
    val currentOnError by rememberUpdatedState(onError)

    val currentError = capturedError
    if (currentError != null) {
        fallback(currentError)
        return
    }

    SubcomposeLayout { constraints ->
        val placeables = try {
            subcompose("remote-content", content).map { measurable ->
                measurable.measure(constraints)
            }
        } catch (throwable: Throwable) {
            if (throwable.isFatalForRemoteComposeBoundary()) {
                throw throwable
            }

            capturedError = throwable
            currentOnError(throwable)
            subcompose("remote-fallback") {
                fallback(throwable)
            }.map { measurable ->
                measurable.measure(constraints)
            }
        }

        val width = placeables.maxOfOrNull { it.width } ?: constraints.minWidth
        val height = placeables.sumOf { it.height }.coerceAtLeast(constraints.minHeight)

        layout(width, height) {
            var y = 0
            placeables.forEach { placeable ->
                placeable.placeRelative(0, y)
                y += placeable.height
            }
        }
    }
}

private fun Throwable.isFatalForRemoteComposeBoundary(): Boolean {
    return this is CancellationException ||
        this is ThreadDeath ||
        this is VirtualMachineError
}
