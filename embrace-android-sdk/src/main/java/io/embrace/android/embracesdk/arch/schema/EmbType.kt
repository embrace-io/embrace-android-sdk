package io.embrace.android.embracesdk.arch.schema

internal sealed class EmbType(type: String, subtype: String?) : TelemetryType {
    override val attributeName: String = "type"
    override val attributeValue = type + (subtype?.run { ".$this" } ?: "")

    /**
     * Keys that track how fast a time interval is. Only applies to spans.
     */
    internal sealed class Performance(subtype: String?) : EmbType("performance", subtype) {

        internal object Default : Performance(null)

        internal object Network : Performance("network")
    }

    /**
     * Keys that track a point in time & is visual in nature. Applies to spans, logs, and span events.
     */
    internal sealed class Ux(subtype: String) : EmbType("ux", subtype) {

        internal object Session : Ux("session")

        internal object View : Ux("view")
    }

    /**
     * Keys that track a point in time that is not visual in nature. Applies to spans, logs, and span events.
     */
    internal sealed class System(subtype: String) : EmbType("system", subtype) {

        internal object Breadcrumb : System("breadcrumb")

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
