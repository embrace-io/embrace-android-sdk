package io.embrace.android.embracesdk.internal.instrumentation.crash

import io.embrace.android.embracesdk.internal.instrumentation.crash.jvm.CrashDataSource

/**
 * Contains dependencies that capture crashes
 */
interface CrashModule {
    val lastRunCrashVerifier: LastRunCrashVerifier
    val crashDataSource: CrashDataSource
}
