package com.engboost.dexmvp.remoteexecution.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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

    val currentError = capturedError
    if (currentError != null) {
        fallback(currentError)
        return
    }

    try {
        content()
    } catch (throwable: Throwable) {
        if (throwable.isFatalForRemoteComposeBoundary()) {
            throw throwable
        }

        SideEffect {
            capturedError = throwable
            onError(throwable)
        }
        fallback(throwable)
    }
}

private fun Throwable.isFatalForRemoteComposeBoundary(): Boolean {
    return this is CancellationException ||
        this is ThreadDeath ||
        this is VirtualMachineError
}
