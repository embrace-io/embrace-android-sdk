package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.instrumentation.crash.jvm.CrashService

class FakeCrashService : CrashService {
    var exception: Throwable? = null

    override fun logUnhandledJvmException(exception: Throwable) {
        this.exception = exception
    }

    override fun logUnhandledJsException(name: String, message: String, type: String?, stacktrace: String?) {
    }
}
