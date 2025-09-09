package io.embrace.android.embracesdk.fakes

import io.embrace.opentelemetry.kotlin.ExperimentalApi
import io.embrace.opentelemetry.kotlin.factory.TracingIdFactory
import kotlin.random.Random

@OptIn(ExperimentalApi::class)
class FakeTracingIdFactory : TracingIdFactory {
    override val invalidSpanId: String = "0000000000000000"
    override val invalidTraceId: String = "00000000000000000000000000000000"

    override fun generateSpanId(): String {
        return Random.nextLong().toULong().toString(16).padStart(16, '0')
    }

    override fun generateTraceId(): String {
        return Random.nextLong().toULong().toString(16).padStart(32, '0')
    }
}
