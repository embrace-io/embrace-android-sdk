package io.embrace.android.embracesdk.arch.schema

import io.embrace.android.embracesdk.internal.spans.toEmbraceAttributeName

internal sealed class EmbType {

    /**
     * Keys that track how fast a time interval is. Only applies to spans.
     */
    internal object Performance : TelemetryType {
        override val description: String = "performance"
    }

    /**
     * Keys that track a point in time & is visual in nature. Applies to spans, logs, and span events.
     */
    internal sealed class Ux(subtype: String) : TelemetryType {
        internal object Session : Ux("session")

        internal object View : Ux("view")

        override val description = "ux.$subtype"
    }

    /**
     * Keys that track a point in time that is not visual in nature. Applies to spans, logs, and span events.
     */
    internal sealed class System(subtype: String) : TelemetryType {
        internal object Breadcrumb : System("breadcrumb")
        internal object Log : System("log")
        internal object Exit : System("exit")

        override val description = "system.$subtype"
    }
}

/**
 * Represents a telemetry type (emb.type). For example, "ux.view" is a type that represents
 * a visual event around a UI element. ux is the type, and view is the subtype. This tells the
 * backend that it can assume the data in the event follows a particular schema.
 */
internal interface TelemetryType {
    val description: String

    /**
     * Return the key name used by this attribute when is used inside of OpenTelemetry objects
     */
    fun attributeName(): String = "type".toEmbraceAttributeName()
}
