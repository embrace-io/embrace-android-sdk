package io.embrace.android.embracesdk.internal.instrumentation.crash.ndk

import io.embrace.android.embracesdk.internal.arch.datasource.DataSource

interface NativeCrashDataSource : DataSource, NativeCrashService
