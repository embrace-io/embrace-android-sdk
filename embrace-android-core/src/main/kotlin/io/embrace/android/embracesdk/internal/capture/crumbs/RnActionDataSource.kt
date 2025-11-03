package io.embrace.android.embracesdk.internal.capture.crumbs

import io.embrace.android.embracesdk.internal.arch.InstrumentationArgs
import io.embrace.android.embracesdk.internal.arch.datasource.DataSourceImpl
import io.embrace.android.embracesdk.internal.arch.limits.UpToLimitStrategy
import io.embrace.android.embracesdk.internal.arch.schema.SchemaType

class RnActionDataSource(
    args: InstrumentationArgs
) : DataSourceImpl(
    args,
    UpToLimitStrategy { args.configService.breadcrumbBehavior.getCustomBreadcrumbLimit() }
) {
    fun logRnAction(
        name: String?,
        startTime: Long,
        endTime: Long,
        properties: Map<String?, Any?>,
        bytesSent: Int,
        output: String?,
    ): Boolean {
        captureTelemetry(
            inputValidation = { !name.isNullOrEmpty() }
        ) {
            val schemaType = SchemaType.ReactNativeAction(
                checkNotNull(name),
                checkNotNull(output),
                bytesSent,
                properties
            )
            recordCompletedSpan(
                name = schemaType.fixedObjectName,
                startTimeMs = startTime,
                endTimeMs = endTime,
                type = schemaType.telemetryType,
                attributes = schemaType.attributes(),
            )
        }
        return true
    }
}
