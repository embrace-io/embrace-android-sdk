package io.embrace.android.embracesdk.internal.otel.state

/**
 * How state is rendered for the app's own OpenTelemetry exporters. Both renderings put every state's value on a log and send an
 * event for each change made in a session part. They differ in what a span and a change event carry.
 */
enum class StateExportMode(val configValue: String) {

    /**
     * A span carries only the two numbers that place it among the change events, and a change event carries the change alone.
     */
    ASSOCIATION("association"),

    /**
     * A span also carries every state's value at its start and each value that differed at its end, and a change event also
     * carries every state's value after the change, so that nothing has to be joined.
     */
    VERBOSE("verbose"),
    ;

    companion object {

        /**
         * The rendering to use: the one [configured] names, or the one that suits what the app can use. An app that receives logs
         * gets the change events, so association is enough. An app with only a span exporter gets none of them, so its spans
         * carry the values.
         */
        fun resolve(configured: String?, receivesLogs: Boolean, receivesSpans: Boolean): StateExportMode =
            entries.firstOrNull { it.configValue.equals(configured?.trim(), ignoreCase = true) } ?: when {
                receivesSpans && !receivesLogs -> VERBOSE
                else -> ASSOCIATION
            }
    }
}
