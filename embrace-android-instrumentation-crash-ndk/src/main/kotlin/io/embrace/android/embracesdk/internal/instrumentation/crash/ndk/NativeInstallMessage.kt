package io.embrace.android.embracesdk.internal.instrumentation.crash.ndk

import io.embrace.android.embracesdk.internal.arch.state.AppState

data class NativeInstallMessage(
    val markerFilePath: String,
    val appState: AppState,
    val reportId: String,
    val apiLevel: Int,
    val is32bit: Boolean,
    val devLogging: Boolean,
)
