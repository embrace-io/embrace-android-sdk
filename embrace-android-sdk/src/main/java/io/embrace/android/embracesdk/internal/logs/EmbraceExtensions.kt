package io.embrace.android.embracesdk.internal.logs

import io.embrace.android.embracesdk.LogExceptionType
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.logs.LogRecordBuilder

/**
 * Extension functions and constants to augment the core OpenTelemetry SDK and provide Embrace-specific customizations
 *
 * Note: there's no explicit tests for these extensions as their functionality will be validated as part of other tests.
 */

/**
 * Prefix added to all [LogRecordBuilder] attribute keys for all attributes added by the SDK
 */
private const val EMBRACE_ATTRIBUTE_NAME_PREFIX = "emb."

/**
 * Attribute name for the application state (foreground/background) at the time the log was recorded
 */
private const val APP_STATE_ATTRIBUTE_NAME = EMBRACE_ATTRIBUTE_NAME_PREFIX + "state"

/**
 * Attribute name for the id of the session in progress at the time the log was recorded
 */
private const val SESSION_ID_ATTRIBUTE_NAME = EMBRACE_ATTRIBUTE_NAME_PREFIX + "session_id"

/**
 * Attribute name for a unique id identifying the log
 */
private const val EVENT_ID_ATTRIBUTE_NAME = EMBRACE_ATTRIBUTE_NAME_PREFIX + "event_id"

/**
 * Attribute name for the exception type in a log representing an exception
 */
private const val EXCEPTION_TYPE_ATTRIBUTE_NAME = EMBRACE_ATTRIBUTE_NAME_PREFIX + "exception_type"

/**
 * Attribute name for the exception name in a log representing an exception
 */
private const val EXCEPTION_NAME_ATTRIBUTE_NAME = EMBRACE_ATTRIBUTE_NAME_PREFIX + "exception_name"

/**
 * Attribute name for the exception message in a log representing an exception
 */
private const val EXCEPTION_MESSAGE_ATTRIBUTE_NAME = EMBRACE_ATTRIBUTE_NAME_PREFIX + "exception_message"

/**
 * Attribute name for the exception context in a log representing an exception
 */
private const val EXCEPTION_CONTEXT_ATTRIBUTE_NAME = EMBRACE_ATTRIBUTE_NAME_PREFIX + "exception_context"

/**
 * Attribute name for the exception library in a log representing an exception
 */
private const val EXCEPTION_LIBRARY_ATTRIBUTE_NAME = EMBRACE_ATTRIBUTE_NAME_PREFIX + "exception_library"

/**
 * Add an attribute representing the application state (foreground/background) at the time the log was recorded
 */
internal fun LogRecordBuilder.setAppState(appState: String): LogRecordBuilder {
    setAttribute(AttributeKey.stringKey(APP_STATE_ATTRIBUTE_NAME), appState)
    return this
}

/**
 * Set an id for the session in progress when the log was recorded
 */
internal fun LogRecordBuilder.setSessionId(sessionId: String): LogRecordBuilder {
    setAttribute(AttributeKey.stringKey(SESSION_ID_ATTRIBUTE_NAME), sessionId)
    return this
}

/**
 * Set an id for the log
 */
internal fun LogRecordBuilder.setEventId(eventId: String): LogRecordBuilder {
    setAttribute(AttributeKey.stringKey(EVENT_ID_ATTRIBUTE_NAME), eventId)
    return this
}

/**
 * Set an exception type for the log
 */
internal fun LogRecordBuilder.setExceptionType(exceptionType: LogExceptionType): LogRecordBuilder {
    setAttribute(AttributeKey.stringKey(EXCEPTION_TYPE_ATTRIBUTE_NAME), exceptionType.value)
    return this
}

/**
 * Set an exception name for the log
 */
internal fun LogRecordBuilder.setExceptionName(exceptionName: String): LogRecordBuilder {
    setAttribute(AttributeKey.stringKey(EXCEPTION_NAME_ATTRIBUTE_NAME), exceptionName)
    return this
}

/**
 * Set an exception message for the log
 */
internal fun LogRecordBuilder.setExceptionMessage(exceptionMessage: String): LogRecordBuilder {
    setAttribute(AttributeKey.stringKey(EXCEPTION_MESSAGE_ATTRIBUTE_NAME), exceptionMessage)
    return this
}

/**
 * Set an exception context for the log
 */
internal fun LogRecordBuilder.setExceptionContext(exceptionContext: String): LogRecordBuilder {
    setAttribute(AttributeKey.stringKey(EXCEPTION_CONTEXT_ATTRIBUTE_NAME), exceptionContext)
    return this
}

/**
 * Set an exception library for the log
 */
internal fun LogRecordBuilder.setExceptionLibrary(exceptionLibrary: String): LogRecordBuilder {
    setAttribute(AttributeKey.stringKey(EXCEPTION_LIBRARY_ATTRIBUTE_NAME), exceptionLibrary)
    return this
}
