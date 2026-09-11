package io.embrace.android.embracesdk.benchmark

import io.embrace.android.embracesdk.internal.otel.spans.EmbraceSdkSpan
import io.embrace.android.embracesdk.internal.otel.spans.NoopEmbraceSdkSpan
import io.embrace.android.embracesdk.internal.otel.spans.SpanService
import io.embrace.android.embracesdk.internal.payload.AppFramework
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.EnvelopeMetadata
import io.embrace.android.embracesdk.internal.payload.EnvelopeResource
import io.embrace.android.embracesdk.internal.payload.SessionPartPayload
import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.android.embracesdk.internal.session.persistence.SessionPartDirectory

/**
 * One session's worth of telemetry, in the form each persistence layer needs to serialize it.
 *
 * [completedSpanCount] sets how much work the session did, which is the main thing that decides
 * how big it is once persisted. [attributesPerSpan] sets how much each of those spans carries.
 */
internal class SimpleSessionFixture(
    private val completedSpanCount: Int = COMPLETED_SPAN_COUNT,
    private val attributesPerSpan: Int = ATTRIBUTES_PER_SPAN,
) {
    val completedSpans: List<Span>
    val sessionSpan: Span
    val spanSnapshots: List<Span>

    val resource: EnvelopeResource = EnvelopeResource(
        appVersion = "3.14.2",
        appFramework = AppFramework.NATIVE,
        buildId = "5b3a1c9e-2f4d-4f7a-9c1b-8d6e0f2a7b41",
        appEcosystemId = "io.embrace.exampleapp",
        buildType = "release",
        buildFlavor = "production",
        environment = "prod",
        bundleVersion = "4821",
        sdkVersion = "8.0.0",
        sdkSimpleVersion = 800,
        deviceManufacturer = "Google",
        deviceModel = "Pixel 8a",
        deviceArchitecture = "arm64-v8a",
        jailbroken = false,
        diskTotalCapacity = 128_000_000_000L,
        osType = "linux",
        osName = "android",
        osVersion = "15",
        osCode = "35",
        screenResolution = "1080x2400",
        numCores = 8,
        deviceSocModel = "Tensor G3",
    )

    val metadata: EnvelopeMetadata = EnvelopeMetadata(
        userId = "8f14e45fceea167a5a36dedd4bea2543",
        email = "user@example.com",
        username = "example-user",
        personas = setOf("member", "beta"),
        timezoneDescription = "Europe/London",
        locale = "en_GB",
    )

    val directory: SessionPartDirectory = SessionPartDirectory(
        timestamp = 1_726_739_283_136L,
        uuid = "9d1e7c22-0f5b-4a63-8c2e-6b7d4f1a0e35",
        userSessionId = "4f3c9a7d0b8e5f2a4c116c9b1f2ec1d3",
        sessionPartId = "6c9b1f2ec1d34f3c9a7d0b8e5f2a4c11",
    )
    val envelopeVersion: String = "0.1.0"
    val envelopeType: String = "spans"

    val envelope: Envelope<SessionPartPayload>

    init {
        val harness = TelemetryDestinationHarness()
        val spanService = harness.createUncappedSpanService()

        repeat(completedSpanCount) { index ->
            spanService.newSpan("completed-work-$index").apply {
                start()
                populate(index)
                stop()
            }
        }

        val inFlight = (0 until IN_FLIGHT_SPAN_COUNT).map { index ->
            spanService.newSpan("in-flight-work-$index").apply {
                start()
                populate(index)
            }
        }
        spanSnapshots = inFlight.mapNotNull(EmbraceSdkSpan::snapshot)

        val sessionPartSpan = checkNotNull(harness.currentSessionPartSpan.current()) {
            "no session part span to end"
        }
        sessionPartSpan.retainDataAfterStop()

        val flushed = harness.currentSessionPartSpan.endSession(startNewSession = false)
        sessionSpan = checkNotNull(sessionPartSpan.snapshot()) { "session span produced no snapshot" }
        completedSpans = flushed.filterNot { it.spanId == sessionSpan.spanId }

        check(spanSnapshots.size == IN_FLIGHT_SPAN_COUNT) {
            "expected $IN_FLIGHT_SPAN_COUNT in-flight spans but snapshotted ${spanSnapshots.size}"
        }
        check(completedSpans.size == completedSpanCount) {
            "expected $completedSpanCount completed spans but got ${completedSpans.size}"
        }

        envelope = Envelope(
            resource = resource,
            metadata = metadata,
            version = envelopeVersion,
            type = envelopeType,
            data = SessionPartPayload(
                spans = completedSpans + sessionSpan,
                spanSnapshots = spanSnapshots,
            ),
        )
    }

    private fun SpanService.newSpan(name: String): EmbraceSdkSpan = createSpan(name = name).also {
        check(it !== NoopEmbraceSdkSpan) { "hit the per-session span cap" }
    }

    private fun EmbraceSdkSpan.populate(index: Int) {
        repeat(attributesPerSpan) { attribute ->
            addAttribute("work.attribute.$attribute", "work-attribute-value-$index-$attribute")
        }
        addEvent(
            name = "work-progressed-$index",
            timestampMs = null,
            attributes = mapOf("work.step" to index.toString()),
        )
    }

    private companion object {
        const val COMPLETED_SPAN_COUNT = 10
        const val IN_FLIGHT_SPAN_COUNT = 2
        const val ATTRIBUTES_PER_SPAN = 5
    }
}
