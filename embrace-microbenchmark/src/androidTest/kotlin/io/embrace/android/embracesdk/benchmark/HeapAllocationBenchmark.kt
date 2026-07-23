package io.embrace.android.embracesdk.benchmark

import android.os.Bundle
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.embrace.android.embracesdk.internal.arch.datasource.LogSeverity
import io.embrace.android.embracesdk.internal.arch.datasource.TelemetryDestination
import io.embrace.android.embracesdk.internal.arch.schema.SchemaType
import io.embrace.android.embracesdk.internal.arch.schema.TelemetryAttributes
import io.embrace.android.embracesdk.internal.otel.spans.NoopEmbraceSdkSpan
import io.embrace.android.embracesdk.internal.otel.spans.SpanService
import io.embrace.android.embracesdk.spans.EmbraceSpan
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Measures the number of bytes the Embrace API retains on the heap of the
 * real Android runtime (ART) for common operations. It uses the following approach:
 *
 *  1. allocate a first batch of items and keep them referenced
 *  2. force GC, snapshot the used heap
 *  3. allocate a second batch of items and keep them referenced
 *  4. force GC, snapshot the used heap again
 *
 * The difference between the two snapshots divided by the number of items added between them
 * gives the retained bytes per item. Taking the delta between two populated snapshots (rather
 * than empty-vs-populated) cancels out fixed harness/framework overhead, and the forced GC
 * before each snapshot removes uncollected garbage so only live objects only are measured.
 */
@RunWith(AndroidJUnit4::class)
class HeapAllocationBenchmark {

    private lateinit var harness: TelemetryDestinationHarness
    private lateinit var spanService: SpanService
    private lateinit var destination: TelemetryDestination
    private lateinit var linkTarget: EmbraceSpan
    private val sampleException = IllegalStateException("benchmark operation failed")

    @Before
    fun setup() {
        harness = TelemetryDestinationHarness()
        // avoid hitting span per session limit & skewing measurement
        spanService = harness.createUncappedSpanService()
        destination = harness.destination
        linkTarget = newSpan().apply { start() }
    }

    /**
     * Sanity check that a known allocation reports a retained size at least as large as the payload
     */
    @Test
    fun baseline() {
        report("baseline", retainedBytesPerItem { ByteArray(KNOWN_SIZE) })
    }

    @Test
    fun createVanillaSpan() {
        report("vanilla_span_created", retainedBytesPerItem { newSpan() })
    }

    @Test
    fun startVanillaSpan() {
        report("vanilla_span_started", retainedBytesPerItem { newSpan().apply { start() } })
    }

    @Test
    fun startAndStopVanillaSpan() {
        report(
            "vanilla_span_start_and_stop",
            retainedBytesPerItem {
                newSpan().apply {
                    start()
                    stop()
                }
            },
        )
    }

    @Test
    fun createComplexSpan() {
        report(
            "complex_span",
            retainedBytesPerItem(HEAVY_FIRST_BATCH, HEAVY_SECOND_BATCH) {
                complexSpan().apply { start() }
            },
        )
    }

    @Test
    fun emitMinimalLog() {
        report("minimal_log", retainedBytesPerItem { index -> minimalLog(index) })
    }

    @Test
    fun emitComplexLog() {
        report("complex_log", retainedBytesPerItem { index -> complexLog(index) })
    }

    /**
     * Creates a real span, failing loudly if the per-session-part cap is hit
     */
    private fun newSpan() = spanService.createSpan(name = "test").also {
        check(it !== NoopEmbraceSdkSpan) { "hit the per-session span cap" }
    }

    private fun complexSpan(): EmbraceSpan = newSpan().apply {
        start()
        updateName("complex.operation")
        repeat(COMPLEX_SPAN_ATTRIBUTES) { i ->
            addAttribute("complex.attribute.key.$i", "complex-attribute-value-$i")
        }
        repeat(COMPLEX_SPAN_EVENTS) { i ->
            addEvent(name = "complex-span-event-$i", attributes = freshAttributes(EVENT_ATTRIBUTES))
        }
        recordException(sampleException, freshAttributes(EXCEPTION_ATTRIBUTES))
        addLink(linkTarget, freshAttributes(LINK_ATTRIBUTES))
        stop()
    }

    private fun minimalLog(index: Int) {
        destination.addLog(
            schemaType = SchemaType.Log(TelemetryAttributes()),
            severity = LogSeverity.INFO,
            message = "user reached the checkout screen $index",
            addCurrentSessionInfo = false,
        )
    }

    private fun complexLog(index: Int) {
        destination.addLog(
            schemaType = SchemaType.Log(TelemetryAttributes(customAttributes = freshAttributes(COMPLEX_LOG_ATTRIBUTES))),
            severity = LogSeverity.WARNING,
            message = "checkout flow failed at the payment step for order $index with a retryable gateway error",
            isPrivate = true,
            addCurrentSessionInfo = false,
        )
    }

    private fun freshAttributes(count: Int): Map<String, String> =
        (0 until count).associate { "attribute.key.$it" to "attribute-value-$it" }

    /**
     * Allocates [firstBatch] then [secondBatch] items produced by [makeItem], snapshotting the used heap
     * after a forced GC between the two batches. Then returns the retained bytes per item added in
     * the second batch.
     */
    private inline fun retainedBytesPerItem(
        firstBatch: Int = LIGHT_FIRST_BATCH,
        secondBatch: Int = LIGHT_SECOND_BATCH,
        makeItem: (index: Int) -> Any,
    ): Long {
        // pre-size to avoid noise
        val retained = ArrayList<Any>(firstBatch + secondBatch)

        repeat(firstBatch) { retained.add(makeItem(it)) }
        val usedAfterFirst = usedHeapAfterGc()

        repeat(secondBatch) { retained.add(makeItem(it)) }
        val usedAfterSecond = usedHeapAfterGc()

        // avoid early collections
        check(retained.size == firstBatch + secondBatch)

        return (usedAfterSecond - usedAfterFirst) / secondBatch
    }

    private fun usedHeapAfterGc(): Long {
        forceGc()
        val runtime = Runtime.getRuntime()
        return runtime.totalMemory() - runtime.freeMemory()
    }

    /**
     * Best-effort GC
     */
    private fun forceGc() {
        repeat(GC_ATTEMPTS) {
            Runtime.getRuntime().gc()
            Thread.sleep(GC_SETTLE_MS)
            System.runFinalization()
        }
    }

    private fun report(label: String, bytesPerItem: Long) {
        Log.i(TAG, "$label retained $bytesPerItem bytes on the heap")
        InstrumentationRegistry.getInstrumentation().sendStatus(
            0,
            Bundle().apply { putLong("${label}_$RESULT_KEY", bytesPerItem) },
        )
    }

    companion object {
        private const val TAG = "HeapAllocationBenchmark"
        private const val RESULT_KEY = "bytes_retained"
        private const val KNOWN_SIZE = 500

        private const val LIGHT_FIRST_BATCH = 5_000
        private const val LIGHT_SECOND_BATCH = 15_000

        private const val HEAVY_FIRST_BATCH = 1_000
        private const val HEAVY_SECOND_BATCH = 4_000

        private const val COMPLEX_SPAN_ATTRIBUTES = 8
        private const val COMPLEX_SPAN_EVENTS = 4
        private const val EVENT_ATTRIBUTES = 3
        private const val EXCEPTION_ATTRIBUTES = 2
        private const val LINK_ATTRIBUTES = 2
        private const val COMPLEX_LOG_ATTRIBUTES = 8

        private const val GC_ATTEMPTS = 5
        private const val GC_SETTLE_MS = 50L
    }
}
