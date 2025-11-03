package io.embrace.android.embracesdk.fakes.injection

import io.embrace.android.embracesdk.fakes.FakeCrashDataSource
import io.embrace.android.embracesdk.internal.instrumentation.crash.jvm.CrashFileMarkerImpl
import io.embrace.android.embracesdk.internal.instrumentation.crash.jvm.CrashModule
import io.embrace.android.embracesdk.internal.instrumentation.crash.LastRunCrashVerifier
import java.io.File

internal class FakeCrashModule : CrashModule {
    override val lastRunCrashVerifier = LastRunCrashVerifier(
        CrashFileMarkerImpl(
            lazy { File.createTempFile("embrace", "crash_marker") }
        )
    )

    override val crashDataSource = FakeCrashDataSource()
}
