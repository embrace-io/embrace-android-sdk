package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.instrumentation.crash.jvm.JvmCrashService

class FakeJvmCrashService : JvmCrashService {
    var exception: Throwable? = null

    override fun logUnhandledJvmThrowable(exception: Throwable) {
        this.exception = exception
    }
}
