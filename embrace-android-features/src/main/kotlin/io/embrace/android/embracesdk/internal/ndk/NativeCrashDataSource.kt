package io.embrace.android.embracesdk.internal.ndk

import io.embrace.android.embracesdk.internal.arch.datasource.DataSource

internal interface NativeCrashDataSource : DataSource, NativeCrashService
