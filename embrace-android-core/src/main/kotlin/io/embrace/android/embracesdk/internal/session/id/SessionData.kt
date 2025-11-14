package io.embrace.android.embracesdk.internal.session.id

import io.embrace.android.embracesdk.internal.session.lifecycle.AppState

data class SessionData(
    val id: String,
    val appState: AppState
)
