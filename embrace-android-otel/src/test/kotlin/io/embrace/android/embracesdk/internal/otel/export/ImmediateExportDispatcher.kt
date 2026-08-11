package io.embrace.android.embracesdk.internal.otel.export

import kotlinx.coroutines.Dispatchers

/**
 * An [ExternalExportDispatcher] that runs exports on the calling thread, so that a test asserting on
 * what reached an external exporter doesn't have to drain anything first.
 */
internal fun immediateExportDispatcher(): ExternalExportDispatcher =
    ExternalExportDispatcher { Dispatchers.Unconfined }
