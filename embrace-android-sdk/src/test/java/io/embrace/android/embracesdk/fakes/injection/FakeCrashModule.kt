package io.embrace.android.embracesdk.fakes.injection

import io.embrace.android.embracesdk.fakes.FakeCrashDataSource
import io.embrace.android.embracesdk.internal.crash.CrashFileMarkerImpl
import io.embrace.android.embracesdk.internal.crash.LastRunCrashVerifier
import io.embrace.android.embracesdk.internal.injection.CrashModule
import java.io.File

internal class FakeCrashModule : CrashModule {
    override val lastRunCrashVerifier = LastRunCrashVerifier(
        CrashFileMarkerImpl(
            lazy { File.createTempFile("embrace", "crash_marker") }
        )
    )

    override val crashDataSource = FakeCrashDataSource()
}
