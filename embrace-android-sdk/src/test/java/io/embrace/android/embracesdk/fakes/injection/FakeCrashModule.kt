package io.embrace.android.embracesdk.fakes.injection

import io.embrace.android.embracesdk.fakes.FakeCrashService
import io.embrace.android.embracesdk.fakes.FakeNativeCrashService
import io.embrace.android.embracesdk.internal.crash.CrashFileMarkerImpl
import io.embrace.android.embracesdk.internal.crash.LastRunCrashVerifier
import io.embrace.android.embracesdk.internal.injection.CrashModule
import io.embrace.android.embracesdk.internal.ndk.NativeCrashService
import io.embrace.android.embracesdk.samples.AutomaticVerificationExceptionHandler
import io.mockk.mockk

internal class FakeCrashModule : CrashModule {
    override val lastRunCrashVerifier = LastRunCrashVerifier(
        CrashFileMarkerImpl(mockk(relaxed = true), mockk(relaxed = true)),
        mockk(relaxed = true)
    )

    override val crashService = FakeCrashService()

    override val automaticVerificationExceptionHandler =
        AutomaticVerificationExceptionHandler(null, mockk(relaxed = true))

    override val nativeCrashService: NativeCrashService = FakeNativeCrashService()
}
