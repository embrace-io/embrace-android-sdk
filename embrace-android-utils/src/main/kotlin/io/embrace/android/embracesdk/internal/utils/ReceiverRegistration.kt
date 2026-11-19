package io.embrace.android.embracesdk.internal.utils

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.os.Build

/**
 * Registers [receiver] so that only the system and this app can send it broadcasts. Flags are only supported from
 * API 33; older versions fall back to the unflagged overload.
 */
@SuppressLint("UnspecifiedRegisterReceiverFlag")
fun Context.registerNotExportedReceiver(
    receiver: BroadcastReceiver,
    filter: IntentFilter,
    versionChecker: VersionChecker = BuildVersionChecker,
) {
    if (versionChecker.isAtLeast(Build.VERSION_CODES.TIRAMISU)) {
        registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
    } else {
        registerReceiver(receiver, filter)
    }
}
