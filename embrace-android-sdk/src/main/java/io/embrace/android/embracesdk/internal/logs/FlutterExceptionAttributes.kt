package io.embrace.android.embracesdk.internal.logs

/**
 * Attributes for a log representing an exception in a flutter app
 */
internal class FlutterExceptionAttributes(properties: Map<String, Any>?) : ExceptionAttributes(properties) {

    /**
     * Set an exception context for the log
     */
    fun setContext(exceptionContext: String) {
        attributes[EXCEPTION_CONTEXT_ATTRIBUTE_NAME] = exceptionContext
    }

    /**
     * Set an exception library for the log
     */
    fun setLibrary(exceptionLibrary: String) {
        attributes[EXCEPTION_LIBRARY_ATTRIBUTE_NAME] = exceptionLibrary
    }
}

/**
 * Attribute name for the exception context
 */
private const val EXCEPTION_CONTEXT_ATTRIBUTE_NAME =
    EMBRACE_ATTRIBUTE_NAME_PREFIX + EXCEPTION_ATTRIBUTE_NAME_PREFIX + "context"

/**
 * Attribute name for the exception library
 */
private const val EXCEPTION_LIBRARY_ATTRIBUTE_NAME =
    EMBRACE_ATTRIBUTE_NAME_PREFIX + EXCEPTION_ATTRIBUTE_NAME_PREFIX + "library"
