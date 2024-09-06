package io.embrace.android.embracesdk.internal.capture.telemetry

import io.embrace.android.embracesdk.internal.arch.datasource.LogDataSource
import io.embrace.android.embracesdk.internal.logging.InternalErrorHandler

interface InternalErrorDataSource : LogDataSource, InternalErrorHandler
