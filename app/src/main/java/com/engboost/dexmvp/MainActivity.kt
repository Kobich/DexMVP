package com.engboost.dexmvp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.engboost.dexmvp.remoteexecution.ui.RemoteExecutionDemoScreen
import com.engboost.dexmvp.ui.theme.DexMVPTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DexMVPTheme {
                RemoteExecutionDemoScreen()
            }
        }
    }
}

