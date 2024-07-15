package io.embrace.android.embracesdk.internal.telemetry.errors

import io.embrace.android.embracesdk.internal.arch.datasource.LogDataSource
import io.embrace.android.embracesdk.internal.logging.InternalErrorHandler

internal interface InternalErrorDataSource : LogDataSource, InternalErrorHandler
