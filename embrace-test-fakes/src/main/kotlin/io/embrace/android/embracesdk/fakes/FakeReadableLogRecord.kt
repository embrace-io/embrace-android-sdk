package io.embrace.android.embracesdk.fakes

import io.embrace.opentelemetry.kotlin.ExperimentalApi
import io.embrace.opentelemetry.kotlin.logging.model.ReadableLogRecord

@OptIn(ExperimentalApi::class)
class FakeReadableLogRecord(
    private val impl: FakeReadWriteLogRecord = FakeReadWriteLogRecord()
) : ReadableLogRecord by impl
