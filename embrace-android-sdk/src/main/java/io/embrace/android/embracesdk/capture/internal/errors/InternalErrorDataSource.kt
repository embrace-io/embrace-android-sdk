package io.embrace.android.embracesdk.capture.internal.errors

import io.embrace.android.embracesdk.internal.arch.datasource.LogDataSource

internal interface InternalErrorDataSource : LogDataSource, InternalErrorHandler
