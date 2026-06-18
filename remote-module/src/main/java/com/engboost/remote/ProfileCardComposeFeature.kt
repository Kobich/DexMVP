package com.engboost.remote

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.engboost.remoteapi.RemoteComposeFeature
import com.engboost.remoteapi.RemoteHost
import com.engboost.remoteapi.RemoteInput

class ProfileCardComposeFeature : RemoteComposeFeature {
    override val id: String = "profile-compose"
    override val version: Int = 1

    @Composable
    override fun Content(input: RemoteInput, host: RemoteHost) {
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("Remote Profile", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text("Alex Kononov", style = MaterialTheme.typography.headlineSmall)
                Text("Android engineer. Loaded from remote APK.", style = MaterialTheme.typography.bodyMedium)
                Text("Host input: ${input.text}", style = MaterialTheme.typography.bodySmall)
                LinearProgressIndicator(
                    progress = { 0.72f },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Prototype readiness: 72%", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

