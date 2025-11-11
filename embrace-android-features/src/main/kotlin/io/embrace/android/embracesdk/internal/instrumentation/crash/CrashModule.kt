package io.embrace.android.embracesdk.internal.instrumentation.crash

import io.embrace.android.embracesdk.internal.instrumentation.crash.jvm.JsCrashService
import io.embrace.android.embracesdk.internal.instrumentation.crash.jvm.JvmCrashDataSource

/**
 * Contains dependencies that capture crashes
 */
interface CrashModule {
    val jvmCrashDataSource: JvmCrashDataSource
    val jsCrashService: JsCrashService?
}
