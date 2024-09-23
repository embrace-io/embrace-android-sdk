package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.internal.capture.crash.CrashDataSource
import io.embrace.android.embracesdk.internal.crash.LastRunCrashVerifier

/**
 * Contains dependencies that capture crashes
 */
interface CrashModule {
    val lastRunCrashVerifier: LastRunCrashVerifier
    val crashDataSource: CrashDataSource
}
