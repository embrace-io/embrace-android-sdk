package io.embrace.android.embracesdk.internal.capture.crash

import io.embrace.android.embracesdk.internal.arch.datasource.LogDataSource

internal interface CrashDataSource : LogDataSource, CrashService
