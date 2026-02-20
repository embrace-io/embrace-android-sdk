package io.embrace.android.embracesdk.fakes

import io.opentelemetry.kotlin.ExperimentalApi
import io.opentelemetry.kotlin.factory.TracingIdFactory
import kotlin.random.Random

@OptIn(ExperimentalApi::class)
class FakeTracingIdFactory(private val random: Random = Random(0)) : TracingIdFactory {

    private companion object {
        private const val TRACE_ID_BYTES = 16
        private const val SPAN_ID_BYTES = 8
    }

    override fun generateTraceIdBytes(): ByteArray = generateId(TRACE_ID_BYTES)
    override fun generateSpanIdBytes(): ByteArray = generateId(SPAN_ID_BYTES)

    override val invalidTraceId: ByteArray = ByteArray(TRACE_ID_BYTES)
    override val invalidSpanId: ByteArray = ByteArray(SPAN_ID_BYTES)

    private fun generateId(length: Int): ByteArray {
        val bytes = ByteArray(length)
        do {
            random.nextBytes(bytes)
        } while (bytes.all { it == 0.toByte() }) // reject all-zero IDs
        return bytes
    }
}
