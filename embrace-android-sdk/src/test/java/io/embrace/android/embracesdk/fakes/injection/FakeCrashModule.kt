package io.embrace.android.embracesdk.fakes.injection

import io.embrace.android.embracesdk.fakes.FakeCrashDataSource
import io.embrace.android.embracesdk.internal.crash.CrashFileMarkerImpl
import io.embrace.android.embracesdk.internal.crash.LastRunCrashVerifier
import io.embrace.android.embracesdk.internal.injection.CrashModule
import io.mockk.mockk

internal class FakeCrashModule : CrashModule {
    override val lastRunCrashVerifier = LastRunCrashVerifier(
        CrashFileMarkerImpl(mockk(relaxed = true), mockk(relaxed = true)),
        mockk(relaxed = true)
    )

    override val crashDataSource = FakeCrashDataSource()
}
