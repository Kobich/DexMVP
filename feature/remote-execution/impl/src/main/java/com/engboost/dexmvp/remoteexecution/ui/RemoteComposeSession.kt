package com.engboost.dexmvp.remoteexecution.ui

import android.content.Context
import android.content.Intent
import com.engboost.remoteapi.RemoteComposeFeature
import com.engboost.remoteapi.RemoteHost
import com.engboost.remoteapi.RemoteInput
import java.util.UUID

class RemoteComposeSession internal constructor(
    val title: String,
    val feature: RemoteComposeFeature,
    val input: RemoteInput,
    val host: RemoteHost,
)

object RemoteComposeSessionStore {
    private val sessions = mutableMapOf<String, RemoteComposeSession>()

    @Synchronized
    fun create(
        title: String,
        feature: RemoteComposeFeature,
        input: RemoteInput,
        host: RemoteHost,
    ): String {
        val sessionId = UUID.randomUUID().toString()
        sessions[sessionId] = RemoteComposeSession(title, feature, input, host)
        return sessionId
    }

    @Synchronized
    fun get(sessionId: String): RemoteComposeSession? = sessions[sessionId]

    @Synchronized
    fun discard(sessionId: String) {
        sessions.remove(sessionId)
    }
}

object RemoteComposeActivityContract {
    const val EXTRA_SESSION_ID = "remote_compose_session_id"
    const val EXTRA_ERROR_MESSAGE = "remote_compose_error_message"

    private const val ACTIVITY_CLASS_NAME = "com.engboost.dexmvp.RemoteComposeActivity"

    fun createIntent(context: Context, sessionId: String): Intent {
        return Intent()
            .setClassName(context.packageName, ACTIVITY_CLASS_NAME)
            .putExtra(EXTRA_SESSION_ID, sessionId)
    }
}
