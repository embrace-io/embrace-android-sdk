package io.embrace.android.embracesdk.internal.instrumentation.startup

import android.os.Build.VERSION_CODES
import android.os.Process
import io.embrace.android.embracesdk.internal.utils.VersionChecker

internal class ProcessInfoImpl(
    private val deviceStartTimeMs: Long,
    private val versionChecker: VersionChecker,
) : ProcessInfo {
    override fun startRequestedTimeMs(): Long? {
        return if (versionChecker.isAtLeast(VERSION_CODES.TIRAMISU)) {
            deviceStartTimeMs + Process.getStartRequestedElapsedRealtime()
        } else if (versionChecker.isAtLeast(VERSION_CODES.N)) {
            deviceStartTimeMs + Process.getStartElapsedRealtime()
        } else {
            null
        }
    }
}
