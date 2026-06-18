package com.engboost.remote

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.engboost.remoteapi.RemoteComposeFeature
import com.engboost.remoteapi.RemoteEvent
import com.engboost.remoteapi.RemoteHost
import com.engboost.remoteapi.RemoteInput

class CounterComposeFeature : RemoteComposeFeature {
    override val id: String = "counter-compose"
    override val version: Int = 1

    @Composable
    override fun Content(input: RemoteInput, host: RemoteHost) {
        var count by remember { mutableIntStateOf(0) }

        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Remote Counter", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text("Input: ${input.text}", style = MaterialTheme.typography.bodyMedium)
                Text("Count: $count", style = MaterialTheme.typography.headlineMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            count += 1
                            host.emit(RemoteEvent(type = "counter", message = "Counter incremented to $count"))
                        },
                    ) {
                        Text("Increment")
                    }
                    OutlinedButton(
                        onClick = {
                            count = 0
                            host.emit(RemoteEvent(type = "counter", message = "Counter reset"))
                        },
                    ) {
                        Text("Reset")
                    }
                }
            }
        }
    }
}

