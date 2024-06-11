package io.embrace.android.embracesdk.capture.internal.errors

import io.embrace.android.embracesdk.arch.datasource.LogDataSource

internal interface InternalErrorDataSource : LogDataSource, InternalErrorHandler
