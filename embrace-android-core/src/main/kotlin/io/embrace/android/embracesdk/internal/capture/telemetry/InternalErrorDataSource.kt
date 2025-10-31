package io.embrace.android.embracesdk.internal.capture.telemetry

import io.embrace.android.embracesdk.internal.arch.datasource.DataSource
import io.embrace.android.embracesdk.internal.logging.InternalErrorHandler

interface InternalErrorDataSource : DataSource, InternalErrorHandler
