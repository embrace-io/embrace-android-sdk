package io.embrace.android.embracesdk.capture.crash

import io.embrace.android.embracesdk.arch.datasource.LogDataSource

internal interface CrashDataSource : LogDataSource, CrashService
