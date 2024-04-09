package io.embrace.android.embracesdk.internal.logs

/**
 * Class to hold attributes for a log
 */
internal open class LogAttributes(properties: Map<String, Any>?) {

    protected val attributes: MutableMap<String, String> = mutableMapOf()

    init {
        // Note that there is an implicit conversion from Any to String here
        // Currently the backend only supports string attributes for logs
        properties?.forEach {
            attributes[it.key] = it.value.toString()
        }
    }

    /**
     * Add session properties as attributes to the log
     */
    fun setSessionProperties(sessionProperties: Map<String, String>) {
        sessionProperties.forEach {
            attributes["$SESSION_PROPERTIES_NAME_PREFIX${it.key}"] = it.value
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

    fun getLogId(): String? = attributes[LOG_ID_ATTRIBUTE_NAME]

    /**
     * Add a unique log identifier
     */
    fun setLogId(logId: String) {
        attributes[LOG_ID_ATTRIBUTE_NAME] = logId
    }

    fun toMap(): Map<String, String> {
        return attributes
    }
}

/**
 * Prefix added to all attribute keys for all attributes added by the SDK
 */
internal const val EMBRACE_ATTRIBUTE_NAME_PREFIX = "emb."

/**
 * Prefix added to all attribute keys for all session properties added by the SDK
 */
private const val SESSION_PROPERTIES_NAME_PREFIX = EMBRACE_ATTRIBUTE_NAME_PREFIX + "properties."

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
private const val LOG_ID_ATTRIBUTE_NAME = EMBRACE_ATTRIBUTE_NAME_PREFIX + "log_id"
