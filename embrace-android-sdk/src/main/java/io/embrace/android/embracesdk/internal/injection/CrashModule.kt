package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.internal.capture.crash.CrashService
import io.embrace.android.embracesdk.internal.crash.LastRunCrashVerifier
import io.embrace.android.embracesdk.ndk.NativeCrashService
import io.embrace.android.embracesdk.samples.AutomaticVerificationExceptionHandler

/**
 * Contains dependencies that capture crashes
 */
internal interface CrashModule {
    val lastRunCrashVerifier: LastRunCrashVerifier
    val crashService: CrashService
    val automaticVerificationExceptionHandler: AutomaticVerificationExceptionHandler
    val nativeCrashService: NativeCrashService
}
