package io.embrace.android.embracesdk.fakes.injection

import io.embrace.android.embracesdk.fakes.FakeCrashService
import io.embrace.android.embracesdk.injection.CrashModule
import io.embrace.android.embracesdk.internal.crash.CrashFileMarker
import io.embrace.android.embracesdk.internal.crash.LastRunCrashVerifier
import io.embrace.android.embracesdk.samples.AutomaticVerificationExceptionHandler
import io.mockk.mockk

internal class FakeCrashModule : CrashModule {
    override val lastRunCrashVerifier = LastRunCrashVerifier(CrashFileMarker(mockk(relaxed = true)))
    override val crashService = FakeCrashService()
    override val automaticVerificationExceptionHandler = AutomaticVerificationExceptionHandler(null)
}
