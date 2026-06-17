package com.engboost.remote

import com.engboost.remoteapi.RemoteFeature
import com.engboost.remoteapi.RemoteInput
import com.engboost.remoteapi.RemoteOutput

class HelloRemoteFeature : RemoteFeature {
    override val id: String = "hello"
    override val version: Int = 1

    override fun execute(input: RemoteInput): RemoteOutput {
        return RemoteOutput(
            title = "Hello from remote module",
            message = "Input: ${input.text}; timestamp: ${input.timestampMillis}",
        )
    }
}

