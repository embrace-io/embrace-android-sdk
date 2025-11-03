package io.embrace.android.embracesdk.internal.instrumentation.crash.ndk

import io.embrace.android.embracesdk.internal.arch.datasource.DataSource
import io.embrace.android.embracesdk.internal.ndk.NativeCrashService

internal interface NativeCrashDataSource : DataSource, NativeCrashService
