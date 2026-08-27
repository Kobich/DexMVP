package com.engboost.dexmvp

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.engboost.dexmvp.remoteexecution.ui.RemoteComposeActivityContract
import com.engboost.dexmvp.remoteexecution.ui.RemoteComposeErrorBoundary
import com.engboost.dexmvp.remoteexecution.ui.RemoteComposeSession
import com.engboost.dexmvp.remoteexecution.ui.RemoteComposeSessionStore
import com.engboost.dexmvp.ui.theme.DexMVPTheme

class RemoteComposeActivity : ComponentActivity() {
    private var sessionId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sessionId = intent.getStringExtra(RemoteComposeActivityContract.EXTRA_SESSION_ID)
        val session = sessionId?.let(RemoteComposeSessionStore::get)
        if (session == null) {
            finishWithError("Remote Compose session is missing")
            return
        }
        this.sessionId = sessionId

        enableEdgeToEdge()
        setContent {
            DexMVPTheme {
                RemoteComposeScreen(
                    sessionId = sessionId,
                    session = session,
                    onBack = ::finish,
                    onError = { error ->
                        finishWithError(error.message ?: error.toString())
                    },
                )
            }
        }
    }

    override fun onDestroy() {
        if (isFinishing) {
            sessionId?.let(RemoteComposeSessionStore::discard)
        }
        super.onDestroy()
    }

    private fun finishWithError(message: String) {
        if (isFinishing) {
            return
        }

        setResult(
            RESULT_OK,
            Intent().putExtra(RemoteComposeActivityContract.EXTRA_ERROR_MESSAGE, message),
        )
        finish()
    }
}

@Composable
private fun RemoteComposeScreen(
    sessionId: String,
    session: RemoteComposeSession,
    onBack: () -> Unit,
    onError: (Throwable) -> Unit,
) {
    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = session.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                OutlinedButton(onClick = onBack) {
                    Text("Back")
                }
            }

            RemoteComposeErrorBoundary(
                resetKey = sessionId,
                onError = onError,
            ) {
                session.feature.Content(input = session.input, host = session.host)
            }
        }
    }
}
