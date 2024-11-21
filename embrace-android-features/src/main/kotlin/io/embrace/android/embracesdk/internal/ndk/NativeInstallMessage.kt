package io.embrace.android.embracesdk.internal.ndk

data class NativeInstallMessage(
    val reportPath: String,
    val markerFilePath: String,
    val sessionId: String,
    val appState: String,
    val reportId: String,
    val apiLevel: Int,
    val is32bit: Boolean,
    val devLogging: Boolean,
)
