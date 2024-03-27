package io.embrace.android.embracesdk.arch.schema

import io.embrace.android.embracesdk.arch.schema.SchemaKeys.AEI_RECORD
import io.embrace.android.embracesdk.arch.schema.SchemaKeys.CUSTOM_BREADCRUMB
import io.embrace.android.embracesdk.arch.schema.SchemaKeys.LOG
import io.embrace.android.embracesdk.arch.schema.SchemaKeys.VIEW_BREADCRUMB
import io.embrace.android.embracesdk.internal.logs.EmbraceLogAttributes
import io.embrace.android.embracesdk.internal.utils.toNonNullMap
import io.embrace.android.embracesdk.payload.AppExitInfoData

internal object SchemaKeys {
    internal const val CUSTOM_BREADCRUMB = "emb-custom-breadcrumb"
    internal const val VIEW_BREADCRUMB = "screen-view"
    internal const val AEI_RECORD = "aei-record"
    internal const val LOG = "emb-log"
}

internal sealed class SchemaType(
    val telemetryType: TelemetryType,
    val name: String,
) {
    abstract val attrs: Map<String, String>

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
