package io.embrace.android.embracesdk.arch.schema

internal sealed class EmbType(type: String, subtype: String?) : TelemetryType {
    override val attributeName: String = "type"
    override val attributeValue = type + (subtype?.run { ".$this" } ?: "")

    /**
     * Keys that track how fast a time interval is. Only applies to spans.
     */
    internal sealed class Performance(subtype: String?) : EmbType("perf", subtype) {

        internal object Default : Performance(null)

        internal object Network : Performance("network_request")
    }

    /**
     * Keys that track telemetry that is explicitly tied to user behaviour or visual in nature.
     * Applies to spans, logs, and span events.
     */
    internal sealed class Ux(subtype: String) : EmbType("ux", subtype) {

        internal object Session : Ux("session")

        internal object View : Ux("view")
    }

    /**
     * Keys that track telemetry that is not explicitly tied to user behaviour and is not visual in nature.
     * Applies to spans, logs, and span events.
     */
    internal sealed class System(subtype: String) : EmbType("sys", subtype) {

        internal object Breadcrumb : System("custom_breadcrumb")

        internal object Log : System("log")

        internal object Exit : System("exit")
    }
}

/**
 * Represents a telemetry type (emb.type). For example, "ux.view" is a type that represents
 * a visual event around a UI element. ux is the type, and view is the subtype. This tells the
 * backend that it can assume the data in the event follows a particular schema.
 */
internal interface TelemetryType : EmbraceAttribute
