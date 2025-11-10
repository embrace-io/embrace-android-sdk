package io.embrace.android.embracesdk.fakes.injection

import io.embrace.android.embracesdk.fakes.FakeJvmCrashDataSource
import io.embrace.android.embracesdk.internal.instrumentation.crash.CrashFileMarkerImpl
import io.embrace.android.embracesdk.internal.instrumentation.crash.CrashModule
import io.embrace.android.embracesdk.internal.instrumentation.crash.LastRunCrashVerifier
import io.embrace.android.embracesdk.internal.instrumentation.crash.jvm.JsCrashService
import java.io.File

internal class FakeCrashModule : CrashModule {
    override val lastRunCrashVerifier = LastRunCrashVerifier(
        CrashFileMarkerImpl(
            lazy { File.createTempFile("embrace", "crash_marker") }
        )
    )

    override val jvmCrashDataSource = FakeJvmCrashDataSource()

    override val jsCrashService: JsCrashService? = null
}
