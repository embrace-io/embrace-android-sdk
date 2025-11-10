package io.embrace.android.embracesdk.fakes.injection

import io.embrace.android.embracesdk.fakes.FakeJvmCrashDataSource
import io.embrace.android.embracesdk.internal.instrumentation.crash.CrashModule
import io.embrace.android.embracesdk.internal.instrumentation.crash.jvm.JsCrashService

internal class FakeCrashModule : CrashModule {

    override val jvmCrashDataSource = FakeJvmCrashDataSource()

    override val jsCrashService: JsCrashService? = null
}
