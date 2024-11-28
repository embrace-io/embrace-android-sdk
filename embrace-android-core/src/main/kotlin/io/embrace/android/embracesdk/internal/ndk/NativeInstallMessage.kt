package io.embrace.android.embracesdk.internal.ndk

data class NativeInstallMessage(
    val markerFilePath: String,
    val appState: String,
    val reportId: String,
    val apiLevel: Int,
    val is32bit: Boolean,
    val devLogging: Boolean,
)
