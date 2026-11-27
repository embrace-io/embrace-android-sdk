package io.embrace.android.embracesdk.internal.state

/**
 * Reads the values the state recorder holds, without taking part in recording them.
 */
interface StateReader {

    /**
     * The values in force now. Never blocks.
     */
    fun current(): StateSnapshot

    /**
     * The values that were in force at [timeMs], by the SDK's clock, from the short window of recent snapshots the
     * recorder keeps, or null if [timeMs] is older than the window. A time equal to the one a change was recorded at
     * counts as after the change.
     */
    fun snapshotAt(timeMs: Long): StateSnapshot?
}

/**
 * How the state is rendered for a customer's OpenTelemetry exporters.
 */
enum class StateExportMode {

    /**
     * A span carries its two sequence numbers alone. The default for an app that receives logs.
     */
    ASSOCIATION,

    /**
     * A span also carries every state's value at its start, and each value that differed at its end. The default for
     * an app with a span exporter and no log receiver.
     */
    VERBOSE,
}
