package io.embrace.android.embracesdk.internal.ndk

import io.embrace.android.embracesdk.internal.arch.datasource.LogDataSource

internal interface NativeCrashDataSource : LogDataSource, NativeCrashService
