package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.arch.datasource.LogSeverity
import io.embrace.android.embracesdk.internal.arch.schema.SchemaType

class FakeLogData(
    val schemaType: SchemaType,
    val severity: LogSeverity,
    val message: String,
)
