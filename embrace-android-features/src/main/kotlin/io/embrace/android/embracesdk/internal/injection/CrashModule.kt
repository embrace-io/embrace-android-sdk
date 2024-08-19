package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.internal.capture.crash.CrashDataSource
import io.embrace.android.embracesdk.internal.crash.LastRunCrashVerifier

/**
 * Contains dependencies that capture crashes
 */
public interface CrashModule {
    public val lastRunCrashVerifier: LastRunCrashVerifier
    public val crashDataSource: CrashDataSource
}
