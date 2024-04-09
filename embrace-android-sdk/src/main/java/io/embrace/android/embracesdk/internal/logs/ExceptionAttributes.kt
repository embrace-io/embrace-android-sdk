package io.embrace.android.embracesdk.internal.logs

import io.embrace.android.embracesdk.LogExceptionType

/**
 * Attributes for a log representing an exception
 */
internal open class ExceptionAttributes(properties: Map<String, Any>?) : LogAttributes(properties) {

    /**
     * Set an exception handling for the log
     */
    fun setExceptionHandling(exceptionType: LogExceptionType) {
        attributes[EXCEPTION_HANDLING_ATTRIBUTE_NAME] = exceptionType.value
    }

    /**
     * Set an exception stacktrace for the log
     */
    fun setExceptionStacktrace(exceptionStacktrace: String) {
        attributes[EXCEPTION_STACKTRACE_ATTRIBUTE_NAME] = exceptionStacktrace
    }

    /**
     * Set an exception message for the log
     */
    fun setExceptionMessage(exceptionMessage: String) {
        attributes[EXCEPTION_MESSAGE_ATTRIBUTE_NAME] = exceptionMessage
    }

    /**
     * Set an exception name for the log.
     * The type of the exception (its fully-qualified class name, if applicable)
     */
    fun setExceptionName(exceptionName: String) {
        attributes[EXCEPTION_NAME_ATTRIBUTE_NAME] = exceptionName
    }
}

/**
 * Attribute name for the exception handling in a log (handled/unhandled)
 */
private const val EXCEPTION_HANDLING_ATTRIBUTE_NAME = EMBRACE_ATTRIBUTE_NAME_PREFIX + "exception_handling"

/**
 * Prefix added to all exception attributes
 */
internal const val EXCEPTION_ATTRIBUTE_NAME_PREFIX = "exception."

/**
 * Attribute name for the stacktrace of an exception
 */
private const val EXCEPTION_STACKTRACE_ATTRIBUTE_NAME = EXCEPTION_ATTRIBUTE_NAME_PREFIX + "stacktrace"

/**
 * Attribute name for the message of an exception
 */
private const val EXCEPTION_MESSAGE_ATTRIBUTE_NAME = EXCEPTION_ATTRIBUTE_NAME_PREFIX + "message"

/**
 * Attribute name for the name of an exception.
 * We named this as "Exception name", but OTel documentation uses "exception.type",
 * as the key for the attribute.
 */
private const val EXCEPTION_NAME_ATTRIBUTE_NAME = EXCEPTION_ATTRIBUTE_NAME_PREFIX + "type"
