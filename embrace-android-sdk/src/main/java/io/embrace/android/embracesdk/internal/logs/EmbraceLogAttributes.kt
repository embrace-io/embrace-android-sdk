package io.embrace.android.embracesdk.internal.logs

import io.embrace.android.embracesdk.LogExceptionType

internal class EmbraceLogAttributes(properties: Map<String, Any>?) {

    companion object {
        /**
         * Prefix added to all attribute keys for all attributes added by the SDK
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
        const val LOG_ID_ATTRIBUTE_NAME = EMBRACE_ATTRIBUTE_NAME_PREFIX + "log_id"

        /**
         * Attribute name for the exception type in a log representing an exception
         */
        private const val EXCEPTION_TYPE_ATTRIBUTE_NAME =
            EMBRACE_ATTRIBUTE_NAME_PREFIX + "exception_type"

        /**
         * Attribute name for the exception name in a log representing an exception
         */
        private const val EXCEPTION_NAME_ATTRIBUTE_NAME =
            EMBRACE_ATTRIBUTE_NAME_PREFIX + "exception_name"

        /**
         * Attribute name for the exception message in a log representing an exception
         */
        private const val EXCEPTION_MESSAGE_ATTRIBUTE_NAME =
            EMBRACE_ATTRIBUTE_NAME_PREFIX + "exception_message"

        /**
         * Attribute name for the exception context in a log representing an exception
         */
        private const val EXCEPTION_CONTEXT_ATTRIBUTE_NAME =
            EMBRACE_ATTRIBUTE_NAME_PREFIX + "exception_context"

        /**
         * Attribute name for the exception library in a log representing an exception
         */
        private const val EXCEPTION_LIBRARY_ATTRIBUTE_NAME =
            EMBRACE_ATTRIBUTE_NAME_PREFIX + "exception_library"
    }

    private val attributes: MutableMap<String, String> = mutableMapOf()

    init {
        // TODO Validate attribute names?
        // TODO There is an implicit conversion from Any to String here. Should the attributes
        // in LogEventData be <String, Any> instead of <String, String> ?
        properties?.forEach {
            attributes[it.key] = it.value.toString()
        }
    }

    /**
     * Add an attribute representing the application state (foreground/background) at the time the log was recorded
     */
    fun setAppState(appState: String) {
        attributes[APP_STATE_ATTRIBUTE_NAME] = appState
    }

    /**
     * Set an id for the session in progress when the log was recorded
     */
    fun setSessionId(sessionId: String) {
        attributes[SESSION_ID_ATTRIBUTE_NAME] = sessionId
    }

    /**
     * Set an exception type for the log
     */
    fun setExceptionType(exceptionType: LogExceptionType) {
        attributes[EXCEPTION_TYPE_ATTRIBUTE_NAME] = exceptionType.value
    }

    /**
     * Set an exception name for the log
     */
    fun setExceptionName(exceptionName: String) {
        attributes[EXCEPTION_NAME_ATTRIBUTE_NAME] = exceptionName
    }

    /**
     * Set an exception message for the log
     */
    fun setExceptionMessage(exceptionMessage: String) {
        attributes[EXCEPTION_MESSAGE_ATTRIBUTE_NAME] = exceptionMessage
    }

    /**
     * Set an exception context for the log
     */
    fun setExceptionContext(exceptionContext: String) {
        attributes[EXCEPTION_CONTEXT_ATTRIBUTE_NAME] = exceptionContext
    }

    /**
     * Set an exception library for the log
     */
    fun setExceptionLibrary(exceptionLibrary: String) {
        attributes[EXCEPTION_LIBRARY_ATTRIBUTE_NAME] = exceptionLibrary
    }

    fun toMap(): Map<String, String> {
        return attributes
    }
}
