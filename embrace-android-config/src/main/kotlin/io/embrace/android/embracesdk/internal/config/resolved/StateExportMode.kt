package io.embrace.android.embracesdk.internal.config.resolved

/**
 * What an app's own OpenTelemetry exporters and processors are given of state, beyond what Embrace's own payload carries:
 * the sequence numbers on a span and the value of every state on a log.
 */
enum class StateExportMode {

    /**
     * Nothing more.
     */
    OFF,

    /**
     * An event for each state change and one for the values when a session part starts, so that whatever receives them
     * can resolve a span's sequence numbers itself.
     */
    ASSOCIATION,

    /**
     * The same events, each change with the value of every state after it, and on every span the value of every state
     * when it started and of each state that differed when it ended.
     */
    VERBOSE,
    ;

    companion object {

        /**
         * The mode [name] stands for in the remote config, whatever its case, or null if it stands for none.
         */
        fun fromRemote(name: String?): StateExportMode? = entries.firstOrNull { mode ->
            mode.name.equals(name, ignoreCase = true)
        }
    }
}
