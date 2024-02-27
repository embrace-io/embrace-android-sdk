package io.embrace.android.embracesdk.internal.logs

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.sdk.logs.ReadWriteLogRecord

/**
 * Extension functions and constants to augment the core OpenTelemetry SDK and provide Embrace-specific customizations
 *
 * Note: there's no explicit tests for these extensions as their functionality will be validated as part of other tests.
 */

/**
 * Set an id for the log
 */
internal fun ReadWriteLogRecord.setLogId(logId: String) {
    setAttribute(AttributeKey.stringKey(EmbraceLogAttributes.LOG_ID_ATTRIBUTE_NAME), logId)
}
