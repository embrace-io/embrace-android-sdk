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
         * The mode an app is given where the remote config names none, which is the one place that says what the default is.
         * An app that has a log record exporter or processor gets [ASSOCIATION]: the events for each change are log records
         * it receives, and they resolve the sequence numbers on its spans. An app with only span exporters or processors
         * gets [VERBOSE], as it receives no events and its spans would otherwise carry two numbers with nothing to resolve
         * them. An app that has configured nothing of either is given [OFF] by the caller, as there is nobody to give
         * anything to.
         */
        fun defaultFor(hasLogReceiver: Boolean): StateExportMode = if (hasLogReceiver) ASSOCIATION else VERBOSE

        /**
         * The mode [name] stands for in the remote config, whatever its case, or null if it stands for none.
         */
        fun fromRemote(name: String?): StateExportMode? = entries.firstOrNull { mode ->
            mode.name.equals(name, ignoreCase = true)
        }
    }
}
