package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.instrumentation.crash.ndk.NativeCrashHandlerInstaller

internal class FakeNativeCrashHandlerInstaller : NativeCrashHandlerInstaller {

    var installed = false
    var disabled = false

    override fun install() {
        installed = true
    }

    override fun disable() {
        disabled = true
    }
}
