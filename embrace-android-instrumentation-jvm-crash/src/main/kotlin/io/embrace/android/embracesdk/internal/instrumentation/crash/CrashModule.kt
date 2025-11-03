package io.embrace.android.embracesdk.internal.instrumentation.crash

/**
 * Contains dependencies that capture crashes
 */
interface CrashModule {
    val lastRunCrashVerifier: LastRunCrashVerifier
    val crashDataSource: CrashDataSource
}
