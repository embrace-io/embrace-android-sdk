package io.embrace.android.embracesdk.arch.schema

import io.embrace.android.embracesdk.arch.schema.SchemaDefaultName.AEI_RECORD
import io.embrace.android.embracesdk.arch.schema.SchemaDefaultName.CUSTOM_BREADCRUMB
import io.embrace.android.embracesdk.arch.schema.SchemaDefaultName.LOG
import io.embrace.android.embracesdk.arch.schema.SchemaDefaultName.VIEW_BREADCRUMB
import io.embrace.android.embracesdk.internal.logs.EmbraceLogAttributes
import io.embrace.android.embracesdk.internal.utils.toNonNullMap
import io.embrace.android.embracesdk.payload.AppExitInfoData

/**
 * The collections of attribute schemas used by the associated telemetry types.
 *
 * Each schema contains a [TelemetryType] that it is being applied to, as well as a [defaultName] used for the generated
 * telemetry data object if a fixed one is being used.
 */
internal sealed class SchemaType(
    val telemetryType: TelemetryType,
    val defaultName: String,
) {
    protected abstract val attrs: Map<String, String>

    /**
     * The attributes defined fo this schema that should be used to populate telemetry objects
     */
    fun attributes(): Map<String, String> = attrs.plus(telemetryType.toOTelKeyValuePair())

    internal class CustomBreadcrumb(message: String) : SchemaType(
        EmbType.System.Breadcrumb,
        CUSTOM_BREADCRUMB
    ) {
        override val attrs = mapOf("message" to message)
    }

    internal class ViewBreadcrumb(viewName: String) : SchemaType(
        EmbType.Ux.View,
        VIEW_BREADCRUMB
    ) {
        override val attrs = mapOf("view.name" to viewName)
    }

    internal class AeiLog(message: AppExitInfoData) : SchemaType(
        EmbType.System.Exit,
        AEI_RECORD
    ) {
        override val attrs = mapOf(
            "session-id" to message.sessionId,
            "session-id-error" to message.sessionIdError,
            "process-importance" to message.importance.toString(),
            "pss" to message.pss.toString(),
            "rs" to message.reason.toString(),
            "rss" to message.rss.toString(),
            "exit-status" to message.status.toString(),
            "timestamp" to message.timestamp.toString(),
            "description" to message.description,
            "trace-status" to message.traceStatus
        ).toNonNullMap()
    }

    internal class Log(attributes: EmbraceLogAttributes) : SchemaType(
        EmbType.System.Log,
        LOG
    ) {
        override val attrs = attributes.toMap()
    }
}

/**
 * Note: Spans marked as internal will always be prefixed with "emb-", so the default name doesn't need to add this.
 */
internal object SchemaDefaultName {
    internal const val CUSTOM_BREADCRUMB = "emb-custom-breadcrumb"
    internal const val VIEW_BREADCRUMB = "screen-view"
    internal const val AEI_RECORD = "emb-aei-record"
    internal const val LOG = "emb-log"
}
